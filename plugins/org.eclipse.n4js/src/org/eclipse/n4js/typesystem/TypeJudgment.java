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
package org.eclipse.n4js.typesystem;

import static org.eclipse.n4js.ts.types.util.TypeExtensions.ref;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.GUARD_TYPE_CALL_EXPRESSION;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.GUARD_TYPE_PROPERTY_ACCESS_EXPRESSION;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.GUARD_VARIABLE_DECLARATION;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.anyTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.anyTypeRefDynamic;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.arrayType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.booleanType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.booleanTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.containsNumericOperand;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.functionTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.getDeclaredOrImplicitSuperType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.getPredefinedTypes;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.isAny;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.isNumeric;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.isNumericOperand;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.isSymbol;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.iterableTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.nullType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.nullTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.numberTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.objectType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.promiseType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.regexpTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.setThisBinding;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.stringType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.stringTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.undefinedType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.undefinedTypeRef;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.voidType;
import static org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.wrap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.n4js.AnnotationDefinition;
import org.eclipse.n4js.compileTime.CompileTimeValue;
import org.eclipse.n4js.flowgraphs.dataflow.guards.GuardAssertion;
import org.eclipse.n4js.flowgraphs.dataflow.guards.InstanceofGuard;
import org.eclipse.n4js.n4JS.AdditiveExpression;
import org.eclipse.n4js.n4JS.AdditiveOperator;
import org.eclipse.n4js.n4JS.Argument;
import org.eclipse.n4js.n4JS.ArrayElement;
import org.eclipse.n4js.n4JS.ArrayLiteral;
import org.eclipse.n4js.n4JS.ArrayPadding;
import org.eclipse.n4js.n4JS.AssignmentExpression;
import org.eclipse.n4js.n4JS.AwaitExpression;
import org.eclipse.n4js.n4JS.BinaryBitwiseExpression;
import org.eclipse.n4js.n4JS.BinaryLogicalExpression;
import org.eclipse.n4js.n4JS.BindingElement;
import org.eclipse.n4js.n4JS.BooleanLiteral;
import org.eclipse.n4js.n4JS.CastExpression;
import org.eclipse.n4js.n4JS.CatchVariable;
import org.eclipse.n4js.n4JS.CoalesceExpression;
import org.eclipse.n4js.n4JS.CommaExpression;
import org.eclipse.n4js.n4JS.ConditionalExpression;
import org.eclipse.n4js.n4JS.EqualityExpression;
import org.eclipse.n4js.n4JS.Expression;
import org.eclipse.n4js.n4JS.ForStatement;
import org.eclipse.n4js.n4JS.FormalParameter;
import org.eclipse.n4js.n4JS.FunctionExpression;
import org.eclipse.n4js.n4JS.GetterDeclaration;
import org.eclipse.n4js.n4JS.IdentifierRef;
import org.eclipse.n4js.n4JS.IndexedAccessExpression;
import org.eclipse.n4js.n4JS.JSXElement;
import org.eclipse.n4js.n4JS.JSXElementName;
import org.eclipse.n4js.n4JS.JSXFragment;
import org.eclipse.n4js.n4JS.MultiplicativeExpression;
import org.eclipse.n4js.n4JS.N4ClassDeclaration;
import org.eclipse.n4js.n4JS.N4ClassExpression;
import org.eclipse.n4js.n4JS.N4EnumLiteral;
import org.eclipse.n4js.n4JS.N4FieldDeclaration;
import org.eclipse.n4js.n4JS.N4JSASTUtils;
import org.eclipse.n4js.n4JS.N4JSPackage;
import org.eclipse.n4js.n4JS.N4MemberDeclaration;
import org.eclipse.n4js.n4JS.N4TypeVariable;
import org.eclipse.n4js.n4JS.NewExpression;
import org.eclipse.n4js.n4JS.NewTarget;
import org.eclipse.n4js.n4JS.NullLiteral;
import org.eclipse.n4js.n4JS.NumericLiteral;
import org.eclipse.n4js.n4JS.ObjectLiteral;
import org.eclipse.n4js.n4JS.ParameterizedCallExpression;
import org.eclipse.n4js.n4JS.ParameterizedPropertyAccessExpression;
import org.eclipse.n4js.n4JS.ParenExpression;
import org.eclipse.n4js.n4JS.PostfixExpression;
import org.eclipse.n4js.n4JS.PromisifyExpression;
import org.eclipse.n4js.n4JS.PropertyNameValuePair;
import org.eclipse.n4js.n4JS.PropertySpread;
import org.eclipse.n4js.n4JS.RegularExpressionLiteral;
import org.eclipse.n4js.n4JS.RelationalExpression;
import org.eclipse.n4js.n4JS.SetterDeclaration;
import org.eclipse.n4js.n4JS.ShiftExpression;
import org.eclipse.n4js.n4JS.StringLiteral;
import org.eclipse.n4js.n4JS.SuperLiteral;
import org.eclipse.n4js.n4JS.TaggedTemplateString;
import org.eclipse.n4js.n4JS.TemplateLiteral;
import org.eclipse.n4js.n4JS.TemplateSegment;
import org.eclipse.n4js.n4JS.ThisLiteral;
import org.eclipse.n4js.n4JS.TypeDefiningElement;
import org.eclipse.n4js.n4JS.UnaryExpression;
import org.eclipse.n4js.n4JS.UnaryOperator;
import org.eclipse.n4js.n4JS.VariableDeclaration;
import org.eclipse.n4js.n4JS.YieldExpression;
import org.eclipse.n4js.n4JS.util.N4JSSwitch;
import org.eclipse.n4js.postprocessing.ASTFlowInfo;
import org.eclipse.n4js.postprocessing.ASTMetaInfoUtils;
import org.eclipse.n4js.resource.N4JSResource;
import org.eclipse.n4js.scoping.builtin.BuiltInTypeScope;
import org.eclipse.n4js.scoping.members.MemberScopingHelper;
import org.eclipse.n4js.tooling.react.ReactHelper;
import org.eclipse.n4js.ts.typeRefs.BooleanLiteralTypeRef;
import org.eclipse.n4js.ts.typeRefs.BoundThisTypeRef;
import org.eclipse.n4js.ts.typeRefs.EnumLiteralTypeRef;
import org.eclipse.n4js.ts.typeRefs.FunctionTypeExprOrRef;
import org.eclipse.n4js.ts.typeRefs.FunctionTypeExpression;
import org.eclipse.n4js.ts.typeRefs.NumericLiteralTypeRef;
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef;
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRefStructural;
import org.eclipse.n4js.ts.typeRefs.StringLiteralTypeRef;
import org.eclipse.n4js.ts.typeRefs.ThisTypeRef;
import org.eclipse.n4js.ts.typeRefs.ThisTypeRefStructural;
import org.eclipse.n4js.ts.typeRefs.TypeArgument;
import org.eclipse.n4js.ts.typeRefs.TypeRef;
import org.eclipse.n4js.ts.typeRefs.TypeRefsFactory;
import org.eclipse.n4js.ts.typeRefs.TypeTypeRef;
import org.eclipse.n4js.ts.typeRefs.UnionTypeExpression;
import org.eclipse.n4js.ts.typeRefs.UnknownTypeRef;
import org.eclipse.n4js.ts.types.IdentifiableElement;
import org.eclipse.n4js.ts.types.ModuleNamespaceVirtualType;
import org.eclipse.n4js.ts.types.TClass;
import org.eclipse.n4js.ts.types.TClassifier;
import org.eclipse.n4js.ts.types.TDynamicElement;
import org.eclipse.n4js.ts.types.TEnum;
import org.eclipse.n4js.ts.types.TEnumLiteral;
import org.eclipse.n4js.ts.types.TFormalParameter;
import org.eclipse.n4js.ts.types.TFunction;
import org.eclipse.n4js.ts.types.TGetter;
import org.eclipse.n4js.ts.types.TMember;
import org.eclipse.n4js.ts.types.TMethod;
import org.eclipse.n4js.ts.types.TSetter;
import org.eclipse.n4js.ts.types.TStructuralType;
import org.eclipse.n4js.ts.types.TTypedElement;
import org.eclipse.n4js.ts.types.TypableElement;
import org.eclipse.n4js.ts.types.Type;
import org.eclipse.n4js.ts.types.TypeAlias;
import org.eclipse.n4js.ts.types.TypesPackage;
import org.eclipse.n4js.ts.types.TypingStrategy;
import org.eclipse.n4js.ts.types.util.TypesSwitch;
import org.eclipse.n4js.types.utils.TypeUtils;
import org.eclipse.n4js.typesystem.utils.RuleEnvironment;
import org.eclipse.n4js.typesystem.utils.TypeSystemHelper.Callable;
import org.eclipse.n4js.typesystem.utils.TypeSystemHelper.Newable;
import org.eclipse.n4js.utils.DestructureHelper;
import org.eclipse.n4js.utils.N4JSLanguageUtils;
import org.eclipse.n4js.utils.N4JSLanguageUtils.EnumKind;
import org.eclipse.n4js.utils.PromisifyHelper;
import org.eclipse.n4js.validation.JavaScriptVariantHelper;
import org.eclipse.n4js.xtext.scoping.IEObjectDescriptionWithError;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.xbase.lib.Pair;

import com.google.inject.Inject;

/* package */ class TypeJudgment extends AbstractJudgment {

	@Inject
	private PromisifyHelper promisifyHelper;
	@Inject
	private MemberScopingHelper memberScopingHelper;
	@Inject
	private IQualifiedNameConverter qualifiedNameConverter;
	@Inject
	private DestructureHelper destructureHelper;
	@Inject
	private JavaScriptVariantHelper javaScriptVariantHelper;
	@Inject
	private ReactHelper reactHelper;

	/**
	 * See {@link N4JSTypeSystem#type(RuleEnvironment, TypableElement)} and
	 * {@link N4JSTypeSystem#use_type_judgment_from_PostProcessors(RuleEnvironment, TypableElement)}.
	 */
	public TypeRef apply(RuleEnvironment G, TypableElement element) {
		final TypeRef result = doApply(G, element);
		if (result == null) {
			final String stringRep = element != null ? element.eClass().getName() : "<null>";
			throw new IllegalStateException("null return value in type judgment for element: " + stringRep);
		}
		return result;
	}

	private TypeRef doApply(RuleEnvironment G, TypableElement element) {
		if (element == null) {
			return unknown();
		}
		final EPackage elementPkg = element.eClass().getEPackage();
		if (elementPkg == TypesPackage.eINSTANCE) {
			return new TypeJudgmentSwitchForTypes(G).doSwitch(element);
		} else if (elementPkg == N4JSPackage.eINSTANCE) {
			return new TypeJudgmentSwitchForASTNodes(G).doSwitch(element);
		} else {
			throw new IllegalStateException("element belongs to unsupported EPackage: " + elementPkg.getName());
		}
	}

	private final class TypeJudgmentSwitchForTypes extends TypesSwitch<TypeRef> {

		private final RuleEnvironment G;

		private TypeJudgmentSwitchForTypes(RuleEnvironment G) {
			this.G = G;
		}

		@Override
		public TypeRef defaultCase(EObject object) {
			throw new UnsupportedOperationException(
					this.getClass().getSimpleName() + " missing case-method for " + object.eClass().getName());
		}

		/*
		 * We already have a type -> thus we simply wrap the existing type in a TypeRef. This rule is a bit odd: the
		 * client already has a Type in hand and invokes the type judgment to obtain its type, which seems superfluous.
		 * However, supporting this simplifies some client code, because the client need not check for this special case
		 * and call TypeUtils#createTypeRef() directly.
		 */
		@Override
		public TypeRef caseType(Type type) {
			TypeRef result = TypeUtils.wrapTypeInTypeRef(type);
			result = declMergingHelper.handleDeclarationMergingDuringTypeInference(G, result);
			return result;
		}

		/**
		 * This override is required only to solve the ambiguity due to {@link TypeAlias} being a subtype of both
		 * {@link Type} and {@link TTypedElement}. We choose to treat it as a {@code Type}.
		 */
		@Override
		public TypeRef caseTypeAlias(TypeAlias typeAlias) {
			return this.caseType(typeAlias);
		}

		@Override
		public TypeRef caseTEnumLiteral(TEnumLiteral enumLiteral) {
			EnumLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createEnumLiteralTypeRef();
			result.setValue(enumLiteral);
			return result;
		}

		/** Covers cases TField, TFormalParameter, TVariable. */
		@Override
		public TypeRef caseTTypedElement(TTypedElement typedElement) {
			final TypeRef typeRef = typedElement.getTypeRef();
			return typeRef != null ? typeRef : anyTypeRef(G);
		}

		@Override
		public TypeRef caseTGetter(TGetter tGetter) {
			final TypeRef declTypeRef = tGetter.getTypeRef();
			return declTypeRef != null ? declTypeRef : anyTypeRef(G);
		}

		@Override
		public TypeRef caseTSetter(TSetter tSetter) {
			final TypeRef declTypeRef = tSetter.getTypeRef();
			return declTypeRef != null ? declTypeRef : anyTypeRef(G);
		}

		@Override
		public TypeRef caseModuleNamespaceVirtualType(ModuleNamespaceVirtualType mnsvt) {
			return TypeUtils.createTypeRef(mnsvt);
		}

		@Override
		public TypeRef caseTDynamicElement(TDynamicElement elem) {
			return anyTypeRefDynamic(G);
		}
	}

	private final class TypeJudgmentSwitchForASTNodes extends N4JSSwitch<TypeRef> {

		private final RuleEnvironment G;

		private TypeJudgmentSwitchForASTNodes(RuleEnvironment G) {
			this.G = G;
		}

		@Override
		public TypeRef defaultCase(EObject object) {
			throw new UnsupportedOperationException(
					this.getClass().getSimpleName() + " missing case-method for " + object.eClass().getName());
		}

		// ----------------------------------------------------------------------
		// AST nodes: type defining elements
		// (esp. declarations except VariableDeclaration)
		// ----------------------------------------------------------------------

		@Override
		public TypeRef caseTypeDefiningElement(TypeDefiningElement elem) {
			Type defType = elem.getDefinedType();
			TypeRef result = TypeUtils.wrapTypeInTypeRef(defType);
			result = declMergingHelper.handleDeclarationMergingDuringTypeInference(G, result);
			return result != null ? result : unknown();
		}

		@Override
		public TypeRef caseObjectLiteral(ObjectLiteral ol) {
			final ParameterizedTypeRefStructural ptr = TypeRefsFactory.eINSTANCE.createParameterizedTypeRefStructural();
			ptr.setDeclaredType(objectType(G));
			ptr.setStructuralType((TStructuralType) ol.getDefinedType());
			ptr.setDefinedTypingStrategy(TypingStrategy.STRUCTURAL);
			return ptr;
		}

		// ----------------------------------------------------------------------
		// AST nodes: declarations
		// ----------------------------------------------------------------------

		@Override
		public TypeRef casePropertyNameValuePair(PropertyNameValuePair property) {
			// note: keep this rule aligned with rules caseN4FieldDeclaration and caseVariableDeclaration
			final TypeRef T;
			if (property.getDeclaredTypeRef() != null) {
				T = property.getDeclaredTypeRef();
			} else if (property.getExpression() != null) {
				final TypeRef E = ts.type(G, property.getExpression());
				T = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G, E,
						!N4JSASTUtils.isImmutable(property));
			} else {
				T = anyTypeRef(G);
			}
			return T;
		}

		@Override
		public TypeRef caseN4FieldDeclaration(N4FieldDeclaration fieldDecl) {
			// note: keep this rule aligned with rules casePropertyNameValuePair and caseVariableDeclaration
			final TypeRef T;
			if (fieldDecl.getDeclaredTypeRef() != null) {
				T = fieldDecl.getDeclaredTypeRef();
			} else if (fieldDecl.getExpression() != null) {
				final TypeRef E = ts.type(G, fieldDecl.getExpression());
				T = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G, E,
						!N4JSASTUtils.isImmutable(fieldDecl));
			} else {
				T = anyTypeRef(G);
			}
			return T;
		}

		@Override
		public TypeRef caseVariableDeclaration(VariableDeclaration vdecl) {
			// note: keep this rule aligned with rules caseN4FieldDeclaration and casePropertyNameValuePair
			final TypeRef T;
			if (vdecl.getDeclaredTypeRef() != null) {
				T = vdecl.getDeclaredTypeRef();
			} else if (vdecl.eContainer() instanceof BindingElement) {
				// guard against infinite recursion, e.g. for vars like this:
				// var [a,b] = b; / var {a,b} = b;
				final Pair<String, Expression> guardKey = Pair.of(GUARD_VARIABLE_DECLARATION, vdecl.getExpression());
				if (G.get(guardKey) == null) {
					final RuleEnvironment G2 = wrap(G);
					G2.put(guardKey, Boolean.TRUE);
					// compute the value type at this location in the destructuring pattern
					final TypeRef raw = destructureHelper.getTypeOfVariableDeclarationInDestructuringPattern(G2, vdecl);
					T = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G, raw,
							!N4JSASTUtils.isImmutable(vdecl));
				} else {
					T = anyTypeRef(G);
				}
			} else if (vdecl.eContainer() instanceof ForStatement && ((ForStatement) vdecl.eContainer()).isForOf()) {
				final ForStatement forOfStmnt = (ForStatement) vdecl.eContainer();
				// we have a situation like this: for [await](var x of myList) { ... }
				// --> infer type of 'x' to the first type argument of the type of 'myList'
				final Pair<String, EObject> guardKey = Pair.of(GUARD_VARIABLE_DECLARATION, vdecl.eContainer());
				if (G.get(guardKey) == null) {
					final RuleEnvironment G2 = wrap(G);
					G2.put(guardKey, Boolean.TRUE);
					final TypeRef ofPartTypeRef = ts.type(G2, forOfStmnt.getExpression());
					final TypeArgument elemType = tsh.extractIterableElementType(G2, ofPartTypeRef,
							forOfStmnt.isAwait());
					if (elemType != null) {
						T = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G2, elemType,
								!N4JSASTUtils.isImmutable(vdecl));
					} else {
						T = unknown();
					}
				} else {
					T = anyTypeRef(G);
				}
			} else if (vdecl.eContainer() instanceof ForStatement && ((ForStatement) vdecl.eContainer()).isForIn()) {
				// we have a situation like this: for(var x in obj) { ... }
				// --> infer type of 'x' to string
				T = stringTypeRef(G);
			} else if (vdecl.getExpression() != null) {
				// guard against infinite recursion e.g. for vars like this:
				// var x = x || {};
				final Pair<String, Expression> guardKey = Pair.of(GUARD_VARIABLE_DECLARATION, vdecl.getExpression());
				if (G.get(guardKey) == null) {
					final RuleEnvironment G2 = wrap(G);
					G2.put(guardKey, Boolean.TRUE);
					// compute the expression type
					TypeRef E = ts.type(G2, vdecl.getExpression());
					if (E instanceof BoundThisTypeRef
							|| (E instanceof TypeTypeRef
									&& ((TypeTypeRef) E).getTypeArg() instanceof BoundThisTypeRef)) {
						// N.T.D. on purpose:
						// in case of ThisTypeRefs, there should be no existential type-refs (so there is no use in
						// getting rid of it :-/)
						// as part of IDE-785 leave the BoundThisTypeRef in place for variables w/o defined type.
					} else if (E instanceof UnknownTypeRef) {
						// NOTE: in case of variables (as opposed to fields, properties) we use UnknownTypeRef as the
						// inferred type and do *not* convert it to 'any', as #sanitizeTypeOfVariableFieldProperty()
						// in the else-block would do.
					} else {
						E = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G2, E,
								!N4JSASTUtils.isImmutable(vdecl));
					}
					if (E.getDeclaredType() == undefinedType(G)
							|| E.getDeclaredType() == nullType(G)
							|| E.getDeclaredType() == voidType(G)) {
						T = anyTypeRef(G);
					} else {
						T = E;
					}
				} else {
					T = anyTypeRef(G);
				}
			} else {
				T = anyTypeRef(G);
			}

			if (javaScriptVariantHelper.enforceDynamicTypes(vdecl)) {
				return typeSystemHelper.makeDynamic(T);
			}
			return T;
		}

		@Override
		public TypeRef caseGetterDeclaration(GetterDeclaration getter) {
			final TypeRef declTypeRef = getter.getDeclaredTypeRef();
			if (declTypeRef != null) {
				return declTypeRef;
			} else {
				final TGetter defGetter = getter.getDefinedGetter();
				final TypeRef defDeclTypeRef = defGetter != null ? defGetter.getTypeRef() : null;
				if (defDeclTypeRef != null) {
					return defDeclTypeRef;
				} else {
					return anyTypeRef(G);
				}
			}
		}

		@Override
		public TypeRef caseSetterDeclaration(SetterDeclaration setter) {
			final TypeRef declTypeRef = setter.getDeclaredTypeRef();
			if (declTypeRef != null) {
				return declTypeRef;
			} else {
				final TSetter defSetter = setter.getDefinedSetter();
				final TypeRef defDeclTypeRef = defSetter != null ? defSetter.getTypeRef() : null;
				if (defDeclTypeRef != null) {
					return defDeclTypeRef;
				} else {
					return anyTypeRef(G);
				}
			}
		}

		@Override
		public TypeRef caseFormalParameter(FormalParameter fpar) {
			final TypeRef fparTypeRefInAST = fpar.getDeclaredTypeRefInAST();
			final TypeRef fparTypeRef = fpar.getDeclaredTypeRef();
			final TypeRef T;
			if (fparTypeRef != null) {
				// check for valid cases of this in type of a fpar

				// case 1: structural this type in constructor
				// (in next line: no need to assert that we are in a constructor; we have a validation for that)
				final boolean case1 = fparTypeRef instanceof ThisTypeRefStructural;

				// case 2: 'this' nested within a function type expression on an fpar
				boolean case2 = fparTypeRef instanceof FunctionTypeExpression
						&& hasFormalParameterWithThisType((FunctionTypeExpression) fparTypeRef);

				if (case1 || case2) {
					T = typeSystemHelper.bindAndSubstituteThisTypeRef(G, fparTypeRefInAST, fparTypeRef);
				} else {
					// note: it's a bit cleaner to return the type from the TModule, if one was already determined
					final TFormalParameter definedElem = fpar.getDefinedVariable();
					final TypeRef definedElemTypeRef = definedElem != null ? definedElem.getTypeRef() : null;
					if (definedElemTypeRef != null) {
						T = definedElemTypeRef;
					} else {
						T = fparTypeRef;
					}
				}
			} else if (fpar.isHasInitializerAssignment()) {
				final Expression initExpr = fpar.getInitializer();
				if (initExpr != null) {
					final TypeRef E = ts.type(G, initExpr);
					T = typeSystemHelper.sanitizeTypeOfVariableFieldPropertyParameter(G, E, true);
				} else {
					T = anyTypeRef(G);
				}
			} else {
				if (javaScriptVariantHelper.enforceDynamicTypes(fpar)) {// e.g. plain ECMAScript
					T = anyTypeRefDynamic(G);
				} else { // e.g., N4JS
					// T = env(G, fpar, TypeRef)
					// or
					T = anyTypeRef(G);
				}
			}
			return TypeUtils.wrapIfVariadic(getPredefinedTypes(G).builtInTypeScope, T, fpar);
		}

		// ----------------------------------------------------------------------
		// AST nodes: literals
		// ----------------------------------------------------------------------

		@Override
		public TypeRef caseNullLiteral(NullLiteral l) {
			return nullTypeRef(G);
		}

		@Override
		public TypeRef caseBooleanLiteral(BooleanLiteral l) {
			BooleanLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createBooleanLiteralTypeRef();
			result.setValue(l.isTrue());
			return result;
		}

		@Override
		public TypeRef caseNumericLiteral(NumericLiteral l) {
			NumericLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createNumericLiteralTypeRef();
			result.setValue(l.getValue());
			return result;
		}

		@Override
		public TypeRef caseStringLiteral(StringLiteral l) {
			StringLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createStringLiteralTypeRef();
			result.setValue(l.getValue());
			return result;
		}

		@Override
		public TypeRef caseTaggedTemplateString(TaggedTemplateString taggedTemplate) {
			final TypeRef tagTypeRef = ts.type(G, taggedTemplate.getTarget());
			if (tagTypeRef instanceof FunctionTypeExprOrRef) {
				return ((FunctionTypeExprOrRef) tagTypeRef).getReturnTypeRef();
			}
			return unknown();
		}

		@Override
		public TypeRef caseTemplateLiteral(TemplateLiteral l) {
			List<Expression> segments = l.getSegments();
			if (segments.size() == 1) {
				Expression segment = segments.get(0);
				if (segment instanceof TemplateSegment) {
					StringLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createStringLiteralTypeRef();
					result.setValue(((TemplateSegment) segment).getValue());
					return result;
				}
			}
			return stringTypeRef(G);
		}

		@Override
		public TypeRef caseTemplateSegment(TemplateSegment l) {
			return stringTypeRef(G);
		}

		@Override
		public TypeRef caseRegularExpressionLiteral(RegularExpressionLiteral l) {
			return regexpTypeRef(G);
		}

		@Override
		public TypeRef caseArrayLiteral(ArrayLiteral l) {
			throw new IllegalStateException(
					"rule caseArrayLiteral() should never be invoked (PolyComputer is responsible for typing ArrayLiterals)");
		}

		@Override
		public TypeRef caseArrayPadding(ArrayPadding l) {
			throw new IllegalStateException(
					"rule caseArrayPadding() should never be invoked (PolyComputer is responsible for typing ArrayLiterals and their children)");
		}

		@Override
		public TypeRef caseArrayElement(ArrayElement e) {
			throw new IllegalStateException(
					"rule caseArrayElement() should never be invoked (PolyComputer is responsible for typing ArrayLiterals and their children)");
		}

		// ----------------------------------------------------------------------
		// AST nodes: expressions
		// ----------------------------------------------------------------------

		@Override
		public TypeRef caseIdentifierRef(IdentifierRef idref) {
			TypeRef T = ts.type(G, idref.getId());

			final ASTFlowInfo flowInfo = ((N4JSResource) idref.eResource()).getASTMetaInfoCache().getFlowInfo();
			final Collection<InstanceofGuard> definitiveGuards = flowInfo.instanceofGuardAnalyser
					.getDefinitiveGuards(idref);

			if (!definitiveGuards.isEmpty()) {
				final Collection<TypeRef> allTypes = new ArrayList<>();
				final Collection<TypeRef> excludedTypes = new ArrayList<>();
				final Collection<TypeRef> remainingTypes = new ArrayList<>();
				if (T != null) {
					allTypes.add(T);
				}
				for (InstanceofGuard ioGuard : definitiveGuards) {
					if (ioGuard.symbolCFE instanceof IdentifierRef) {
						final IdentifiableElement guardedElement = ((IdentifierRef) ioGuard.symbolCFE).getId();
						if (guardedElement != null && idref.getId() == guardedElement) {
							List<TypeRef> disjTypes = new ArrayList<>();
							for (Expression typeIdentifier : ioGuard.typeIdentifiers) {
								TypeRef instanceofType = ts.type(G, typeIdentifier);

								if (instanceofType instanceof TypeTypeRef) {
									final TypeTypeRef ttRef = (TypeTypeRef) instanceofType;
									final TypeArgument typeArg = ttRef.getTypeArg();
									if (typeArg instanceof TypeRef) {
										instanceofType = (TypeRef) typeArg;
									}
								} else {
									TMethod constructSig = tsh.getConstructSignature(G, instanceofType);
									TypeRef returnTypeRef = constructSig != null ? constructSig.getReturnTypeRef()
											: null;
									if (returnTypeRef != null) {
										instanceofType = returnTypeRef;
									}
								}

								TypeUtils.sanitizeRawTypeRef(instanceofType);
								disjTypes.add(instanceofType);
							}

							TypeRef type = disjTypes.size() == 1 ? disjTypes.get(0)
									: TypeUtils.createNonSimplifiedUnionType(disjTypes);

							if (ioGuard.asserts == GuardAssertion.AlwaysHolds) {
								allTypes.add(type);
							} else if (ioGuard.asserts == GuardAssertion.NeverHolds) {
								excludedTypes.add(type);
							}
						}
					}
				}

				if (excludedTypes.isEmpty()) {
					remainingTypes.addAll(allTypes);
				} else {
					for (TypeRef origType : allTypes) {
						TypeRef remainingType = null;
						if (origType instanceof UnionTypeExpression) {
							final UnionTypeExpression union = (UnionTypeExpression) origType;
							final Collection<TypeRef> newUnionTypes = new LinkedList<>();
							for (TypeRef unionType : union.getTypeRefs()) {
								boolean removeType = false;
								for (TypeRef neverHoldingType : excludedTypes) {
									if (ts.subtypeSucceeded(G, unionType, neverHoldingType)) {
										removeType = true;
										break;
									}
								}
								if (!removeType) {
									newUnionTypes.add(unionType);
								}
							}
							if (newUnionTypes.size() < union.getTypeRefs().size()) {
								remainingType = tsh.createUnionType(G,
										newUnionTypes.toArray(new TypeRef[newUnionTypes.size()]));
							}
						} else {

							boolean removeType = false;
							for (TypeRef neverHoldingType : excludedTypes) {
								if (ts.subtypeSucceeded(G, origType, neverHoldingType)) {
									removeType = true;
									break;
								}
							}
							if (!removeType) {
								remainingType = origType;
							}
						}

						if (remainingType != null) {
							remainingTypes.add(remainingType);
						}
					}
				}

				if (!remainingTypes.isEmpty()) {
					T = tsh.createIntersectionType(G, remainingTypes.toArray(new TypeRef[remainingTypes.size()]));
				}
			}

			if (T != null && T.isUnknown()) {
				if (idref.eContainer() instanceof JSXElementName) {
					// special case for <div> elements
					T = stringTypeRef(G);
				}
			}

			if (!N4JSASTUtils.isWriteAccess(idref)) {
				T = ts.substTypeVariablesWithFullCapture(G, T);
			} else {
				// in case of write access: we must not capture wildcards!
				T = ts.substTypeVariables(G, T);
			}

			return T;
		}

		@Override
		public TypeRef caseN4EnumLiteral(N4EnumLiteral enumLiteral) {
			TEnumLiteral definedLiteral = enumLiteral.getDefinedLiteral();
			if (definedLiteral == null) {
				return unknown();
			}
			EnumLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createEnumLiteralTypeRef();
			result.setValue(definedLiteral);
			return result;
		}

		@Override
		public TypeRef caseThisLiteral(ThisLiteral t) {
			TypeRef rawT = typeSystemHelper.getThisTypeAtLocation(G, t);
			return rawT != null ? TypeUtils.enforceNominalTyping(rawT) : unknown();
		}

		@Override
		public TypeRef caseSuperLiteral(SuperLiteral superLiteral) {
			final N4MemberDeclaration containingMemberDecl = EcoreUtil2.getContainerOfType(
					superLiteral.eContainer(), N4MemberDeclaration.class);

			if (containingMemberDecl == null) {
				// super at wrong location, will be checked in expression validator
				return unknown();
			}

			final EObject container = containingMemberDecl.eContainer();
			if (!(container instanceof N4ClassDeclaration)) {
				return unknown();
			}
			final TClass containingClass = ((N4ClassDeclaration) container).getDefinedTypeAsClass();
			if (containingClass == null) {
				return unknown();
			}
			final TClassifier superClassifier = getDeclaredOrImplicitSuperType(G, containingClass);

			TClassifier effectiveSuperClassifier = superClassifier;
			if (containingClass.isStaticPolyfill()) { // IDE-1735: static-polyfills replacing original constructor
				if (superClassifier instanceof TClass) {
					effectiveSuperClassifier = getDeclaredOrImplicitSuperType(G, (TClass) superClassifier);
				}
			}

			// Note: to avoid nasty special cases in rules expectedTypeOfArgumentInCallExpression,
			// expectedTypeOfArgumentInNewExpression, typePropertyAccessExpression, typeIndexedAccessExpression, etc. we
			// make the type of keyword super depend on where/how it is used.
			if (superLiteral.eContainer() instanceof ParameterizedPropertyAccessExpression
					|| superLiteral.eContainer() instanceof IndexedAccessExpression) {
				// case 1: super member access, i.e. super.foo() OR super['foo']
				final TypeRef T;
				if (effectiveSuperClassifier != null) {
					if (containingMemberDecl.isStatic()) {
						T = TypeUtils.createConstructorTypeRef(effectiveSuperClassifier);
					} else {
						T = TypeUtils.createTypeRef(effectiveSuperClassifier);
					}
				} else {
					T = null;
				}
				return T != null ? TypeUtils.enforceNominalTyping(T) : unknown();
			} else if (superLiteral.eContainer() instanceof ParameterizedCallExpression) {
				// case 2: super call, i.e. super()
				if (containingMemberDecl.isConstructor()) {
					// super() is used in a constructor
					final TMethod ctor = containerTypesHelper.fromContext(superLiteral.eResource())
							.findConstructor(effectiveSuperClassifier);
					return ctor != null ? TypeUtils.createTypeRef(ctor)
							: unknown();
				} else {
					// super() used in a normal method, getter or setter (not in a constructor)
					// --> this is an error case, but error will be produced by validation rule
					// --> make sure no error is produced in the type system to avoid duplicate errors
					return unknown();
				}
			} else if (superLiteral.eContainer() instanceof NewExpression) {
				// case 3: super with new keyword, i.e. new super OR new super(<args>)

				// not yet supported
				return unknown();
			} else {
				// super at wrong location, will be checked in Expression Validator
				return unknown();
			}
		}

		@Override
		public TypeRef caseParenExpression(ParenExpression e) {
			return ts.type(G, e.getExpression());
		}

		@Override
		public TypeRef caseYieldExpression(YieldExpression y) {
			TypeRef t;
			if (y.isMany()) {
				final Expression yieldValue = y.getExpression();
				TypeRef yieldValueTypeRef = ts.type(G, yieldValue);
				final BuiltInTypeScope scope = getPredefinedTypes(G).builtInTypeScope;
				if (TypeUtils.isGeneratorOrAsyncGenerator(yieldValueTypeRef, scope)) {
					t = typeSystemHelper.getGeneratorTReturn(G, yieldValueTypeRef);
				} else {
					final ParameterizedTypeRef itTypeRef = iterableTypeRef(G, TypeUtils.createWildcard());
					final boolean isIterable = ts.subtype(G, yieldValueTypeRef, itTypeRef).isSuccess();
					if (isIterable) {
						// In case a custom iterable is given, it might return an
						// entry like {done=true, value=...} where value can have
						// any type and would be returned. Hence, the return type
						// is 'any' here.
						t = scope.getAnyTypeRef();
					} else {
						t = anyTypeRef(G);
					}
				}
			} else {
				final TypeRef actualGenTypeRef = typeSystemHelper.getActualGeneratorReturnType(G, y);
				if (actualGenTypeRef != null) {
					t = typeSystemHelper.getGeneratorTNext(G, actualGenTypeRef);
				} else {
					t = anyTypeRef(G);
				}
			}

			return t != null ? t : anyTypeRef(G);
		}

		@Override
		public TypeRef caseAwaitExpression(AwaitExpression e) {
			final TypeRef exprType = ts.type(G, e.getExpression());
			final TypeRef T;
			if (exprType.getDeclaredType() == promiseType(G)) {
				// standard case: use await on a promise
				// --> result will be upper bound of first type argument
				T = ts.upperBound(G, exprType.getDeclaredTypeArgs().get(0));
			} else if (promisifyHelper.isPromisifiableExpression(e.getExpression())) {
				// "auto-promisify" case (i.e. an "await <expr>" that is a short-syntax for "await @Promisify <expr>")
				final TypeRef promisifiedReturnTypeRef = promisifyHelper
						.extractPromisifiedReturnType(e.getExpression());
				if (promisifiedReturnTypeRef.getDeclaredType() == promiseType(G)) {
					// --> result will be upper bound of first type argument
					T = ts.upperBound(G, promisifiedReturnTypeRef.getDeclaredTypeArgs().get(0));
				} else {
					T = promisifiedReturnTypeRef;
				}
			} else {
				// "pass through" case
				T = exprType;
			}
			return T;
		}

		@Override
		public TypeRef casePromisifyExpression(PromisifyExpression e) {
			return promisifyHelper.extractPromisifiedReturnType(e.getExpression());
		}

		@Override
		public TypeRef caseIndexedAccessExpression(IndexedAccessExpression expr) {
			if (expr.getTarget() == null || expr.getIndex() == null) {
				// broken AST
				return unknown();
			} else if (expr.getTarget() instanceof SuperLiteral) {
				// index access on super keyword is disallowed
				return unknown();
			}
			// standard case:

			TypeRef targetTypeRef = ts.type(G, expr.getTarget());
			targetTypeRef = ts.upperBoundWithReopenAndResolveBoth(G, targetTypeRef);

			TypeRef indexTypeRef = ts.type(G, expr.getIndex());

			final Type targetDeclType = targetTypeRef.getDeclaredType();
			final boolean targetIsLiteralOfStringBasedEnum = targetDeclType instanceof TEnum
					&& N4JSLanguageUtils.getEnumKind((TEnum) targetDeclType) == EnumKind.StringBased;
			final boolean indexIsNumeric = ts.subtype(G, indexTypeRef, numberTypeRef(G)).isSuccess();
			final CompileTimeValue indexValue = ASTMetaInfoUtils.getCompileTimeValue(expr.getIndex());
			final String memberName = N4JSLanguageUtils.derivePropertyNameFromCompileTimeValue(indexValue);

			final TypeRef T;
			if (indexIsNumeric && (targetTypeRef.isArrayLike() || targetIsLiteralOfStringBasedEnum)) {
				if (targetDeclType.isGeneric() && targetTypeRef.getTypeArgsWithDefaults().isEmpty()) {
					// later: evaluate name if possible, we may even want to return smth like intersect(allProperties)
					T = anyTypeRef(G);
				} else {
					final RuleEnvironment G2 = wrap(G);
					typeSystemHelper.addSubstitutions(G2, targetTypeRef);
					setThisBinding(G2, targetTypeRef);
					final TypeRef elementTypeRef = targetIsLiteralOfStringBasedEnum
							? stringType(G).getElementType()
							: targetDeclType.getElementType();
					T = ts.substTypeVariables(G2, elementTypeRef);
				}
			} else if (memberName != null) {
				// indexing via constant computed-name, sub-cases: static or instance member, for the latter
				// nominally-typed or structurally-typed receiver
				final boolean staticAccess = (targetTypeRef instanceof TypeTypeRef);
				final boolean structFieldInitMode = targetTypeRef
						.getTypingStrategy() == TypingStrategy.STRUCTURAL_FIELD_INITIALIZER;
				final boolean checkVisibility = false; // access modifiers checked in validation
				final IScope scope = memberScopingHelper.createMemberScope(targetTypeRef, expr, checkVisibility,
						staticAccess, structFieldInitMode);
				final IEObjectDescription memberDesc = !memberName.isEmpty()
						? scope.getSingleElement(qualifiedNameConverter.toQualifiedName(memberName))
						: null;
				final EObject member = memberDesc != null
						&& !IEObjectDescriptionWithError.isErrorDescription(memberDesc)
								? memberDesc.getEObjectOrProxy()
								: null;

				if (member instanceof TMember && !member.eIsProxy()) {
					TypeRef memberTypeRef = ts.type(G, (TMember) member);
					final RuleEnvironment G2 = wrap(G);
					typeSystemHelper.addSubstitutions(G2, targetTypeRef);
					setThisBinding(G2, targetTypeRef);
					T = ts.substTypeVariables(G2, memberTypeRef);
				} else if (targetTypeRef.isDynamic()) {
					T = anyTypeRefDynamic(G);
				} else if (N4JSLanguageUtils.hasIndexSignature(targetTypeRef)) {
					T = anyTypeRefDynamic(G);
				} else if (targetDeclType == objectType(G)) {
					T = anyTypeRefDynamic(G);
				} else {
					T = unknown();
				}
			} else if (targetTypeRef.isDynamic()) {
				T = anyTypeRefDynamic(G);
			} else if (N4JSLanguageUtils.hasIndexSignature(targetTypeRef)) {
				T = anyTypeRefDynamic(G);
			} else {
				T = anyTypeRef(G);
				// later: evaluate name if possible, we may even want to return smth like intersect(allProperties)
			}
			return T;
		}

		@Override
		public TypeRef caseParameterizedPropertyAccessExpression(ParameterizedPropertyAccessExpression expr) {
			// avoid loops in type inference
			final Pair<String, Expression> guardKey = Pair.of(GUARD_TYPE_PROPERTY_ACCESS_EXPRESSION, expr);
			final Object guardValue = G.get(guardKey);
			if (guardValue instanceof TypeRef) {
				return (TypeRef) guardValue;
			}

			// record that we are inferring the type of expr
			final RuleEnvironment G2 = wrap(G);
			G2.put(guardKey, anyTypeRef(G2));

			final TypeRef receiverTypeRef = ts.type(G2, expr.getTarget());

			typeSystemHelper.addSubstitutions(G2, receiverTypeRef);
			setThisBinding(G2, receiverTypeRef);

			// add parameterization stemming from super types, e.g., "class C extends G<string>",
			// in case of super or this literals
			// TODO can/should this be moved to one of the #addSubstitutions() methods?
			if (!(receiverTypeRef instanceof UnknownTypeRef)
					&& (expr.getTarget() instanceof SuperLiteral || expr.getTarget() instanceof ThisLiteral)) {
				// we may be in Object Literals etc., as we also handle this, so check for failures:
				final N4ClassDeclaration containingClassDecl = EcoreUtil2.getContainerOfType(expr,
						N4ClassDeclaration.class);
				Type containingClass = containingClassDecl != null ? containingClassDecl.getDefinedType() : null;
				if (containingClass instanceof TClass) {
					// In case of static polyfill (filler), replace defined type with filled type:
					if (containingClass.isStaticPolyfill()) {
						final TypeRef superClassRef = ((TClass) containingClass).getSuperClassRef();
						if (superClassRef != null) {
							containingClass = superClassRef.getDeclaredType();
						} else {
							containingClass = null;
						}
					}
					if (containingClass instanceof TClass) {
						final TypeRef superClassRef = ((TClass) containingClass).getSuperClassRef();
						if (superClassRef != null) {
							typeSystemHelper.addSubstitutions(G2, superClassRef);
						}
					}
				}
			}

			final TypeRef receiverTypeRefUB = ts.upperBoundWithReopen(G2, receiverTypeRef);
			final IdentifiableElement prop = expr.getProperty();
			final TypeRef propTypeRef;
			if (prop instanceof TMethod && ((TMethod) prop).isConstructor()) {
				// accessing the built-in constructor property ...
				final TypeArgument ctorTypeArg;
				if (receiverTypeRefUB instanceof TypeTypeRef) {
					// case "C.constructor"
					ctorTypeArg = functionTypeRef(G);
				} else {
					// cases such as "c.constructor" or "this.constructor"
					final Type declType = receiverTypeRefUB.getDeclaredType();
					final boolean finalCtorSig = declType instanceof TClassifier
							&& N4JSLanguageUtils.hasCovariantConstructor((TClassifier) declType, declMergingHelper);
					if (finalCtorSig) {
						ctorTypeArg = ref(declType);
					} else if (declType != null) {
						ctorTypeArg = TypeUtils.createWildcardExtends(ref(declType));
					} else {
						ctorTypeArg = null; // will boil down to UnknownTypeRef (see below)
					}
				}
				propTypeRef = ctorTypeArg != null
						? TypeUtils.createTypeTypeRef(ctorTypeArg, true)
						: unknown();
			} else if (receiverTypeRefUB.isDynamic() && prop != null && prop.eIsProxy()) {
				// access to an unknown property of a dynamic type
				propTypeRef = anyTypeRefDynamic(G2);
			} else {
				propTypeRef = ts.type(wrap(G2), prop);
				if (expr.isParameterized() || !propTypeRef.getTypeArgsWithDefaults().isEmpty()) {
					typeSystemHelper.addSubstitutions(G2, expr);
				}
			}

			TypeRef T = propTypeRef;

			// Because 'propTypeRef' was taken from the TMember in the TModule and therefore does not have any context
			// information, we just "lost" all substitution / capturing that might have been performed on
			// 'receiverTypeRef' by other #case...() methods. Therefore, we have to perform substitution / capturing in
			// much the same was as in method #caseIdentifierRef():
			if (!N4JSASTUtils.isWriteAccess(expr)) {
				T = ts.substTypeVariablesWithFullCapture(G2, T);
			} else {
				// in case of write access: we must not capture contained wildcards!
				T = ts.substTypeVariablesWithPartialCapture(G2, T);
			}

			if (expr.getTarget() instanceof SuperLiteral && T instanceof FunctionTypeExprOrRef) {
				// super.foo(): this; cf. GHOLD-95
				final FunctionTypeExprOrRef F = (FunctionTypeExprOrRef) T;
				if (F.getReturnTypeRef() instanceof BoundThisTypeRef) {
					final TypeRef rawT = typeSystemHelper.getThisTypeAtLocation(G2, expr);
					final TypeRef thisTypeRef = TypeUtils.enforceNominalTyping(rawT);
					if (F instanceof FunctionTypeExpression && F.eContainer() == null) {
						// avoid creation of new instance
						final FunctionTypeExpression FTE = (FunctionTypeExpression) F;
						FTE.setReturnTypeRef(TypeUtils.copyIfContained(thisTypeRef));
					} else {
						T = TypeUtils.createFunctionTypeExpression(null, F.getTypeVars(), F.getFpars(), thisTypeRef);
					}
				}
			}

			return T;
		}

		@Override
		public TypeRef caseParameterizedCallExpression(ParameterizedCallExpression expr) {
			final TypeRef targetTypeRef = ts.type(G, expr.getTarget());
			final Callable callable = tsh.getCallableTypeRef(G, targetTypeRef);
			if (callable != null && callable.getSignatureTypeRef().isPresent()) {
				final FunctionTypeExprOrRef F = callable.getSignatureTypeRef().get();
				final TFunction tFunction = F.getFunctionType();

				TypeRef T;
				final Pair<String, Expression> guardKey = Pair.of(GUARD_TYPE_CALL_EXPRESSION, expr);
				final Object guardValue = G.get(guardKey);
				if (guardValue instanceof TypeRef) {
					T = ts.substTypeVariables(G, (TypeRef) guardValue);
					// TODO redesign GUARDs: instead of returning a preliminary (i.e. incorrect!) result, an error value
					// should be returned! But what error value to use?
					// fail (-> does not work, because next 'or' block will be executed then!)
					// T = TypesFactory.eINSTANCE.createUnknownTypeRef
					// T = null
				} else {
					// record that we are inferring the type of expr
					final RuleEnvironment G2 = wrap(G);
					G2.put(guardKey, F.getReturnTypeRef());
					typeSystemHelper.addSubstitutions(G2, expr, F, false);

					// get the return type of F
					if (expr.eContainer() instanceof AwaitExpression
							&& expr.eContainmentFeature() == N4JSPackage.eINSTANCE.getAwaitExpression_Expression()
							&& tFunction != null
							&& AnnotationDefinition.PROMISIFIABLE.hasAnnotation(tFunction)) {
						// special case: automatic @Promisify
						// given a @Promisifiable function foo(), the following two should be equivalent:
						// await foo() <=> await @Promisify foo()
						T = promisifyHelper.extractPromisifiedReturnType(expr);
					} else {
						// standard case:
						final TypeRef rtr = F.getReturnTypeRef();
						T = rtr != null ? rtr : anyTypeRef(G);
					}

					T = ts.substTypeVariables(G2, T);
					if (T == null) {
						return unknown();
					}

					if (T instanceof BoundThisTypeRef
							&& !(expr.getReceiver() instanceof ThisLiteral
									|| expr.getReceiver() instanceof SuperLiteral)) {
						// we've got something like "this[C]" as return type of a call expression of the form "new
						// C().method()" but *not* "this.method()" or "super.method()"
						// -> detach 'this'-type from its 'this'-context to make sure it won't be confused with the
						// current 'this'-context or other this contexts, e.g.
						// @formatter:off
						//     class C {
						//         method(): this {
						//             return new C().method(); // must lead to an error!
						//         }
						//     }
						// @formatter:on
						T = ts.upperBoundWithReopen(G2, T); // taking upper bound turns this[C] into C
					}
				}
				return T;
			} else if (callable != null) {
				// targetTypeRef is callable, but no signature information available
				return anyTypeRef(G, callable.isDynamic());
			} else if (targetTypeRef.isDynamic()) {
				return anyTypeRefDynamic(G);
			} else {
				return unknown();
			}
		}

		@Override
		public TypeRef caseArgument(Argument arg) {
			return ts.type(G, arg.getExpression());
		}

		@Override
		public TypeRef caseNewExpression(NewExpression e) {
			Newable newable = tsh.getNewableTypeRef(G, e, false);
			if (newable != null) {
				TypeRef instanceTypeRef = newable.getInstanceTypeRef();
				if (instanceTypeRef != null) {
					return instanceTypeRef;
				}
			}
			return unknown();
		}

		@Override
		public TypeRef caseNewTarget(NewTarget nt) {
			return unknown();
		}

		@Override
		public TypeRef casePostfixExpression(PostfixExpression object) {
			return numberTypeRef(G);
		}

		@Override
		public TypeRef caseUnaryExpression(UnaryExpression e) {
			switch (e.getOp()) {
			case DELETE:
				return booleanTypeRef(G);
			case VOID:
				return undefinedTypeRef(G);
			case TYPEOF:
				return stringTypeRef(G);
			case NOT:
				return booleanTypeRef(G);
			case POS:
			case NEG:
				TypeRef exprTypeRef = ts.type(G, e.getExpression());
				if (exprTypeRef instanceof NumericLiteralTypeRef) {
					BigDecimal value = ((NumericLiteralTypeRef) exprTypeRef).getValue();
					if (value != null) {
						if (e.getOp() == UnaryOperator.NEG) {
							NumericLiteralTypeRef result = TypeRefsFactory.eINSTANCE.createNumericLiteralTypeRef();
							result.setValue(value.negate());
							return result;
						} else {
							return exprTypeRef;
						}
					}
				}
				return numberTypeRef(G);
			default: // INC, DEC, INV
				return numberTypeRef(G);
			}
		}

		@Override
		public TypeRef caseMultiplicativeExpression(MultiplicativeExpression object) {
			return numberTypeRef(G);
		}

		@Override
		public TypeRef caseAdditiveExpression(AdditiveExpression expr) {
			if (expr.getOp() == AdditiveOperator.ADD) {
				final TypeRef l = ts.type(G, expr.getLhs());
				final TypeRef r = ts.type(G, expr.getRhs());

				final boolean lunknown = l instanceof UnknownTypeRef;
				final boolean runknown = r instanceof UnknownTypeRef;

				if (lunknown && runknown) {
					return typeSystemHelper.createUnionType(G, numberTypeRef(G), stringTypeRef(G));
				} else {
					final boolean lnum = isNumericOperand(G, l);
					final boolean rnum = isNumericOperand(G, r);

					if (lnum && rnum) {
						return numberTypeRef(G);
					} else {
						if ((lunknown || runknown) && (lnum || rnum)) {
							// one is numeric, the other unknown -->
							return typeSystemHelper.createUnionType(G, numberTypeRef(G), stringTypeRef(G));
						} else {
							final boolean lMayNum = lnum || containsNumericOperand(G, l) || isAny(G, l)
									|| isSymbol(G, l);
							final boolean rMayNum = rnum || containsNumericOperand(G, r) || isAny(G, r)
									|| isSymbol(G, r);

							if (lMayNum && rMayNum) {
								return typeSystemHelper.createUnionType(G, numberTypeRef(G), stringTypeRef(G));
							} else {
								return stringTypeRef(G);
							}
						}
					}
				}
			} else {
				return numberTypeRef(G);
			}
		}

		@Override
		public TypeRef caseShiftExpression(ShiftExpression e) {
			return numberTypeRef(G);
		}

		@Override
		public TypeRef caseRelationalExpression(RelationalExpression e) {
			return booleanTypeRef(G);
		}

		@Override
		public TypeRef caseEqualityExpression(EqualityExpression e) {
			return booleanTypeRef(G);
		}

		@Override
		public TypeRef caseBinaryBitwiseExpression(BinaryBitwiseExpression e) {
			return numberTypeRef(G);
		}

		@Override
		public TypeRef caseBinaryLogicalExpression(BinaryLogicalExpression expr) {
			return simplifyUnionWithEmptyAnyArray(expr.getLhs(), expr.getRhs());
		}

		@Override
		public TypeRef caseConditionalExpression(ConditionalExpression expr) {
			return simplifyUnionWithEmptyAnyArray(expr.getTrueExpression(), expr.getFalseExpression());
		}

		@Override
		public TypeRef caseCoalesceExpression(CoalesceExpression expr) {
			return simplifyUnionWithEmptyAnyArray(expr.getExpression(), expr.getDefaultExpression());
		}

		private TypeRef simplifyUnionWithEmptyAnyArray(Expression lhs, Expression rhs) {
			final boolean lhsIsEmptyArrayLiteral = lhs instanceof ArrayLiteral
					&& ((ArrayLiteral) lhs).getElements().isEmpty();
			final boolean rhsIsEmptyArrayLiteral = rhs instanceof ArrayLiteral
					&& ((ArrayLiteral) rhs).getElements().isEmpty();

			final TypeRef L = ts.type(G, lhs);
			final TypeRef R = ts.type(G, rhs);

			if (lhsIsEmptyArrayLiteral && R.getDeclaredType() == arrayType(G)) {
				// case: [] || someArray
				return R;
			} else if (rhsIsEmptyArrayLiteral && L.getDeclaredType() == arrayType(G)) {
				// case: someArray || []
				return L;
			}
			return typeSystemHelper.createUnionType(G, L, R);
		}

		@Override
		public TypeRef caseAssignmentExpression(AssignmentExpression expr) {
			switch (expr.getOp()) {
			case ASSIGN:
				return ts.type(G, expr.getRhs());
			case ADD_ASSIGN:
				// see typeAdditiveExpression
				final TypeRef l = ts.type(G, expr.getLhs());
				final TypeRef r = ts.type(G, expr.getRhs());
				final Type lDeclType = l.getDeclaredType();
				final Type rDeclType = r.getDeclaredType();
				final boolean lnum = lDeclType == booleanType(G) || isNumeric(G, lDeclType);
				final boolean rnum = rDeclType == booleanType(G) || isNumeric(G, rDeclType);
				final boolean undef = lDeclType == undefinedType(G) || lDeclType == nullType(G)
						|| rDeclType == undefinedType(G) || rDeclType == nullType(G);
				if (!(lnum && rnum)
						&& !(undef && (lnum || rnum))) {
					return stringTypeRef(G);
				}
				//$FALL-THROUGH$
			default:
				// MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN: 6.1.13. Multiplicative Expression
				// SUB_ASSIGN, ADD_ASSIGN (of numbers/boolean): 6.1.14 Additive Expression
				// SHL_ASSIGN, SHR_ASSIGN, USHR_ASSIGN: 6.1.15. Bitwise Shift Expression
				// AND_ASSIGN, XOR_ASSIGN, OR_ASSIGN: 6.1.18. Binary Bitwise Expression
				return numberTypeRef(G);
			}
		}

		@Override
		public TypeRef caseCommaExpression(CommaExpression e) {
			if (e.getExprs().isEmpty()) {
				return unknown();
			}
			final Expression last = e.getExprs().get(e.getExprs().size() - 1);
			return ts.type(G, last);
		}

		@Override
		public TypeRef caseCastExpression(CastExpression e) {
			final TypeRef targetTypeRef = e.getTargetTypeRef();
			return targetTypeRef != null ? targetTypeRef : unknown();
		}

		// This is needed to remove the ambiguity:
		// N4ClassExpression is both a TypeDefininingElement and an Expression
		@Override
		public TypeRef caseN4ClassExpression(N4ClassExpression e) {
			return this.caseTypeDefiningElement(e);
		}

		// This is needed to remove the ambiguity:
		// FunctionExpression is both a TypeDefininingElement and an Expression
		@Override
		public TypeRef caseFunctionExpression(FunctionExpression e) {
			return this.caseTypeDefiningElement(e);
		}

		// ----------------------------------------------------------------------
		// AST nodes: miscellaneous
		// ----------------------------------------------------------------------

		@Override
		public TypeRef caseN4TypeVariable(N4TypeVariable n4TypeVar) {
			final TypeRef typeRef = TypeUtils.wrapTypeInTypeRef(n4TypeVar.getDefinedTypeVariable());
			return typeRef != null ? typeRef : unknown();
		}

		@Override
		public TypeRef caseCatchVariable(CatchVariable catchVariable) {
			if (javaScriptVariantHelper.enforceDynamicTypes(catchVariable)) {// e.g. plain ECMAScript
				return anyTypeRefDynamic(G);
			} else { // e.g. N4JS
				return anyTypeRef(G);
			}
		}

		@Override
		public TypeRef casePropertySpread(PropertySpread object) {
			return unknown(); // TODO GH-1337 add support for spread operator
		}

		@Override
		public TypeRef caseJSXElement(JSXElement jsxElem) {
			final TClassifier classifierReactElement = reactHelper.lookUpReactElement(jsxElem);
			if (classifierReactElement == null) {
				return unknown();
			}
			TypeRef propsType = reactHelper.getPropsType(jsxElem);
			return ref(classifierReactElement, propsType);
		}

		@Override
		public TypeRef caseJSXFragment(JSXFragment jsxFragment) {
			final TClassifier classifierReactElement = reactHelper.lookUpReactElement(jsxFragment);
			if (classifierReactElement == null) {
				return unknown();
			}
			return ref(classifierReactElement);
		}
	}

	private static boolean hasFormalParameterWithThisType(FunctionTypeExpression fte) {
		Iterator<EObject> iter = fte.eAllContents();
		while (iter.hasNext()) {
			final EObject obj = iter.next();
			if (obj instanceof TFormalParameter
					&& ((TFormalParameter) obj).getTypeRef() instanceof ThisTypeRef) {
				return true;
			}
		}
		return false;
	}
}
