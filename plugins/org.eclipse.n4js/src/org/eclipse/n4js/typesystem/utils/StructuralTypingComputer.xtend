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
package org.eclipse.n4js.typesystem.utils

import com.google.common.base.Optional
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.Collections
import java.util.List
import org.eclipse.emf.ecore.util.EcoreUtil.EqualityHelper
import org.eclipse.n4js.AnnotationDefinition
import org.eclipse.n4js.ts.typeRefs.OptionalFieldStrategy
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef
import org.eclipse.n4js.ts.typeRefs.TypeArgument
import org.eclipse.n4js.ts.typeRefs.TypeRef
import org.eclipse.n4js.ts.types.FieldAccessor
import org.eclipse.n4js.ts.types.PrimitiveType
import org.eclipse.n4js.ts.types.TEnum
import org.eclipse.n4js.ts.types.TField
import org.eclipse.n4js.ts.types.TGetter
import org.eclipse.n4js.ts.types.TMember
import org.eclipse.n4js.ts.types.TMethod
import org.eclipse.n4js.ts.types.TN4Classifier
import org.eclipse.n4js.ts.types.TSetter
import org.eclipse.n4js.ts.types.TypeVariable
import org.eclipse.n4js.ts.types.TypingStrategy
import org.eclipse.n4js.ts.types.util.Variance
import org.eclipse.n4js.types.utils.TypeCompareUtils
import org.eclipse.n4js.types.utils.TypeUtils
import org.eclipse.n4js.typesystem.N4JSTypeSystem
import org.eclipse.n4js.typesystem.constraints.TypeConstraint
import org.eclipse.n4js.utils.N4JSLanguageUtils
import org.eclipse.n4js.utils.N4JSLanguageUtils.EnumKind
import org.eclipse.n4js.utils.StructuralMembersTriple
import org.eclipse.n4js.utils.StructuralTypesHelper
import org.eclipse.n4js.validation.N4JSElementKeywordProvider
import org.eclipse.xtend.lib.annotations.Data

import static org.eclipse.n4js.AnnotationDefinition.*
import static org.eclipse.n4js.ts.types.TypingStrategy.*
import static org.eclipse.n4js.typesystem.utils.StructuralTypingResult.*
import static org.eclipse.n4js.utils.StructuralMembersPredicates.*

import static extension org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions.*
import static extension org.eclipse.n4js.utils.N4JSLanguageUtils.*

/**
 */
@Singleton
class StructuralTypingComputer extends TypeSystemHelperStrategy {

	@Inject private N4JSTypeSystem ts;
	@Inject private N4JSElementKeywordProvider keywordProvider;
	@Inject private StructuralTypesHelper structuralTypesHelper;

	@Data
	public static class StructTypingInfo {
		val RuleEnvironment G;
		val TypeRef left;
		val TypeRef right;
		val TypingStrategy leftStrategy;
		val TypingStrategy rightStrategy;
		val List<String> missingMembers = newArrayList();
		val List<String> wrongMembers = newArrayList();
	}

	public def StructuralTypingResult isStructuralSubtype(RuleEnvironment G, TypeRef left, TypeRef right) {
		val leftStrategy = left.typingStrategy;
		val rightStrategy = right.typingStrategy;

		// shortcut: keep in sync with Reducer#reduceStructuralTypeRef()
		if (leftStrategy === rightStrategy
			&& left.declaredType === right.declaredType
			&& (left.declaredType instanceof TN4Classifier || left.declaredType instanceof TypeVariable) // <-- IDEBUG-838
			&& left.structuralMembers.empty && right.structuralMembers.empty) {
			
			if (!left.generic) {
				return result(left, right, emptyList, emptyList);
			} else if (left instanceof ParameterizedTypeRef && right instanceof ParameterizedTypeRef) {
				val leftTypeArgs = left.typeArgsWithDefaults;
				val rightTypeArgs = right.typeArgsWithDefaults;
				if (leftTypeArgs.size === rightTypeArgs.size && leftTypeArgs.size === right.declaredType.typeVars.size) {
					var typeArgsEqual = true;
					for (var i = 0; typeArgsEqual && i < leftTypeArgs.size; i++) {
						val leftTypeArg = leftTypeArgs.get(i);
						val rightTypeArg = rightTypeArgs.get(i);
						
						val variance = right.declaredType.getVarianceOfTypeVar(i);
						val tempResult = tsh.checkTypeArgumentCompatibility(G, leftTypeArg, rightTypeArg, Optional.of(variance), false);
		
						if (tempResult.failure) {
							return failure(left.typeRefAsString + " is not a structural subtype of " + right.typeRefAsString + " due to type argument incompatibility: " + tempResult.failureMessage);
						}
					}
					return result(left, right, emptyList, emptyList);
				}
			}
		}

		// check if we are dealing with structural primitive types
		val primitiveSubtypingResult = isPrimitiveStructuralSubtype(G, left, right);
		if (null !== primitiveSubtypingResult) {
			return primitiveSubtypingResult;
		}
		
		// recursion guard (see method #isStructuralSubtypingInProgressFor() for details)
		if (G.isStructuralSubtypingInProgressFor(left, right)) {
			return result(left, right, emptyList, emptyList);
		}
		val G2 = G.wrap;
		G2.rememberStructuralSubtypingInProgressFor(left, right);

		val info = new StructTypingInfo(G2, left, right, leftStrategy, rightStrategy); // we'll collect error messages in here

		val iter = structuralTypesHelper.getMembersTripleIterator(G2, left, right, true);
		while (iter.hasNext) {
			// check if left member can fulfill the structural requirement imposed by right member
			checkMembers(left, iter.next, info);
		}

		return result(left, right, info.missingMembers, info.wrongMembers);
	}

	/**
	 * Special handling for primitive-structural types. 
	 * 
	 * <p>Note that this method only returns a non-null {@link StructuralTypingResult} if primitive structural subtyping is 
	 * applicable for the given operands {@code left} and {@code right}.</p>
	 * 
	 * @returns A {@link StructuralTypingResult} if primitive structural typing is applicable. {@code null} otherwise.
	 */
	def StructuralTypingResult isPrimitiveStructuralSubtype(RuleEnvironment G, TypeRef leftRaw, TypeRef right) {

		// for the purpose of the rules implemented here, a number-/string-based enum behaves like type 'number'/'string' (lower-case)
		val left = changeNumberOrStringBasedEnumToPrimitive(G, leftRaw);

		// check if we're dealing with structural primitive types
		val rightIsPrimitive = right.declaredType instanceof PrimitiveType;
		val leftIsPrimitive = left.declaredType instanceof PrimitiveType
		
		// primitive type on the right and non-primitive on the left
		if (rightIsPrimitive && !leftIsPrimitive) { 
			return failure(leftRaw.typeRefAsString + " is not a subtype of " + right.typeRefAsString);
		}
		// primitive type on the left and non-primitive on the right
		else if (leftIsPrimitive && !rightIsPrimitive) { 
			return failure(leftRaw.typeRefAsString + " is not a subtype of " + right.typeRefAsString);
		} 
		// primitive types on both sides
		else if (leftIsPrimitive && rightIsPrimitive) {
			// types must match nominally
			return if (left.declaredType === right.declaredType) {
				success();
			} else {
				failure(leftRaw.typeRefAsString + " is not a subtype of " + right.typeRefAsString);
			}
		} 
		// neither left nor right is primitive
		else {
			// shouldn't be handled by this method
			return null;
		}
	}

	/**
	 * Replace type references pointing to the type of a <code>@NumberBased</code> / <code>@StringBased</code> enum by
	 * a reference to built-in type <code>number</code> / <code>string</code>, leaving all other types unchanged.
	 */
	def private TypeRef changeNumberOrStringBasedEnumToPrimitive(RuleEnvironment G, TypeRef typeRef) {
		val declType = typeRef.declaredType;
		if (declType instanceof TEnum) {
			val enumKind = N4JSLanguageUtils.getEnumKind(declType);
			if (enumKind === EnumKind.NumberBased) {
				return G.numberTypeRef;
			} else if (enumKind === EnumKind.StringBased) {
				return G.stringTypeRef;
			}
		}
		return typeRef;
	}

	def private void checkMembers(TypeRef leftTypeRef, StructuralMembersTriple triple, StructTypingInfo info) {

		val leftMember = triple.left;
		val rightMember = triple.right;
		val leftOtherAccessor = triple.leftOtherAccessor;
		val leftStrategy = info.leftStrategy;
		val rightStrategy = info.rightStrategy;

		checkMembers(leftTypeRef, leftMember, rightMember, info);

		switch (rightStrategy) {

			// For any ~r~ right members.
			case STRUCTURAL_READ_ONLY_FIELDS: {

				// For any readable, non-optional right members. Initialized fields does not count as optional.
				val handleOptionality = leftTypeRef.ASTNodeOptionalFieldStrategy.isOptionalityLessRestrictedOrEqual(OptionalFieldStrategy.GETTERS_OPTIONAL);
				val memberNecessary = !rightMember.optional || (rightMember.optional && !handleOptionality);
				if (memberNecessary && READABLE_FIELDS_PREDICATE.apply(rightMember)) {

					// For ~w~ left members requires an explicit getter.
					if (STRUCTURAL_WRITE_ONLY_FIELDS === leftStrategy
						&& !GETTERS_PREDICATE.apply(leftMember)
						&& !GETTERS_PREDICATE.apply(leftOtherAccessor)) {

						info.wrongMembers.add(rightMember.name + " failed: readable field requires a getter in subtype.");

					// Otherwise any readable field is enough.
					} else if (!READABLE_FIELDS_PREDICATE.apply(leftMember) && !READABLE_FIELDS_PREDICATE.apply(leftOtherAccessor)) {
						info.wrongMembers.add(rightMember.name + " failed: readable field requires a readable field or a getter in subtype.");
					}
				}
			}

			// For any ~w~ right members.
			case STRUCTURAL_WRITE_ONLY_FIELDS: {

				// For any writable right members.
				if (WRITABLE_FIELDS_PREDICATE.apply(rightMember) && (leftTypeRef.ASTNodeOptionalFieldStrategy != OptionalFieldStrategy.FIELDS_AND_ACCESSORS_OPTIONAL)) {

					// If left is either ~r~ or ~i~, then an explicit setter is required.
					if ((STRUCTURAL_READ_ONLY_FIELDS === leftStrategy || STRUCTURAL_FIELD_INITIALIZER === leftStrategy)
						&& !SETTERS_PREDICATE.apply(leftMember)
						&& !SETTERS_PREDICATE.apply(leftOtherAccessor)) {

						info.wrongMembers.add(rightMember.name + " failed: writable field requires a setter in subtype.");

					// Otherwise any writable field (data field or setter) is sufficient.
					} else if (!WRITABLE_FIELDS_PREDICATE.apply(leftMember) && !WRITABLE_FIELDS_PREDICATE.apply(leftOtherAccessor)) {
						info.wrongMembers.add(rightMember.name + " failed: writable field requires a writable field or a setter in subtype.");
					}
				}
			}

			// For any ~i~ right members.
			case STRUCTURAL_FIELD_INITIALIZER: {

				// For any non-optional and writable right members.
				// Important: unlike in case of ~r~, here we treat initialized fields, such as @Final ones as optional fields.
				if (WRITABLE_FIELDS_PREDICATE.apply(rightMember) && rightMember.mandatoryField) {

					// If left is ~w~, then getters are required.
					if (STRUCTURAL_WRITE_ONLY_FIELDS === leftStrategy
						&& !(GETTERS_PREDICATE.apply(leftMember) || GETTERS_PREDICATE.apply(leftOtherAccessor))) {

						info.wrongMembers.add(rightMember.name + " failed: non-optional writable field requires a getter in subtype.");

					// Otherwise any readable fields are fine.
					} else if (!(READABLE_FIELDS_PREDICATE.apply(leftMember) || READABLE_FIELDS_PREDICATE.apply(leftOtherAccessor))) {
						info.wrongMembers.add(rightMember.name + " failed: non-optional writable field requires a readable field or a getter in subtype.");
					}
				}
			}

			default: {

				// If the left member is ~r~, ~w~ and/or ~i~ type.
				if (STRUCTURAL_READ_ONLY_FIELDS === leftStrategy || STRUCTURAL_WRITE_ONLY_FIELDS === leftStrategy || STRUCTURAL_FIELD_INITIALIZER === leftStrategy) {
					switch (rightMember) {
						// If right is writable field, a getter/setter pair is mandatory on the left.
						case rightMember.writeableField: {
							if (!isGetterSetterPair(leftMember, leftOtherAccessor)) {
								info.wrongMembers.add(rightMember.name + " failed: writable field requires a getter/setter pair in subtype.");
							}
						}
						// If right is readable, we require an explicit getter.
						case READABLE_FIELDS_PREDICATE.apply(rightMember): {
							if (!(GETTERS_PREDICATE.apply(leftMember) || GETTERS_PREDICATE.apply(leftOtherAccessor))) {
								info.wrongMembers.add(rightMember.name + " failed: read-only field requires a getter in subtype.");
							}
						}
						// If there is a setter on the right, then we need a setter on the left.
						case SETTERS_PREDICATE.apply(rightMember) : {
							if (!(SETTERS_PREDICATE.apply(leftMember) || SETTERS_PREDICATE.apply(leftOtherAccessor))) {
								info.wrongMembers.add(rightMember.name + " failed: setter requires a setter in subtype.");
							}
						}
					}
				} else {
					// for a writable field on the right-hand side, require a getter/setter pair on the left
					if (rightMember.writeableField && leftMember instanceof FieldAccessor) {
						if (!(leftOtherAccessor instanceof TSetter)) {
							if(leftTypeRef.ASTNodeOptionalFieldStrategy != OptionalFieldStrategy.FIELDS_AND_ACCESSORS_OPTIONAL) {
								val isSpecialCaseOfDispensableGetterForOptionalField = leftMember instanceof TSetter
									&& rightMember.optional
									&& (leftTypeRef.ASTNodeOptionalFieldStrategy == OptionalFieldStrategy.GETTERS_OPTIONAL);
								if(!isSpecialCaseOfDispensableGetterForOptionalField) {
									// special error message in case only either a getter XOR setter is supplied for a field
									val msgSpecial = if(leftMember instanceof TGetter && rightMember.optional) {
										"optional writable field requires at least a setter in subtype."
									} else {
										"writable field requires a field or a getter/setter pair in subtype."
									};
									info.wrongMembers.add(rightMember.name + " failed: " + msgSpecial);
								}
							}
						} else {
							// check type of setter as usual
							checkMembers(leftTypeRef, leftOtherAccessor, rightMember, info);
						}
					}

				}
			}
		}
	}

	/** Returns with {@code true} iff the  the arguments are a getter-setter accessor pair. */
	def private isGetterSetterPair(TMember firstLeft, TMember secondLeft) {
		(GETTERS_PREDICATE.apply(firstLeft) || GETTERS_PREDICATE.apply(secondLeft))
		&& (SETTERS_PREDICATE.apply(secondLeft) || SETTERS_PREDICATE.apply(secondLeft));
	}

	/**
	 * Checks if the member 'left' (may be <code>null</code> if not found) fulfills the structural requirement represented
	 * by member 'right'.  In the error case, the two lists of error messages in 'info' are updated accordingly.
	 * <p>
	 * NOTE: in case of a field on right-hand side, this method accepts a getter OR(!) a setter of appropriate type
	 * on left side; the requirement that BOTH a getter AND setter must be provided for a writable field must be
	 * checked outside this method.
	 */
	def private void checkMembers(TypeRef leftTypeRef, TMember left, TMember right, StructTypingInfo info) {
		val G = info.G;

		// !!! keep the following aligned with below method #reduceMembers() !!!
		if (left === null) {
			// no corresponding member found on left side
			if (memberIsMissing(leftTypeRef, right, info)) {

				if (right instanceof TMethod) {
					if (right.isCallSignature) {
						// in case a call signature is missing on the left side, it is ok
						// if the left side is itself a function and is override-compatible:
						tsh.addSubstitutions(G, info.right);
						val TypeRef rightTypeRef = ts.substTypeVariables(G, TypeUtils.createTypeRef(right));
						val result = ts.subtype(G, info.left, rightTypeRef);
						if (result.success) {
							return; // success
						}
						if (ts.subtypeSucceeded(G, info.left, G.functionTypeRef)) {
							info.wrongMembers.add(keywordProvider.keyword(right, info.rightStrategy) + " failed: " + result.failureMessage);
							return;
						}
					}
				}
				if (right instanceof TField) {
					val leftOptionalStrategy = leftTypeRef.ASTNodeOptionalFieldStrategy;
					if (leftOptionalStrategy === OptionalFieldStrategy.GETTERS_OPTIONAL) {
						info.missingMembers.add("setter or field " + right.name);
						return;
					}
				}

				info.missingMembers.add(keywordProvider.keyword(right, info.rightStrategy) + " " + right.name);
			}

		} else {
			// found a corresponding member
			// -> make sure types are compatible

			val mtypes = getMemberTypes(left, right, info);

			var Variance variance;
			if (left.optional && !right.optional) {
				info.missingMembers.add(left.name + ' failed: non-optional member requires a corresponding non-optional member in the structural subtype.');
				return;
			} else if (right.writeableField && left instanceof TField) {
				// Fields are on both sides.
				// T_FL = T_FR
				if (left.readOnlyField
					&& STRUCTURAL_FIELD_INITIALIZER !== info.rightStrategy
					&& STRUCTURAL_READ_ONLY_FIELDS !== info.rightStrategy) {
					info.wrongMembers.add(right.name + " failed: field is read-only.");
					return;
				} else {
					variance = Variance.INV;
				}
			} else if (right instanceof TSetter || left instanceof TSetter) {
				// Setter on one side (other side may be field or setter).
				// contra-variant
				variance = Variance.CONTRA;
			} else {
				// Other cases such as methods and function expressions.
				// Only L<:R.
				variance = Variance.CO;
			}

			val result = tsh.checkTypeArgumentCompatibility(G, mtypes.key, mtypes.value, Optional.of(variance), true);
			if (result.failure) {
				info.wrongMembers.add(getMemberName(right) + " failed: " + result.failureMessage);
			}
		}
	}

	/**
	 * Same as previous method, but instead of actually checking the types, we return 0..2 constraints. This would normally
	 * belong into class <code>InferenceContext</code>, but is placed here to keep it aligned with above method more
	 * easily.
	 */
	def public List<TypeConstraint> reduceMembers(RuleEnvironment G,
		TypeRef leftTypeRef, TMember left, TMember right, StructTypingInfo info) {

		// !!! keep the following aligned with above method #checkMembers() !!!
		if (left === null) {
			// no corresponding member found on left side
			if (memberIsMissing(leftTypeRef, right, info)) {
				if (right instanceof TMethod) {
					if (right.isCallSignature) {
						// in case a call signature is missing on the left side, it is ok
						// if the left side is itself a function and is override-compatible:
						tsh.addSubstitutions(G, info.right);
						val TypeRef rightTypeRef = ts.substTypeVariables(G, TypeUtils.createTypeRef(right));
						return Collections.singletonList(
							new TypeConstraint(info.left, rightTypeRef, Variance.CO));
					}
				}
				return Collections.singletonList(TypeConstraint.FALSE);
			} else {
				return Collections.emptyList(); // this is like returning TypeConstraint.TRUE
			}

		} else {
			val mtypes = getMemberTypes(left, right, info);

			var Variance variance;
			if (left.optional && !right.optional) {
				return Collections.singletonList(TypeConstraint.FALSE);
			} else if (right.writeableField && left instanceof TField) {
				if (left.readOnlyField
					&& STRUCTURAL_FIELD_INITIALIZER !== info.rightStrategy
					&& STRUCTURAL_READ_ONLY_FIELDS !== info.rightStrategy) {
					return Collections.singletonList(TypeConstraint.FALSE);
				} else {
					variance = Variance.INV;
				}
			} else if (right instanceof TSetter || left instanceof TSetter) {
				variance = Variance.CONTRA;
			} else {
				variance = Variance.CO;
			}
			return tsh.reduceTypeArgumentCompatibilityCheck(G, mtypes.key, mtypes.value, Optional.of(variance), false);
		}
	}

	def private boolean memberIsMissing(TypeRef leftTypeRef, TMember right, StructTypingInfo info) {
		val rightMemberIsOptional = rightMemberIsOptional(leftTypeRef, right, info.rightStrategy);
		if (rightMemberIsOptional) {
			// nothing to do (rightMember is optional or initialized for ~i~ typing.)
			return false;
		} else if (right?.containingType === info.G.objectType) {
			// ignore object members, can only happen if left is structural field type
			return false;
		} else {
			// standard case of missing member on left side -> report error
			return true;
		}
	}

	/**
	 * This method implements Req-IDE-240500 in language spec.
	 */
	def private boolean rightMemberIsOptional(TypeRef leftTypeRef, TMember right, TypingStrategy rightStrategy) {
		val leftOptionalStrategy = leftTypeRef.ASTNodeOptionalFieldStrategy;

		// Whether a right member is optional depends on both the right typing strategy and the right optionality field strategy.
		val boolean rightMemberIsOptional = switch (rightStrategy) {
			case STRUCTURAL,
			case STRUCTURAL_FIELDS: {
				// L <: ~N or L <: ~~N
				if (!right.optional) {
					false
				} else {
					(leftOptionalStrategy == OptionalFieldStrategy.FIELDS_AND_ACCESSORS_OPTIONAL) ||
					(right instanceof TGetter && leftOptionalStrategy.isOptionalityLessRestrictedOrEqual(OptionalFieldStrategy.GETTERS_OPTIONAL))
				}
			}

			case STRUCTURAL_WRITE_ONLY_FIELDS: {
				// L <: ~w~N
				right.optional && (leftOptionalStrategy == OptionalFieldStrategy.FIELDS_AND_ACCESSORS_OPTIONAL)
			}

			case STRUCTURAL_READ_ONLY_FIELDS: {
				// L <: ~r~N
				right.optional && leftOptionalStrategy.isOptionalityLessRestrictedOrEqual(OptionalFieldStrategy.GETTERS_OPTIONAL)
			}

			case STRUCTURAL_FIELD_INITIALIZER: {
				// L <: ~i~N
				(right.optional && leftOptionalStrategy.isOptionalityLessRestrictedOrEqual(OptionalFieldStrategy.GETTERS_OPTIONAL)) ||
				right.initializedField || right.optionalSetter
			}

			default: {
				false
			}
		};
		return rightMemberIsOptional;
	}

	/**
	 * Store a guard in the given rule environment to note that we are in the process of inferring
	 * left ~<: right.
	 * <p>
	 * IDEBUG-171:
	 * If we are already computing the structural subtype relation left ~<: right and we are again asked
	 * whether left ~<: right, then we simply want to return success.
	 * The rationale is that if we run into a cycle while checking the members' types, we can simply say the
	 * members causing the cycle won't render the overall evaluation false ("an uns soll es nicht scheitern").
	 * <p>
	 * EXAMPLE 1:
	 * <pre>
	 * class Element {
	 * 	public ~Element child;
	 * }
	 * var ~Element e1;
	 * var ~Element e2;
	 * //e1 = e2;   // remove comment to get stack overflow when disabling if-clause in #isSubtype()
	 * </pre>
	 * <p>
	 * EXAMPLE 2:
	 * <pre>
	 * class A {
	 * 	public ~B f;
	 * }
	 * class B {
	 * 	public ~A f;
	 * }
	 * var ~A a;
	 * var ~B b;
	 * //a = b;   // remove comment to get stack overflow when disabling if-clause in #isSubtype()
	 * </pre>
	 * Note how this is analogous to what EMF is doing when computing structural equality as explained
	 * in the paragraph on "populating a two way map" in the following EMF API documentation:
	 * {@link EqualityHelper}
	 */
	def private void rememberStructuralSubtypingInProgressFor(RuleEnvironment G, TypeRef left, TypeRef right) {
		G.put(GUARD_STRUCTURAL_TYPING_COMPUTER__IN_PROGRESS_FOR_TYPE_REF -> (left.wrap -> right.wrap), Boolean.TRUE);
	}

	def private boolean isStructuralSubtypingInProgressFor(RuleEnvironment G, TypeRef left, TypeRef right) {
		return G.get(GUARD_STRUCTURAL_TYPING_COMPUTER__IN_PROGRESS_FOR_TYPE_REF -> (left.wrap -> right.wrap)) !== null;
	}

	def private TypeCompareUtils.SemanticEqualsWrapper wrap(TypeRef typeRef) {
		new TypeCompareUtils.SemanticEqualsWrapper(typeRef);
	}

	def private Pair<TypeArgument, TypeArgument> getMemberTypes(TMember leftMember, TMember rightMember,
		StructTypingInfo info) {

		val G = info.G;
		val typeLeftRaw = ts.type(G, leftMember);
		val typeRightRaw = ts.type(G, rightMember);

		// replace bound type variables with type arguments
		val G_left = G.wrap;
		val G_right = G.wrap;
		tsh.addSubstitutions(G_left, info.left);
		tsh.addSubstitutions(G_right, info.right);

		val typeLeft = ts.substTypeVariables(G_left, typeLeftRaw);
		val typeRight = ts.substTypeVariables(G_right, typeRightRaw);

		return typeLeft -> typeRight;
	}

	/**
	 * Returns with {@code true} if the member argument is
	 * <ul>
	 * <li>*NOT* {@link TMember#isOptional() optional} member,</li>
	 * <li>*NOT* {@link #isInitializedField(TMember) initialized field} and</li>
	 * <li>*NOT* {@link #isOptionalSetter(TMember) optional setter}.</li>
	 * </ul>
	 * Otherwise returns with {@code false}.
	 */
	def private isMandatoryField(TMember it) {
		null !== it && !optional && !initializedField && !optionalSetter;
	}

	/**
	 * Argument is an instance of a {@link TField field} and {@link TField#isHasExpression() has initializer expression}.
	 * This method is {@code null} safe.
	 */
	def private isInitializedField(TMember it) {
		if (it instanceof TField) { hasExpression } else false;
	}

	/**
	 * Returns {@code true} if the member argument is an instance of a {@link TSetter setter} and has
	 * {@link AnnotationDefinition#IDE_1996 IDE_1996} annotation. Otherwise returns with {@code false}.
	 */
	def private isOptionalSetter(TMember it) {
		it instanceof TSetter && PROVIDES_INITIALZER.hasAnnotation(it);
	}

	def private String getMemberName(TMember member) {
		if (member instanceof TMethod) {
			if (member.isCallSignature) {
				return "call signature";
			} else if (member.isConstructSignature) {
				return "construct signature";
			}
		}
		return member.name;
	}
}
