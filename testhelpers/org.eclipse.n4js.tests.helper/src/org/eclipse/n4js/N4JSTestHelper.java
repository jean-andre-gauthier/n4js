/**
 * Copyright (c) 2017 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.n4js.n4JS.Script;
import org.eclipse.n4js.resource.N4JSResource;
import org.eclipse.n4js.utils.URIUtils;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.xbase.lib.Pair;
import org.junit.Assert;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Broad, top-level helper methods for testing N4JS. For more fine-grained helper functionality see classes such as
 * {@link N4JSParseHelper}, {@link N4JSValidationTestHelper}, etc.
 */
public class N4JSTestHelper {

	@Inject
	private N4JSParseHelper parseHelper;
	@Inject
	private N4JSValidationTestHelper validationTestHelper;
	@Inject
	private Provider<XtextResourceSet> resourceSetProvider;

	/**
	 * Parse & validate the given code, assert that there are no parser or validation errors, and return the root of the
	 * fully resolved (i.e. types builder, post-processing, etc.) AST.
	 */
	public Script parseAndValidateSuccessfully(CharSequence code) {
		try {
			final Script script = parseHelper.parseN4js(code);
			parseHelper.assertNoParseErrors(script);
			validationTestHelper.assertNoErrors(script); // this will trigger post-processing, validation, etc.
			return script;
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
			return null;
		}
	}

	/**
	 * Like {@link #parseAndValidateSuccessfully(CharSequence)}, but ignoring issues with the given issue codes.
	 */
	public Script parseAndValidateSuccessfullyIgnoring(CharSequence code, String... ignoredIssueCodes) {
		try {
			final Script script = parseHelper.parseN4js(code);
			parseHelper.assertNoParseErrors(script, new HashSet<>(Arrays.asList(ignoredIssueCodes)));
			validationTestHelper.assertNoErrorsExcept(script, ignoredIssueCodes);
			return script;
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
			return null;
		}
	}

	/**
	 * Parse & validate several files within the same resource set. The files may depend on one another (i.e. may import
	 * each other).
	 *
	 * @param fileNamesToCode
	 *            pair from file name (including file extension!) to file content (i.e. source code). File names may
	 *            include a path relative to a prject's source folder.
	 * @return the newly created resource set.
	 */
	public ResourceSet parseAndValidateSuccessfullyMany(Iterable<Pair<String, String>> fileNamesToCode) {
		XtextResourceSet rs = resourceSetProvider.get();

		// add all files to resource set and parse them
		for (Pair<String, String> pair : fileNamesToCode) {
			String fileName = pair.getKey();
			String code = pair.getValue();
			Script script = parseInFile(code, fileName, rs);
			parseHelper.assertNoParseErrors(script);
		}

		// validate all N4JS, N4JSD, etc. files (will trigger post-processing, etc.)
		for (Resource res : rs.getResources()) {
			if (res instanceof N4JSResource) {
				Script script = ((N4JSResource) res).getScript();
				validationTestHelper.assertNoErrors(script);
			}
		}

		return rs;
	}

	/**
	 * Create a new {@link Resource} in the given {@link ResourceSet} and parse the given source code.
	 */
	public Script parseInFile(CharSequence code, String fileName, ResourceSet resourceSetToUse) {
		return parseWithFileExtensionFromURI(code, URI.createURI(fileName), resourceSetToUse);
	}

	private Script parseWithFileExtensionFromURI(CharSequence text, URI uriToUse, ResourceSet resourceSetToUse) {
		final String fileExtensionToUse = URIUtils.fileExtension(uriToUse);
		if (fileExtensionToUse == null) {
			throw new IllegalArgumentException("given URI does not have a file extension");
		}
		final String oldExtension = parseHelper.fileExtension;
		try {
			parseHelper.fileExtension = fileExtensionToUse;
			return parseHelper.parse(text, uriToUse, resourceSetToUse);

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
			return null;
		} finally {
			parseHelper.fileExtension = oldExtension;
		}
	}
}
