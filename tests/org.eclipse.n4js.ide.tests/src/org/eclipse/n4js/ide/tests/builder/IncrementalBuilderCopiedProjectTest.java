/**
 * Copyright (c) 2020 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.ide.tests.builder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.n4js.utils.io.FileCopier;
import org.eclipse.n4js.workspace.locations.FileURI;
import org.eclipse.xtext.xbase.lib.Pair;
import org.junit.Test;

/**
 */
public class IncrementalBuilderCopiedProjectTest extends AbstractIncrementalBuilderTest {

	/**
	 * This test creates a copy of a built project (including .n4js.projectstate files). After copying, both of the
	 * projects should compile like the original project compiled before.
	 *
	 * The contents of the test files (polyfills etc.) would create many warnings in case the .n4js.projectstate would
	 * reference absolute URIs.
	 */
	@Test
	public void testCreateDuplicateProject() throws IOException {
		testWorkspaceManager.createTestProjectOnDisk(Map.of(
				CFG_NODE_MODULES + "lib" + CFG_SRC + "LibModule.n4jsd", """
							@@Global
							@@ProvidedByRuntime

							@Polyfill export external public class Object extends Object {
							}
						""",
				"MyModule", """
							_globalThis.MigrationContext;
						""",
				CFG_DEPENDENCIES, """
							lib
						"""));

		startAndWaitForLspServer();
		assertProjectBuildOrder(
				"[test-project/node_modules/lib, test-project/node_modules/n4js-runtime, test-project]");
		assertIssues2(Pair.of("MyModule",
				List.of("(Error, [0:1 - 0:12], Couldn't resolve reference to IdentifiableElement '_globalThis'.)")));
		shutdownLspServer();

		File projectFolder = new File(getRoot(), DEFAULT_PROJECT_NAME);
		File projectFolder2 = new File(getRoot(), DEFAULT_PROJECT_NAME + "2");
		FileCopier.copy(projectFolder, projectFolder2);
		FileURI packagejson2 = new FileURI(new File(getRoot(), DEFAULT_PROJECT_NAME + "2/package.json"));
		changeFileOnDiskWithoutNotification(packagejson2, Pair.of(DEFAULT_PROJECT_NAME, DEFAULT_PROJECT_NAME + "2"));

		startAndWaitForLspServer();
		FileURI myModule1 = new FileURI(new File(getRoot(), DEFAULT_PROJECT_NAME + "/src/MyModule.n4js"));
		FileURI myModule2 = new FileURI(new File(getRoot(), DEFAULT_PROJECT_NAME + "2/src/MyModule.n4js"));
		assertProjectBuildOrder(
				"[test-project/node_modules/lib, test-project/node_modules/n4js-runtime, test-project, test-project2/node_modules/lib, test-project2/node_modules/n4js-runtime, test-project2]");
		assertIssues(Map.of(
				myModule1,
				List.of("(Error, [0:1 - 0:12], Couldn't resolve reference to IdentifiableElement '_globalThis'.)"),
				myModule2,
				List.of("(Error, [0:1 - 0:12], Couldn't resolve reference to IdentifiableElement '_globalThis'.)")));
	}

}
