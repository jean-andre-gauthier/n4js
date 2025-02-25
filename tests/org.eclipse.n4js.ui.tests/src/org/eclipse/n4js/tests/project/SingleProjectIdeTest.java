/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.tests.project;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.n4js.packagejson.PackageJsonUtils;
import org.eclipse.n4js.packagejson.projectDescription.SourceContainerType;
import org.eclipse.n4js.tests.utils.ConvertedIdeTest;
import org.eclipse.n4js.workspace.locations.FileURI;
import org.eclipse.xtext.xbase.lib.Pair;
import org.junit.Before;
import org.junit.Test;

/**
 */
// converted from SingleProjectPluginTest
public class SingleProjectIdeTest extends ConvertedIdeTest {

	private FileURI src;
	private FileURI src2; // this is not a source folder (defined in package.json), unless addSrc2ToSources is called
	private FileURI projectDescriptionFile;

	@Before
	public void setUp2() {
		testWorkspaceManager.createTestProjectOnDisk();
		startAndWaitForLspServer();
		assertNoIssues();
		projectDescriptionFile = toFileURI(getPackageJsonFile());
		src = toFileURI(getProjectRoot()).appendSegment("src");
		src2 = toFileURI(getProjectRoot()).appendSegment("src2");
		src2.toFile().mkdirs();
	}

	private void addSrc2ToSources() throws IOException {
		PackageJsonUtils.addSourceFoldersToPackageJsonFile(getPackageJsonFile().toPath(), SourceContainerType.SOURCE,
				"src2");
		joinServerRequests();
	}

	private void removeSrc2FromSource() throws IOException {
		PackageJsonUtils.removeSourceFoldersFromPackageJsonFile(getPackageJsonFile().toPath(), "src2");
		joinServerRequests();
	}

	private void addSrc3ToSources() throws IOException {
		PackageJsonUtils.addSourceFoldersToPackageJsonFile(getPackageJsonFile().toPath(), SourceContainerType.SOURCE,
				"src3");
		joinServerRequests();
	}

	private void addMainSrcToSources() throws IOException {
		PackageJsonUtils.addSourceFoldersToPackageJsonFile(getPackageJsonFile().toPath(), SourceContainerType.SOURCE,
				"main/src");
		joinServerRequests();
	}

	@Test
	public void testFileInSrcNoError() throws Exception {
		createFile("C", "class C {}");
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testBrokenFileInSrc2NoError() throws Exception {
		createFile(src2.appendSegment("C.n4js"), "class C extends Unknown {}");
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testBrokenFileInSrc2ShowsErrorAfterProjectDescriptionChange() throws Exception {
		createFile(src2.appendSegment("C.n4js"), "class C extends Unknown {}");
		joinServerRequests();
		assertNoIssues();
		addSrc2ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:16 - 0:23], Couldn't resolve reference to Type 'Unknown'.)")));
	}

	@Test
	public void testFileInSrcWithError() throws Exception {
		createFile("C", "class C extends Unknown {}");
		joinServerRequests();
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:16 - 0:23], Couldn't resolve reference to Type 'Unknown'.)")));
	}

	@Test
	public void testFileInSrcWithMissingDep() throws Exception {
		createFile("C", """
				import { D } from "D"
				class C extends D {}
				""");
		joinServerRequests();

		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:18 - 0:21], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)",
						"(Error, [1:16 - 1:17], Couldn't resolve reference to Type 'D'.)")));
		createFile("D", "export class D {}");
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testFileInSrcWithMissingDepInOtherFolder() throws Exception {
		createFile("C", """
				import { D } from "D"
				class C extends D {}
				""");
		joinServerRequests();

		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:18 - 0:21], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)",
						"(Error, [1:16 - 1:17], Couldn't resolve reference to Type 'D'.)")));
		createFile(src2.appendSegment("D.n4js"), "export class D {}");
		joinServerRequests();
		// Same as above, src2 folder is not set as source folder yet.
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:18 - 0:21], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)",
						"(Error, [1:16 - 1:17], Couldn't resolve reference to Type 'D'.)")));
		addSrc2ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertNoIssues();
	}

	@Test
	public void testDuplicateModuleInOtherFolder() throws Exception {
		createFile("C", "class C1 {}");
		createFile(src2.appendSegment("C.n4js"), "class C2 {}");
		joinServerRequests();
		assertNoIssues();
		addSrc2ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertEquals("unexpected number of issues in workspace", 2, getIssues().size());
		assertDuplicateModuleIssue(src.appendSegments("C.n4js"), DEFAULT_PROJECT_NAME, "src2/C.n4js");
		assertDuplicateModuleIssue(src2.appendSegments("C.n4js"), DEFAULT_PROJECT_NAME, "src/C.n4js");

		removeSrc2FromSource();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertNoIssues();
		// note: in Eclipse the issue in file src2/C.n4js remained after removing the source folder,
		// so this would also be acceptable (but the issue in src/C.n4js must be gone)
	}

	@Test
	public void testDuplicateN4JSDInOtherFolder() throws Exception {
		addSrc2ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		createFile("C", "class C {}");
		createFile(src2.appendSegment("C.n4jsd"), "export external public class C {}");
		joinServerRequests();
		assertEquals("unexpected number of issues in workspace", 2, getIssues().size());
		assertDuplicateModuleIssue(src.appendSegments("C.n4js"), DEFAULT_PROJECT_NAME, "src2/C.n4jsd");
		assertDuplicateModuleIssue(src2.appendSegments("C.n4jsd"), DEFAULT_PROJECT_NAME, "src/C.n4js");
	}

	@Test
	public void testJSIsNoDuplicate_01() throws Exception {
		addSrc2ToSources();
		createFile(src.appendSegment("C.js"), "var c = {}");
		createFile(src.appendSegment("C.n4jsd"), "export external public class C {}");
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testJSIsNoDuplicate_02() throws Exception {
		addSrc2ToSources();
		createFile(src.appendSegment("C.js"), "var c = {}");
		createFile(src2.appendSegment("C.n4jsd"), "export external public class C {}");
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testJSIsNoDuplicate_03() throws Exception {
		createFile(src.appendSegment("C.js"), "var c = {}");
		createFile(src.appendSegment("C.n4js"), "export public class C {}");
		joinServerRequests();
		assertDuplicateModuleIssue(src.appendSegments("C.n4js"), DEFAULT_PROJECT_NAME, "src/C.js");
	}

	@Test
	public void testJSIsNoDuplicate_04() throws Exception {
		createFile(src.appendSegment("C.js"), "var c = {}");
		createFile(src2.appendSegment("C.n4js"), "export public class C {}"); // src2 is not a source folders
		joinServerRequests();
		assertNoIssues();
	}

	@Test
	public void testTwoFilesSourceFolderRemovedFromProjectDescription() throws Exception {
		addSrc2ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		createFile("C", """
				import { D } from "D"
				class C extends D {}
				""");
		createFile(src2.appendSegment("D.n4js"), "export class D {}");
		joinServerRequests();
		assertNoIssues();

		removeSrc2FromSource();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:18 - 0:21], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)",
						"(Error, [1:16 - 1:17], Couldn't resolve reference to Type 'D'.)")));
	}

	@Test
	public void testTwoFilesSourceFolderRenamed() throws Exception {
		addSrc3ToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		createFile("C", """
				import { D } from "D"
				class C extends D {}
				""");
		createFile(src2.appendSegment("D.n4js"), "export class D {}");
		joinServerRequests();

		assertIssues2(
				Pair.of(DEFAULT_PROJECT_NAME + "/package.json", List.of(
						"(Warning, [12:16 - 12:22], Source container path src3 does not exist.)")),
				Pair.of("C", List.of(
						"(Error, [0:18 - 0:21], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)",
						"(Error, [1:16 - 1:17], Couldn't resolve reference to Type 'D'.)")));
		renameFile(src2, "src3");
		joinServerRequests();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertNoIssues();
	}

	@Test
	public void testTwoFilesMovedToDifferentSourceFolder() throws Exception {
		createFile("a/b/c/C", "export class C {}");
		createFile("a/b/c/D", "import * as C from 'a/b/c/C'");
		joinServerRequests();
		assertIssues2(
				Pair.of("a/b/c/D", List.of(
						"(Warning, [0:7 - 0:13], The import of * as C from a/b/c/C is unused.)")));

		FileURI mainSrc = src.getParent().appendSegments("main", "src");
		mainSrc.toFile().mkdirs();
		moveFile(src.appendSegment("a"), mainSrc.toFile());
		joinServerRequests();
		assertNoIssues();

		addMainSrcToSources();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertIssues2(
				Pair.of("D", List.of(
						"(Warning, [0:7 - 0:13], The import of * as C from a/b/c/C is unused.)")));

		renameFile(mainSrc.appendSegments("a", "b"), "d");
		joinServerRequests();
		assertIssues2(
				Pair.of("D", List.of(
						"(Error, [0:19 - 0:28], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)")));
	}

	@Test
	public void testTwoFilesMovedToDifferentSourceFolderAndPackage() throws Exception {
		createFile("a/b/c/C", "export class C {}");
		createFile("a/b/c/D", "import { C } from 'a/b/c/C'");
		joinServerRequests();
		assertIssues2(
				Pair.of("a/b/c/D", List.of(
						"(Warning, [0:9 - 0:10], The import of C is unused.)")));

		FileURI mainSrcX = src.getParent().appendSegments("main", "src", "x");
		mainSrcX.toFile().mkdirs();
		moveFile(src.appendSegment("a"), mainSrcX.toFile());
		joinServerRequests();
		assertNoIssues();

		addMainSrcToSources(); // note: using main/src as source folder, not main/src/x
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertIssues2(
				Pair.of("D", List.of(
						"(Error, [0:18 - 0:27], Cannot resolve plain module specifier (without project name as first segment): no matching module found.)")));
	}

	@Test
	public void testProjectDescriptionFileRemoved() throws Exception {
		createFile("C", "class C extends Unknown {}");
		joinServerRequests();
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:16 - 0:23], Couldn't resolve reference to Type 'Unknown'.)")));

		deleteFile(projectDescriptionFile);
		joinServerRequests();
		// TODO GH-2060 next line should not be necessary
		languageClient.clearIssues();
		cleanBuildAndWait();
		// file should have no errors because it is no longer validated
		assertNoIssues();
	}

	@Test
	public void testProjectDescriptionFileRecreated() throws Exception {
		String packageJsonContent = Files.readString(projectDescriptionFile.toPath());

		createFile("C", "class C extends Unknown {}");
		deleteFile(projectDescriptionFile);
		joinServerRequests();
		// TODO GH-2060 next line should not be necessary
		languageClient.clearIssues();
		cleanBuildAndWait();
		assertNoIssues();

		createFile(projectDescriptionFile, packageJsonContent);
		joinServerRequests();
		// TODO GH-2060 next line should not be necessary
		cleanBuildAndWait();
		assertIssues2(
				Pair.of("C", List.of(
						"(Error, [0:16 - 0:23], Couldn't resolve reference to Type 'Unknown'.)")));
	}
}
