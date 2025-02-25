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
package org.eclipse.n4js;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.n4js.naming.N4JSQualifiedNameConverter;
import org.eclipse.n4js.packagejson.projectDescription.ProjectType;
import org.eclipse.n4js.utils.UtilN4;
import org.eclipse.n4js.workspace.N4JSProjectConfigSnapshot;
import org.eclipse.n4js.workspace.utils.N4JSPackageName;
import org.eclipse.xtext.naming.QualifiedName;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

/**
 * Global hook for static information about the current setup. Contains file extensions, library names, and other
 * "useful" strings.
 */
public final class N4JSGlobals {

	/**
	 * The version of node.js that is officially supported by N4JS. This version is used for testing and if several node
	 * versions are found on the system (e.g. when nvm is used) this version is chosen.
	 * <p>
	 * This string
	 * <ul>
	 * <li>contains one or more version segments separated by ".",
	 * <li>contains only digits and '.' characters (in particular, it never starts with "v"),
	 * <li>never starts or ends with "." and never contains "..".
	 * </ul>
	 * If it contains fewer than three version segments, all numbers for the remaining segments are deemed compatible.
	 */
	public static final String NODE_VERSION = "20.3";

	/** URL of the public npm registry. */
	public static final String NPMJS_URL = "https://registry.npmjs.org/";
	/** URL of the local verdaccio instance. */
	public static final String VERDACCIO_URL = "http://localhost:4873/";
	/** Version of all test artifacts (i.e. n4js-libs, stdlib) in the local verdaccio instance. */
	public static final String VERDACCIO_TEST_VERSION = "0.0.1";

	/** Maximum value of type 'int' in N4JS. */
	public static final int INT32_MAX_VALUE = Integer.MAX_VALUE;
	/** Minimum value of type 'int' in N4JS. */
	public static final int INT32_MIN_VALUE = Integer.MIN_VALUE;
	/** Same as #{@link #INT32_MAX_VALUE}, but as a {@link BigDecimal}. */
	public static final BigDecimal INT32_MAX_VALUE_BD = new BigDecimal(INT32_MAX_VALUE);
	/** Same as #{@link #INT32_MIN_VALUE}, but as a {@link BigDecimal}. */
	public static final BigDecimal INT32_MIN_VALUE_BD = new BigDecimal(INT32_MIN_VALUE);

	/**
	 * The hashbang prefix is used to start a hashbang at the beginning of a module or script. E.g. #!/usr/bin/env node
	 */
	public static final String HASHBANG_PREFIX = "#!";

	/** Files extension of JS source files (<b>not</b> including the separator dot). */
	public static final String JS_FILE_EXTENSION = UtilN4.JS_FILE_EXTENSION;
	/** File extension of JS source files that contain ES6 modules (<b>not</b> including the separator dot). */
	public static final String MJS_FILE_EXTENSION = UtilN4.MJS_FILE_EXTENSION;
	/** File extension of JS source files that contain CommonJS modules (<b>not</b> including the separator dot). */
	public static final String CJS_FILE_EXTENSION = UtilN4.CJS_FILE_EXTENSION;
	/** Files extension of N4JS source files (<b>not</b> including the separator dot). */
	public static final String N4JS_FILE_EXTENSION = UtilN4.N4JS_FILE_EXTENSION;
	/** Files extension of JSX source files (<b>not</b> including the separator dot). */
	public static final String JSX_FILE_EXTENSION = UtilN4.JSX_FILE_EXTENSION;
	/** Files extension of N4JSX source files (<b>not</b> including the separator dot). */
	public static final String N4JSX_FILE_EXTENSION = UtilN4.N4JSX_FILE_EXTENSION;
	/** Files extension of N4JS definition files (<b>not</b> including the separator dot). */
	public static final String N4JSD_FILE_EXTENSION = UtilN4.N4JSD_FILE_EXTENSION;
	/** Files extension of TS files (<b>not</b> including the separator dot). */
	public static final String TS_FILE_EXTENSION = UtilN4.TS_FILE_EXTENSION;
	/** Files extension of TS definition files (<b>not</b> including the separator dot). */
	public static final String DTS_FILE_EXTENSION = UtilN4.DTS_FILE_EXTENSION;
	/** Files extension of XT source files (<b>not</b> including the separator dot). */
	public static final String XT_FILE_EXTENSION = UtilN4.XT_FILE_EXTENSION;

	/**
	 * Vendor ID
	 */
	public static final String VENDOR_ID = "org.eclipse.n4js";
	/**
	 * Mangelhaft
	 */
	public static final N4JSPackageName MANGELHAFT = new N4JSPackageName(VENDOR_ID + ".mangelhaft");
	/**
	 * Mangelhaft Assert
	 */
	public static final N4JSPackageName MANGELHAFT_ASSERT = new N4JSPackageName(MANGELHAFT + ".assert");

	/**
	 * Name of the npm package containing the mangelhaft command-line interface.
	 */
	public static final N4JSPackageName MANGELHAFT_CLI = new N4JSPackageName("n4js-mangelhaft-cli");

	/**
	 * All standard plain-JS file extensions.
	 * <p>
	 * Unmodifiable list containing {@link #JS_FILE_EXTENSION}, {@link #CJS_FILE_EXTENSION},
	 * {@link #MJS_FILE_EXTENSION}, and {@link #JSX_FILE_EXTENSION}.
	 */
	public static final Set<String> ALL_JS_FILE_EXTENSIONS = ImmutableSet.of(
			JS_FILE_EXTENSION,
			CJS_FILE_EXTENSION,
			MJS_FILE_EXTENSION,
			JSX_FILE_EXTENSION);

	/**
	 * All N4JS file extensions.
	 * <p>
	 * Unmodifiable list containing {@link #N4JS_FILE_EXTENSION}, {@link #N4JSD_FILE_EXTENSION},
	 * {@link #N4JSX_FILE_EXTENSION}.
	 */
	public static final Set<String> ALL_N4JS_FILE_EXTENSIONS = ImmutableSet.of(
			N4JS_FILE_EXTENSION,
			N4JSD_FILE_EXTENSION,
			N4JSX_FILE_EXTENSION);

	/**
	 * All file extensions the N4JS tooling is interested in, including but not limited to the N4JS file extensions.
	 * <p>
	 * Unmodifiable list containing {@link #N4JSD_FILE_EXTENSION},
	 * {@link #N4JS_FILE_EXTENSION},{@link #N4JSX_FILE_EXTENSION}, {@link #JS_FILE_EXTENSION},
	 * {@link #CJS_FILE_EXTENSION}, {@link #MJS_FILE_EXTENSION}, {@link #JSX_FILE_EXTENSION},
	 * {@link #DTS_FILE_EXTENSION}.
	 */
	public static final Set<String> ALL_N4_FILE_EXTENSIONS = ImmutableSet.<String> builder()
			.addAll(ALL_N4JS_FILE_EXTENSIONS)
			.addAll(ALL_JS_FILE_EXTENSIONS)
			.add(DTS_FILE_EXTENSION)
			.build();

	/**
	 * Name of the N4JS Git repository, i.e. "n4js". Same as {@link UtilN4#N4JS_GIT_REPOSITORY_NAME}.
	 */
	public static final String N4JS_GIT_REPOSITORY_NAME = UtilN4.N4JS_GIT_REPOSITORY_NAME;

	/**
	 * Name of the top-level folder in the N4JS Git repository containing the main N4JS plugins.
	 */
	public static final String PLUGINS_FOLDER_NAME = "plugins";

	/**
	 * Name of the top-level folder in the N4JS Git repository containing the N4JS runtime code, test frame work, and
	 * other code shipped with the IDE.
	 * <p>
	 * NOTE: the actual projects are not contained directly in this folder but in a sub folder, see
	 * {@link #N4JS_LIBS_SOURCES_PATH}.
	 */
	public static final String N4JS_LIBS_FOLDER_NAME = "n4js-libs";

	/**
	 * Path to the folder in the N4JS Git repository containing the source code of the "n4js-libs", relative to the
	 * repository's root folder.
	 */
	public static final Path N4JS_LIBS_SOURCES_PATH = Path.of(N4JS_LIBS_FOLDER_NAME, "packages");

	/**
	 * Name of the npm package containing the N4JS bootstrap and runtime code, i.e. the code defining internal low-level
	 * functions such as {@code $makeClass()} and containing core runtime code such as the implementation of N4Injector.
	 * <p>
	 * It is expected that this npm package lives in the N4JS Git repository at path {@value #N4JS_LIBS_SOURCES_PATH},
	 * cf. {@link #N4JS_LIBS_SOURCES_PATH}.
	 */
	public static final N4JSPackageName N4JS_RUNTIME = new N4JSPackageName("n4js-runtime");

	/**
	 * Runtime for ECMA 402.
	 */
	public static final N4JSPackageName N4JS_RUNTIME_ECMA402 = new N4JSPackageName("n4js-runtime-ecma402");

	/**
	 * Runtime for HTML5 DOM definitions, i.e. <code>window</code>, <code>document</code>, etc.
	 */
	public static final N4JSPackageName N4JS_RUNTIME_HTML5 = new N4JSPackageName("n4js-runtime-html5");

	/**
	 * The wrapper npm package for the N4JS command line interface.
	 */
	public static final N4JSPackageName N4JS_CLI = new N4JSPackageName("n4js-cli");

	/**
	 * Project types for which a dependency to the {@link #N4JS_RUNTIME n4js-runtime} is mandatory.
	 */
	public static final Set<ProjectType> PROJECT_TYPES_REQUIRING_N4JS_RUNTIME = ImmutableSet.of(
			ProjectType.LIBRARY,
			ProjectType.APPLICATION,
			ProjectType.TEST);

	/**
	 * Project types for which the generator is disabled.
	 */
	public static final Set<ProjectType> PROJECT_TYPES_WITHOUT_GENERATION = ImmutableSet.of(
			ProjectType.PLAINJS,
			ProjectType.DEFINITION,
			ProjectType.VALIDATION);

	/**
	 * Project types for which .d.ts generation will always be inactive, even if
	 *
	 * <pre>
	 * "generator": {
	 *     "d.ts": true
	 * }
	 * </pre>
	 *
	 * is given in the package.json file.
	 */
	public static final Set<ProjectType> PROJECT_TYPES_WITHOUT_DTS_GENERATION = ImmutableSet.of(
			ProjectType.PLAINJS,
			ProjectType.DEFINITION,
			ProjectType.VALIDATION,
			ProjectType.RUNTIME_ENVIRONMENT,
			ProjectType.RUNTIME_LIBRARY);

	/**
	 * The name of an npm command.
	 */
	public static final String NPM = "npm";
	/**
	 * Name of the npm scope of n4js definition projects.
	 */
	public static final String N4JSD_SCOPE = "@n4jsd";
	/**
	 * Name of the npm scope of TypeScript definition projects.
	 */
	public static final String TYPES_SCOPE = "@types";
	/**
	 * Name of the npm node_modules folder.
	 */
	public static final String NODE_MODULES = "node_modules";
	/**
	 * The name of NPM's package json file.
	 */
	public static final String PACKAGE_JSON = UtilN4.PACKAGE_JSON;

	/**
	 * Projects with a name ending in one of these suffixes are assumed to be API projects as defined by the API/Impl
	 * concept.
	 * <p>
	 * NOTE: normally API projects should be identified by a project type of {@link ProjectType#API API}. Use of these
	 * suffixes is only intended in temporary work-around implementations.
	 * <p>
	 * IMPORTANT: in addition to the direct references to this constant, another use of these suffixes is located in
	 * file {@code NodeTestRunner.n4js}.
	 */
	public static final String[] API_PROJECT_NAME_SUFFIXES = { ".api", "-api" };

	/**
	 * The name of the files storing each N4JS project's meta-information (serialized TModules, etc.).
	 */
	public static final String N4JS_PROJECT_STATE = ".n4js.projectstate";

	/**
	 * The name of the N4JS test catalog file.
	 */
	public static final String TEST_CATALOG = "test-catalog.json";

	/**
	 * The name of the tsconfig.json file.
	 */
	public static final String TS_CONFIG = "tsconfig.json";

	/**
	 * All project names of n4js libraries.
	 */
	public static final Set<N4JSPackageName> ALL_N4JS_LIBS = ImmutableSet.of(
			new N4JSPackageName("n4js-cli"),
			new N4JSPackageName("n4js-mangelhaft-cli"),
			N4JS_RUNTIME,
			N4JS_RUNTIME_ECMA402,
			new N4JSPackageName("n4js-runtime-es2015"),
			new N4JSPackageName("n4js-runtime-esnext"),
			N4JS_RUNTIME_HTML5,
			new N4JSPackageName("n4js-runtime-v8"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.assert"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.assert.test"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.reporter.console"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.reporter.xunit"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.reporter.sonar"),
			new N4JSPackageName("org.eclipse.n4js.mangelhaft.test"));

	/**
	 * The values of this map define TypeScript libraries to be included in the "lib" property of an auto-generated
	 * <code>tsconfig.json</code> file whenever the corresponding N4JS runtime library is declared as required runtime
	 * library in the containing project's <code>package.json</code> file.
	 */
	public static final ImmutableSetMultimap<N4JSPackageName, String> N4JS_DTS_LIB_CORRESPONDENCE = ImmutableSetMultimap
			.of(N4JS_RUNTIME_HTML5, "dom");

	/**
	 * String used to separate segments in the string representation of a {@link QualifiedName qualified name}.
	 *
	 * @see N4JSQualifiedNameConverter#DELIMITER
	 */
	public static final String QUALIFIED_NAME_DELIMITER = "/";

	/**
	 * Character used to separate the namespace name from the exported element's name when referring to an element via a
	 * namespace import. For example:
	 *
	 * <pre>
	 * import * as NS from "some/other/module"
	 *
	 * let c: NS.OtherClass;
	 * </pre>
	 */
	public static final char NAMESPACE_ACCESS_DELIMITER = '.';

	/**
	 * All HTML tags.
	 * <p>
	 * Source: <a href="http://www.w3schools.com/tags/">http://www.w3schools.com/tags/</a>.
	 */
	public static final Set<String> HTML_TAGS = new LinkedHashSet<>(Arrays.asList(
			"a", "abbr", "address", "area", "article", "aside", "audio",
			"b", "base", "bdi", "bdo", "blockquote", "body", "br", "button",
			"canvas", "caption", "cite", "code", "col", "colgroup",
			"datalist", "dd", "del", "details", "dfn", "dialog", "div", "dl", "dt",
			"em", "embed",
			"fieldset", "figcaption", "figure", "footer", "form",
			"h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hr", "html",
			"i", "iframe", "img", "input", "ins",
			"kbd", "keygen",
			"label", "legend", "li", "link",
			"main", "map", "mark", "menu", "menuitem", "meta", "meter",
			"nav", "noscript",
			"object", "ol", "optgroup", "option",
			"p", "param", "pre", "progress", "q", "rp", "rt", "ruby",
			"s", "samp", "script", "section", "select", "small", "source", "span",
			"strong", "style", "sub", "summary", "sup", "svg",
			"table", "tbody", "td", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track",
			"u", "ul", "use", "var", "video", "wbr"));

	/**
	 * All SVG tags.
	 * <p>
	 * Source: <a href=
	 * "https://developer.mozilla.org/en-US/docs/Web/SVG/Element">https://developer.mozilla.org/en-US/docs/Web/SVG/Element</a>.
	 */
	public static final Set<String> SVG_TAGS = new LinkedHashSet<>(Arrays.asList(
			"a", "altGlyph", "altGlyphDef", "altGlyphItem", "animate", "animateColor", "animateMotion",
			"animateTransform", "circle", "clipPath", "color-profile", "cursor", "defs", "desc", "discard", "ellipse",
			"feBlend", "feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting",
			"feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG",
			"feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology", "feOffset",
			"fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence", "filter", "font",
			"font-face", "font-face-format", "font-face-name", "font-face-src", "font-face-uri", "foreignObject", "g",
			"glyph", "glyphRef", "hatch", "hatchpath", "hkern", "image", "line", "linearGradient", "marker", "mask",
			"mesh", "meshgradient", "meshpatch", "meshrow", "metadata", "missing-glyph", "mpath", "path", "pattern",
			"polygon", "polyline", "radialGradient", "rect", "script", "set", "solidcolor", "stop", "style", "svg",
			"switch", "symbol", "text", "textPath", "title", "tref", "tspan", "unknown", "use", "view", "vkern"));

	/**
	 * The preamble added to all output files generated by the N4JS {@code EcmaScriptTranspiler} and .d.ts generator.
	 */
	public static final String OUTPUT_FILE_PREAMBLE = "// Generated by N4JS transpiler; for copyright see original N4JS source file.\n";

	/**
	 * File name of {@code n4js-runtime/n4jsglobals.d.ts}.
	 */
	public static final String N4JS_GLOBALS_DTS = "n4jsglobals.d.ts";

	/**
	 * Mandatory import for every generated d.ts file used by the d.ts generator.
	 */
	public static final String IMPORT_N4JSGLOBALS = "import 'n4js-runtime'";

	/**
	 * Standard maven target folder-name, the base of maven-compile results.
	 */
	public static final String TARGET = "target";

	/**
	 * Name of the N4JS headless builder JAR.
	 */
	public static final String N4JSC_JAR = "n4jsc.jar";

	/**
	 * For the npm packages in this set, ts-config build semantics will always be activated no matter whether the
	 * default rules (as implemented {@link N4JSProjectConfigSnapshot#hasTsConfigBuildSemantic() here}) apply.
	 *
	 * TODO IDE-3605 apply ts-config build semantics to more/all .packages with .d.ts files and remove this constant
	 */
	public static final Set<N4JSPackageName> NPM_PACKAGES_WITH_TS_CONFIG_BUILD_SEMANTICS = ImmutableSet.of(
			new N4JSPackageName("typescript"));

	private N4JSGlobals() {
		// private to prevent inheritance & instantiation.
	}

	/**
	 * Tells whether the given string ends with one of the {@link #ALL_N4JS_FILE_EXTENSIONS N4JS file extensions}.
	 */
	public static boolean endsWithN4JSFileExtension(String fileName) {
		if (fileName != null) {
			for (String ext : ALL_N4JS_FILE_EXTENSIONS) {
				if (fileName.endsWith("." + ext)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Tells whether the given node.js version string is compatible with {@link #NODE_VERSION}. */
	public static boolean isCompatibleNodeVersion(String nodeVersionStr) {
		String trimmedStr = nodeVersionStr.startsWith("v") ? nodeVersionStr.substring(1) : nodeVersionStr;
		return trimmedStr.equals(NODE_VERSION) || trimmedStr.startsWith(NODE_VERSION + ".");
	}
}
