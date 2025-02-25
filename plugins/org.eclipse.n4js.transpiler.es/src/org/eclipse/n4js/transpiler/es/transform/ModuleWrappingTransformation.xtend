/**
 * Copyright (c) 2019 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.transpiler.es.transform

import org.eclipse.n4js.N4JSGlobals
import org.eclipse.n4js.n4JS.ExportDeclaration
import org.eclipse.n4js.n4JS.ModifiableElement
import org.eclipse.n4js.n4JS.VariableBinding
import org.eclipse.n4js.n4JS.VariableDeclaration
import org.eclipse.n4js.n4JS.VariableStatement
import org.eclipse.n4js.transpiler.Transformation

import static org.eclipse.n4js.transpiler.TranspilerBuilderBlocks.*

/**
 * This transformation will prepare the output code for module loading. Since dropping support for commonjs and SystemJS
 * and instead using ECMAScript 2015 imports/exports in the output code, this transformation is no longer doing much.
 */
class ModuleWrappingTransformation extends Transformation {

	override assertPreConditions() {
		// true
	}

	override assertPostConditions() {
		// true
	}

	override analyze() {
		// nothing
	}

	override transform() {
		// strip modifiers off all exported elements
		// (e.g. remove "public" from something like "export public let a = 'hello';")
		collectNodes(state.im, ExportDeclaration, false).map[exportedElement].filter(ModifiableElement).forEach [
			it.declaredModifiers.clear
		];

		// the following is only required because earlier transformations are producing
		// invalid "export default var|let|const ..."
		// TODO instead of the next line, change the earlier transformations to not produce these invalid constructs
		collectNodes(state.im, ExportDeclaration, false).forEach[splitDefaultExportFromVarDecl];

		// add implicit import of "n4js-runtime"
		addEmptyImport(N4JSGlobals.N4JS_RUNTIME.rawName);
	}

	/**
	 * Turns
	 * <pre>
	 * export default var|let|const C = ...
	 * </pre>
	 * into
	 * <pre>
	 * var|let|const C = ...
	 * export default C;
	 * </pre>
	 */
	def private void splitDefaultExportFromVarDecl(ExportDeclaration exportDecl) {
		if (exportDecl.isDefaultExport) {
			val exportedElement = exportDecl.exportedElement;
			if (exportedElement instanceof VariableStatement) {
				if (!exportedElement.varDeclsOrBindings.filter(VariableBinding).isEmpty) {
					throw new UnsupportedOperationException("unsupported: default-exported variable binding");
				}
				if (exportedElement.varDeclsOrBindings.size > 1) {
					throw new UnsupportedOperationException(
						"unsupported: several default-exported variable declarations in a single export declaration");
				}
				val varDecl = exportedElement.varDeclsOrBindings.head as VariableDeclaration;
				val varDeclSTE = findSymbolTableEntryForElement(varDecl, true);
				insertBefore(exportDecl, exportedElement); // will remove exportedElement from exportDecl
				exportDecl.defaultExportedExpression = _IdentRef(varDeclSTE);
			}
		}
	}
}
