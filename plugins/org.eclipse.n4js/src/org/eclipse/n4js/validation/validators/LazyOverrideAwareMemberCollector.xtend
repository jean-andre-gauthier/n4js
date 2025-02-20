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
package org.eclipse.n4js.validation.validators

import com.google.common.collect.Lists
import java.util.ArrayList
import java.util.Collections
import java.util.List
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef
import org.eclipse.n4js.ts.typeRefs.TypeRef
import org.eclipse.n4js.ts.types.ContainerType
import org.eclipse.n4js.ts.types.PrimitiveType
import org.eclipse.n4js.ts.types.TClass
import org.eclipse.n4js.ts.types.TClassifier
import org.eclipse.n4js.ts.types.TInterface
import org.eclipse.n4js.ts.types.TMember
import org.eclipse.n4js.ts.types.Type
import org.eclipse.n4js.types.utils.TypeUtils
import org.eclipse.n4js.typesystem.utils.AbstractCompleteHierarchyTraverser
import org.eclipse.n4js.typesystem.utils.RuleEnvironment
import org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions
import org.eclipse.n4js.utils.DeclMergingHelper
import com.google.inject.Inject
import org.eclipse.n4js.smith.N4JSDataCollectors
import org.eclipse.n4js.smith.Measurement

/**
 * Collects all members, including inherited members, of a type and omits some members overridden in the type hierarchy.
 * That is, members overridden in the type hierarchy are removed and replaced by the overridden members. However,
 * this is done lazily, that is not all cases are considered, as the result is further validated anyway.
 * That is,
 * <ul>
 * <li>data fields and corresponding accessors are not recognized to override each other
 * <li>methods (and fields) inherited from interfaces are all added and not filtered.
 * <li>private members are added to result (which is required for validation: cannot override private member etc.)
 * </ul>
 * Thus, this collector should only be used for validation purposes.
 */

public class LazyOverrideAwareMemberCollector {
	@Inject
	private DeclMergingHelper declMergingHelper;


	/**
	 * Collects all members, including owned members and members defined in implicit super types.
	 */
	def List<TMember> collectAllMembers(ContainerType<?> type) {
		return new LazyOverrideAwareMemberCollectorX(type, declMergingHelper, true, false).getResult();
	}

	/**
	 * Collects all inherited members, including members defined in implicit super types; owned members are omitted.
	 */
	def List<TMember> collectAllInheritedMembers(ContainerType<?> type) {
		return new LazyOverrideAwareMemberCollectorX(type, declMergingHelper, true, true).getResult();
	}

	/**
	 * Collects all declared members, including owned members; members defined in implicit super types are omitted.
	 */
	def List<TMember> collectAllDeclaredMembers(ContainerType<?> type) {
		return new LazyOverrideAwareMemberCollectorX(type, declMergingHelper, false, false).getResult();
	}

	/**
	 * Collects all declared inherited members; owned members and members defined in implicit super types are omitted.
	 */
	def List<TMember> collectAllDeclaredInheritedMembers(ContainerType<?> type) {
		return new LazyOverrideAwareMemberCollectorX(type, declMergingHelper, false, true).getResult();
	}

	static class LazyOverrideAwareMemberCollectorX extends AbstractCompleteHierarchyTraverser<List<TMember>> {
	
		val boolean includeImplicitSuperTypes;
	
		private var List<TMember> result;
		private val RuleEnvironment G;
	
		val boolean onlyInheritedMembers
		

		/**
		 * Creates a new collector with optional support for implicit super types, better use static helper methods.
		 *
		 * @param type
		 *            the base type. Must be contained in a resource set if <code>includeImplicitSuperTypes</code> is set to
		 *            <code>true</code>.
		 * @param includeImplicitSuperTypes
		 *            if true also members of implicit super types will be collected; otherwise only members of declared
		 *            super types are included.
		 * @param onlyInheritedMembers
		 * 			if true, owned members of type are ignore, that is only inherited members are collected
		 * @throws IllegalArgumentException
		 *             if <code>includeImplicitSuperTypes</code> is set to <code>true</code> and <code>type</code> is not
		 *             contained in a properly initialized N4JS resource set.
		 */
		private new(ContainerType<?> type, DeclMergingHelper declMergingHelper, boolean includeImplicitSuperTypes, boolean onlyInheritedMembers) {
			super(type, declMergingHelper);
			this.onlyInheritedMembers = onlyInheritedMembers;
			this.includeImplicitSuperTypes = includeImplicitSuperTypes;
			result = createResultInstance();
			G = if ( includeImplicitSuperTypes) RuleEnvironmentExtensions.newRuleEnvironment(type) else null;
		}

		override Measurement getMeasurement() {
			return N4JSDataCollectors.dcTHT_LazyOverrideAwareMemberCollectorX.getMeasurementIfInactive("HierarchyTraverser");
		}

		def List<TMember> createResultInstance() {
			return Lists.newLinkedList()
		}
	
		override protected List<TMember> doGetResult() {
			return result;
		}
	
		override protected void doProcess(ContainerType<?> type) {
			if (type instanceof TClassifier) {
				val ownedMembers = type.getOwnedMembers()
				result.addAll(ownedMembers);
			}
		}
	
		override protected doProcess(PrimitiveType currentType) {
			// nothing to do in this case
		}
	
		def protected processAndReplace(TClassifier type) {
			val ownedMembers = type.getOwnedMembers()
			val iterInherited = result.iterator
			while (iterInherited.hasNext()) {
				val inherited = iterInherited.next;
				if (ownedMembers.exists[
					name==inherited.name && memberType===inherited.memberType && static===inherited.static
				]) {
					iterInherited.remove
				}
			}
			result.addAll(ownedMembers)
		}
	
	
		/**
		 * Does not add owned members and add inherited members overridden aware.
		 */
		override boolean visitTClass(ParameterizedTypeRef typeRef, TClass object) {
			val parentResult = result
			if (object!==bottomType) {
				result = createResultInstance();
			}

			// add and merge
			doSwitchTypeRefs(getSuperTypes(object));
			doSwitchTypeRefs(object.getImplementedInterfaceRefs());

			// add and replace
			if (!onlyInheritedMembers || object!=bottomType) { // do not add
				processAndReplace(object); // ownedMembers
			}

			if (parentResult!==result) {
				parentResult.addAll(result); // merge
				result = parentResult;
			}

			return Boolean.FALSE;
		}
	
	
	
		/**
		 * Does not add owned members and add inherited members overridden aware.
		 */
		override public boolean visitTInterface(ParameterizedTypeRef typeRef, TInterface object) {
			val parentResult = result
			if (object!==bottomType) {
				result = createResultInstance();
			}

			doSwitchTypeRefs(object.getSuperInterfaceRefs());
			doSwitchTypeRefs(object.getSuperInterfaceRefs());
			if (!onlyInheritedMembers || object!=bottomType) { // do not add
				processAndReplace(object);
			}

			if (parentResult!==result) {
				parentResult.addAll(result); // merge
				result = parentResult;
			}
	
			return Boolean.FALSE;
		}
	
	
		override protected List<ParameterizedTypeRef> getImplicitSuperTypes(Type t) {
			if (includeImplicitSuperTypes) {
				val List<ParameterizedTypeRef> implSuperTypeRefs = new ArrayList<ParameterizedTypeRef>();
				for (TypeRef currTypeRef : RuleEnvironmentExtensions.collectAllImplicitSuperTypes(G,
						TypeUtils.createTypeRef(t)))
					implSuperTypeRefs.add(currTypeRef as ParameterizedTypeRef); // they should all be ParameterizedTypeRefs
				return implSuperTypeRefs;
			}
			else
				return Collections.emptyList();
		}
	}
}
