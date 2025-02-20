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
package org.eclipse.n4js.resource

import com.google.inject.Singleton
import java.math.BigDecimal
import org.eclipse.emf.ecore.EObject
import org.eclipse.n4js.n4JS.Script
import org.eclipse.n4js.scoping.builtin.BuiltInTypeScope
import org.eclipse.n4js.ts.typeRefs.BooleanLiteralTypeRef
import org.eclipse.n4js.ts.typeRefs.EnumLiteralTypeRef
import org.eclipse.n4js.ts.typeRefs.NumericLiteralTypeRef
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef
import org.eclipse.n4js.ts.typeRefs.StringLiteralTypeRef
import org.eclipse.n4js.validation.ASTStructureValidator

/**
 * This class performs some early pre-processing of the AST. This happens after {@link N4JSLinker lazy linking} and
 * before {@link ASTStructureValidator AST structure validation}.
 */
@Singleton
package final class N4JSPreProcessor {

	/**
	 * Performs an early processing of the AST, e.g. initialization of transient helper values.
	 * <b>This method assumes that it is allowed to change the AST!</b> Thus, it should be invoked from an "exec without
	 * cache clear" handler, see {@code OnChangeEvictingCache#execWithoutCacheClear(N4JSResource,IUnitOfWork)}.
	 */
	def public void process(Script script, N4JSResource resource) {
		val resourceSet = resource.resourceSet;
		if( resourceSet === null ) {
			// null-safe exit - required in smoke test where Resources detached from a ResourceSet are used.
			return;
		}
		val builtInTypes = BuiltInTypeScope.get( resourceSet );
		for (node : resource.script.eAllContents.toIterable) {
			processNode(node, resource, builtInTypes);
		}
	}

	def private dispatch void processNode(EObject astNode, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		// by default, do nothing
	}

	/**
	 * Support for array type syntax:
	 * <pre>
	 * let arr: string[];
	 * </pre>
	 * and arrrayN syntax:
	 * <pre>
	 * let tup: [string, int];
	 * </pre>
	 * Note that both {@code string[]} and {@code [string]} results in an {@code Array<string>}
	 */
	def private dispatch void processNode(ParameterizedTypeRef typeRef, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		if (typeRef.isArrayTypeExpression) {
			typeRef.declaredType = builtInTypes.arrayType;
		} else if (typeRef.isArrayNTypeExpression) {
			val n = typeRef.declaredTypeArgs.size;
			if (n < 2) {
				typeRef.declaredType = builtInTypes.arrayType;
			} else if (n <= BuiltInTypeScope.ITERABLE_N__MAX_LEN) {
				typeRef.declaredType = builtInTypes.getArrayNType(n);
			} else {
				// error (a validation will create an issue)
				// NOTE: it would be nice to create an InterableN with a union as last type argument
				// containing those element types that exceed the ITERABLE_N__MAX_LEN; however, this
				// would require AST rewriting, which isn't allowed.
				typeRef.declaredType = builtInTypes.getArrayNType(BuiltInTypeScope.ITERABLE_N__MAX_LEN);
			}
		}
	}

	def private dispatch void processNode(BooleanLiteralTypeRef typeRef, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		typeRef.value = typeRef.astValue == "true";
	}

	def private dispatch void processNode(NumericLiteralTypeRef typeRef, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		var valueRaw = typeRef.astValue as BigDecimal; // validity of this cast is enforced by the grammar
		if (valueRaw !== null) {
			valueRaw = valueRaw.stripTrailingZeros;
			if (typeRef.astNegated) {
				valueRaw = valueRaw.negate();
			}
			typeRef.value = valueRaw;
		} else {
			// syntax error
			typeRef.value = BigDecimal.ZERO;
		}
	}

	def private dispatch void processNode(StringLiteralTypeRef typeRef, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		typeRef.value = typeRef.astValue as String; // validity of this cast is enforced by the grammar
	}

	def private dispatch void processNode(EnumLiteralTypeRef typeRef, N4JSResource resource, BuiltInTypeScope builtInTypes) {
		// setting the value of an EnumLiteralTypeRef requires scoping and can therefore not be done here;
		// see N4JSScopeProvider#getScopeByShortcut() and TypeRefProcessor#processEnumLiteralTypeRefs()
	}
}
