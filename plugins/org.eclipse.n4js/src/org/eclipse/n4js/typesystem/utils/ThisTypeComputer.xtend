/**
 * Copyright (c) 2018 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.typesystem.utils

import com.google.inject.Inject
import org.eclipse.emf.ecore.EObject
import org.eclipse.n4js.n4JS.ArrowFunction
import org.eclipse.n4js.n4JS.N4ClassDeclaration
import org.eclipse.n4js.n4JS.N4ClassifierDefinition
import org.eclipse.n4js.n4JS.N4FieldDeclaration
import org.eclipse.n4js.n4JS.N4GetterDeclaration
import org.eclipse.n4js.n4JS.N4JSASTUtils
import org.eclipse.n4js.n4JS.N4MemberDeclaration
import org.eclipse.n4js.n4JS.N4MethodDeclaration
import org.eclipse.n4js.n4JS.N4SetterDeclaration
import org.eclipse.n4js.n4JS.ObjectLiteral
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef
import org.eclipse.n4js.ts.typeRefs.TypeRef
import org.eclipse.n4js.ts.typeRefs.TypeRefsFactory
import org.eclipse.n4js.ts.types.TClass
import org.eclipse.n4js.ts.types.TypingStrategy
import org.eclipse.n4js.types.utils.TypeUtils
import org.eclipse.n4js.typesystem.N4JSTypeSystem
import org.eclipse.n4js.validation.JavaScriptVariantHelper
import org.eclipse.xtext.EcoreUtil2

import static extension org.eclipse.n4js.types.utils.TypeUtils.*
import static extension org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.*

/**
 * @see ThisTypeComputer#getThisTypeAtLocation(RuleEnvironment,EObject)
 */
class ThisTypeComputer extends TypeSystemHelperStrategy {

	@Inject
	private N4JSTypeSystem ts;
	@Inject
	private JavaScriptVariantHelper jsVariantHelper;

	/**
	 * Computes the this type at the given location. Since ECMAScript does not support lexical this typing, this
	 * basically is a heuristic.
	 * <p>
	 * This code was moved here from judgment thisTypeRef in file 'n4js.xsemantics'.
	 */
	def TypeRef getThisTypeAtLocation(RuleEnvironment G, EObject location) {
		if (location instanceof ParameterizedTypeRef) {
			return TypeUtils.createBoundThisTypeRef(location);
		}

		val containingFunctionOrAccessor = N4JSASTUtils.getContainingFunctionOrAccessor(location);
		if (containingFunctionOrAccessor instanceof ArrowFunction) {
			return getThisTypeAtLocation(G, containingFunctionOrAccessor);
		}

		// searching for declaredTypeRefs introduced through @This
		val containingTFunctionOrAccessor = if (containingFunctionOrAccessor !== null) {
			containingFunctionOrAccessor.getDefinedFunctionOrAccessor()
		};
		val declaredThisTypeRef = TypeSystemHelper.getDeclaredThisType(containingTFunctionOrAccessor);
		if (declaredThisTypeRef !== null) {
			if (declaredThisTypeRef instanceof ParameterizedTypeRef) {
				return getThisTypeAtLocation(G, declaredThisTypeRef);
			}
			return declaredThisTypeRef;
		}

		val thisTarget = N4JSASTUtils.getProbableThisTarget(location);
		if (thisTarget instanceof ObjectLiteral) {
			// call rule, type may be created on the fly
			return ts.type(G, thisTarget);
		} else if (thisTarget instanceof N4ClassifierDefinition) {
			var thisTargetDefType = thisTarget.definedType;

			// In case of static polyfill (filler), replace defined type with filled type:
			if (thisTarget instanceof N4ClassDeclaration) {
				val clazz = thisTarget.definedTypeAsClass;
				if (clazz !== null && clazz.isStaticPolyfill()) {
					val actualClazz = clazz.superClassRef?.declaredType;
					if (actualClazz !== null) {
						thisTargetDefType = actualClazz;
					}
				}
			}

			if (thisTargetDefType !== null) {
				val containingFunction = N4JSASTUtils.getContainingFunction(location);
				if (containingFunction instanceof N4MethodDeclaration &&
						(containingFunction as N4MemberDeclaration).isStatic) {
					if (isInReturnDeclaration_Of_StaticMethod(location, containingFunction as N4MethodDeclaration)) {
						return getThisTypeAtLocation(G, thisTargetDefType.createTypeRef(TypingStrategy.DEFAULT, true));
					} else if (isInBody_Of_StaticMethod(location, containingFunction as N4MethodDeclaration)) {
						val ttRef = createTypeRefWithParamsAsArgs(thisTargetDefType);
						if (thisTargetDefType instanceof TClass) {
							val classTypeRef = ttRef//if (thisTargetDefType.final) ttRef else createWildcardExtends(ttRef);
							return TypeUtils.createTypeTypeRef(TypeUtils.createBoundThisTypeRef(classTypeRef), true);
						} else {
							return TypeUtils.createClassifierBoundThisTypeRef(TypeUtils.createTypeTypeRef(ttRef, false));
						}
					} else {
						return TypeUtils.createConstructorTypeRef(thisTargetDefType);
					}
				} else {
					val n4Field = EcoreUtil2.getContainerOfType(location, N4FieldDeclaration);
					if (n4Field !== null && n4Field.isStatic) {
						// this case has been disallowed as of GHOLD-263
						return TypeRefsFactory.eINSTANCE.createUnknownTypeRef();
						// old:
						// return TypeUtils.createConstructorTypeRef(thisTargetDEFTYPE);
					} else {
						val n4Getter = EcoreUtil2.getContainerOfType(location, N4GetterDeclaration);
						if (n4Getter !== null && n4Getter.isStatic) {
							return TypeUtils.createConstructorTypeRef(thisTargetDefType);
						} else {
							val n4Setter = EcoreUtil2.getContainerOfType(location, N4SetterDeclaration);
							if (n4Setter !== null && n4Setter.isStatic) {
								return TypeUtils.createConstructorTypeRef(thisTargetDefType);
							} else {
								return getThisTypeAtLocation(G, thisTargetDefType.createTypeRefWithParamsAsArgs);
							}
						}
					}
				}
			} else {
				return G.anyTypeRefDynamic;
			}
		} else {
			// if (unrestricted.isActive(location)) {
			if (jsVariantHelper.hasGlobalObject(location)) {
				return G.globalObjectTypeRef;
			}
			// note: it is possible to pass in a primitive as "thisArg"; therefore, 'this' in functions must
			// actually be inferred to 'any', by default, not to 'Object':
			return G.anyTypeRef;
		}
	}
}
