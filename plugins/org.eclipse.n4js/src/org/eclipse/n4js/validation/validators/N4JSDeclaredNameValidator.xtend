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

import com.google.common.collect.ListMultimap
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Multimaps
import com.google.common.collect.Sets
import com.google.inject.Inject
import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.ListIterator
import java.util.Set
import java.util.stream.Collectors
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EStructuralFeature
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.n4js.AnnotationDefinition
import org.eclipse.n4js.n4JS.AbstractVariable
import org.eclipse.n4js.n4JS.AnnotableElement
import org.eclipse.n4js.n4JS.Block
import org.eclipse.n4js.n4JS.CatchVariable
import org.eclipse.n4js.n4JS.ExportableElement
import org.eclipse.n4js.n4JS.FormalParameter
import org.eclipse.n4js.n4JS.FunctionDeclaration
import org.eclipse.n4js.n4JS.FunctionDefinition
import org.eclipse.n4js.n4JS.FunctionExpression
import org.eclipse.n4js.n4JS.FunctionOrFieldAccessor
import org.eclipse.n4js.n4JS.ImportDeclaration
import org.eclipse.n4js.n4JS.ImportSpecifier
import org.eclipse.n4js.n4JS.N4ClassExpression
import org.eclipse.n4js.n4JS.N4ClassifierDeclaration
import org.eclipse.n4js.n4JS.N4JSASTUtils
import org.eclipse.n4js.n4JS.N4JSPackage
import org.eclipse.n4js.n4JS.N4NamespaceDeclaration
import org.eclipse.n4js.n4JS.N4TypeDeclaration
import org.eclipse.n4js.n4JS.N4TypeDefinition
import org.eclipse.n4js.n4JS.N4TypeVariable
import org.eclipse.n4js.n4JS.NamedElement
import org.eclipse.n4js.n4JS.NamedImportSpecifier
import org.eclipse.n4js.n4JS.NamespaceImportSpecifier
import org.eclipse.n4js.n4JS.ObjectLiteral
import org.eclipse.n4js.n4JS.PropertyAssignment
import org.eclipse.n4js.n4JS.PropertyGetterDeclaration
import org.eclipse.n4js.n4JS.PropertySetterDeclaration
import org.eclipse.n4js.n4JS.Script
import org.eclipse.n4js.n4JS.SetterDeclaration
import org.eclipse.n4js.n4JS.VariableDeclaration
import org.eclipse.n4js.n4JS.VariableEnvironmentElement
import org.eclipse.n4js.packagejson.projectDescription.ProjectType
import org.eclipse.n4js.scoping.builtin.GlobalObjectScope
import org.eclipse.n4js.scoping.builtin.N4Scheme
import org.eclipse.n4js.scoping.utils.SourceElementExtensions
import org.eclipse.n4js.ts.types.IdentifiableElement
import org.eclipse.n4js.ts.types.SyntaxRelatedTElement
import org.eclipse.n4js.ts.types.TClass
import org.eclipse.n4js.ts.types.TMember
import org.eclipse.n4js.ts.types.TypableElement
import org.eclipse.n4js.ts.types.TypesPackage
import org.eclipse.n4js.utils.EcoreUtilN4
import org.eclipse.n4js.utils.N4JSLanguageUtils
import org.eclipse.n4js.validation.AbstractN4JSDeclarativeValidator
import org.eclipse.n4js.validation.JavaScriptVariantHelper
import org.eclipse.n4js.validation.ValidatorMessageHelper
import org.eclipse.n4js.workspace.WorkspaceAccess
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.nodemodel.util.NodeModelUtils
import org.eclipse.xtext.service.OperationCanceledManager
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.Check
import org.eclipse.xtext.validation.EValidatorRegistrar

import static org.eclipse.n4js.validation.IssueCodes.*
import org.eclipse.n4js.n4JS.PropertyNameValuePair

/**
 */
class N4JSDeclaredNameValidator extends AbstractN4JSDeclarativeValidator {

	@Inject ValidatorMessageHelper messageHelper;

	@Inject WorkspaceAccess workspaceAccess;

	@Inject SourceElementExtensions sourceElementExtensions;

	@Inject JavaScriptVariantHelper jsVariantHelper;

	@Inject OperationCanceledManager operationCanceledManager;


	public static val BASE_JS_TYPES = Sets.newHashSet(
		#['Object', 'Function', 'Array', 'String', 'Boolean', 'Number', 'Math', 'Date', 'RegExp', 'Error', 'JSON']);
	public static val BASE_GLOBAL_NAMES = Sets.newHashSet(
		#["number", "string", "boolean", "any", "pathSelector", "i18nKey", "typeName", "N4Object", "N4Class", "N4Enum"]);

	/**
	 * NEEEDED
	 *
	 * when removed check methods will be called twice once by N4JSValidator, and once by
	 * AbstractDeclarativeN4JSValidator
	 */
	override register(EValidatorRegistrar registrar) {
		// nop
	}

	/**
	 * Check duplicated names of declared properties in the {@link ObjectLiteral}s.
	 * Similar to check of duplicates in scope.
	 */
	@Check
	def void checkNameConflictsInObjectLiteral(ObjectLiteral objectLiteral) {
		objectLiteral.propertyAssignments.filter[!it.findName.nullOrEmpty].toList.stream.collect(
			Collectors.groupingBy([findName(it)])).filter[name, lstEO|lstEO.size > 1].forEach [ name, lstEO |
			//appearance order
			lstEO.sort(
				[ e1, e2|
					Integer.valueOf(NodeModelUtils.getNode(e1).getOffset()).compareTo(
						Integer.valueOf(
							NodeModelUtils.getNode(e2).getOffset()
						))]);
			val iter = lstEO.listIterator();
			val baseEO = iter.next();
			iter.forEachRemaining(
				[ dupeEO |
					{

						//getter-setter pair
						if ((baseEO instanceof PropertyGetterDeclaration && dupeEO instanceof PropertySetterDeclaration) ||
							(baseEO instanceof PropertySetterDeclaration && dupeEO instanceof PropertyGetterDeclaration)) {
							return;
						}

						addIssue(
							StringExtensions.toFirstUpper(
								getMessageForAST_NAME_DUPLICATE_ERR(messageHelper.description(dupeEO, name),
									messageHelper.descriptionWithLine(baseEO, name))), dupeEO, findNameEAttribute(dupeEO),
							AST_NAME_DUPLICATE_ERR);
					}
				]);
		]
	}

	/**
	 * Constraints 126 (Global Definitions), except polyfills
	 */
	@Check
	def void checkExportableNameConflictsWithBuiltIn(ExportableElement exportableElement) {
		if (exportableElement instanceof AnnotableElement) {
			if (AnnotationDefinition.GLOBAL.hasAnnotation(exportableElement)) {
				if (N4Scheme.isFromResourceWithN4Scheme(exportableElement)) {
					return; // this validation does not apply to built-in types (i.e. builtin_js.n4jsd, etc.)
				}
				if (N4JSLanguageUtils.isNonStaticPolyfill(exportableElement)) {
					return; // of course it is possible to fill predefined types
				}
				val name = exportableElement.declaredName;
				if (name !== null) {
					if (BASE_JS_TYPES.contains(name)) {
						val project = workspaceAccess.findProjectContaining(exportableElement);
						if (project === null || project.type !== ProjectType.RUNTIME_ENVIRONMENT) {
							addIssue(getMessageForAST_GLOBAL_JS_NAME_CONFLICT(name), exportableElement,
								AST_GLOBAL_JS_NAME_CONFLICT);
						}
					} else if (BASE_GLOBAL_NAMES.contains(name)) {
						addIssue(getMessageForAST_GLOBAL_NAME_CONFLICT(name), exportableElement,
							AST_GLOBAL_NAME_CONFLICT);
					}
				}
			}
		}
	}

	/**
	 * Check name conflicts in script.
	 * For every scope ({@link VariableEnvironmentElement} instance) will
	 *  - check for name conflict with members of global object
	 *  - check for name conflict with implicit function arguments
	 *  - check for name conflicts with outer (containing) scope
	 */
	@Check
	def void checkNameConflicts(Script script) {
		val ListMultimap<String, TMember> globalNames = getGlobalNames(script.eResource);

		script.checkNameConflicts(newHashSet, globalNames, getCancelIndicator());
	}

	/**
	 * Delegates proper checks for a given scope.
	 * After checks are done for current scope, calls itself recursively on children.
	 * Recursive call is passing set of used names in current scope, allowing child scope to perform proper comparison checks.
	 * Only names are passed, actual {@link EObject}s are resolved only if name clash is detected.
	 */
	def private void checkNameConflicts(VariableEnvironmentElement vee, Set<String> outerNames, ListMultimap<String, TMember> globalNames, CancelIndicator ci) {
		val ListMultimap<String, EObject> localNames = getLocalNames(vee);

		vee.checkGlobalNamesConflict(localNames, globalNames, ci);

		vee.checkLocalScopeNamesConflict(localNames, ci);

		// (1) add 'localNames' to 'outerNames'
		// (note: for performance reasons, we change 'outerNames' in place and restore its state after processing the nested VEEs)
		val localNamesWithConflictToOuterNames = new ArrayList(localNames.size);
		val localNamesAddedToOuterNames = new ArrayList(localNames.size);
		for (localName : localNames.keySet) {
			val wasAdded = outerNames.add(localName);
			if (wasAdded) {
				// 'outerNames' did not contain 'localName' -> no conflict
				// remember that 'localName' has to be removed from 'outerNames' after processing nested VEEs
				localNamesAddedToOuterNames += localName;
			} else {
				// 'outerNames' did already contain 'localName' -> conflict!
				localNamesWithConflictToOuterNames += localName;
			}
		}

		vee.checkOuterScopesNamesConflict(localNames, localNamesWithConflictToOuterNames, ci);

		// (2) process nested VEEs
		for (veeNested : vee.nestedScopes.toIterable) {
			operationCanceledManager.checkCanceled(ci);
			veeNested.checkNameConflicts(outerNames, globalNames, ci);
		}

		// (3) restore state of 'outerNames'
		outerNames -= localNamesAddedToOuterNames;
	}

	/**
	 * checks if provided localNames of given scope are not conflicting with names in global object
	 */
	def private checkGlobalNamesConflict(VariableEnvironmentElement vee, ListMultimap<String, EObject> localNames,
		ListMultimap<String, TMember> globalNames, CancelIndicator ci) {

		for (globalEntry : globalNames.asMap.entrySet) {
			operationCanceledManager.checkCanceled(ci);
			val name = globalEntry.key;
			if (name != 'eval') { // already validated by the AST Structure validator
				val globalObjectMember = globalEntry.value.head;
				val localObjects = localNames.get(name);
				for (innerScopeObject : localObjects) {
					addIssue(
						StringExtensions.toFirstUpper(
							getMessageForAST_GLOBAL_NAME_SHADOW_ERR(
								messageHelper.description(innerScopeObject, name),
								messageHelper.description(globalObjectMember, name))), innerScopeObject,
						findNameEAttribute(innerScopeObject), AST_GLOBAL_NAME_SHADOW_ERR);
				}
			}
		}
	}

	/**
	 * check (pre-computed) localNames of the given scope against each other. When conflict is found EObjects
	 * that create conflict are analyzed and if appropriate error marker is issued.
	 */
	def private checkLocalScopeNamesConflict(VariableEnvironmentElement vee, ListMultimap<String, EObject> localNames, CancelIndicator ci) {

		if(vee instanceof Block
				&& vee.eContainer instanceof FunctionOrFieldAccessor
				&& (vee.eContainer as FunctionOrFieldAccessor).body === vee) {
			checkLocalScopeNamesConflict_letConstSpecialCase(vee.eContainer as FunctionOrFieldAccessor);
		}

		// search for duplicates in local scope
		if (localNames.keySet.size < localNames.size) {

			// found 1 or more duplicate names

			for (entry : localNames.asMap.entrySet.filter[value.size > 1]) {
				operationCanceledManager.checkCanceled(ci);
				val name = entry.key;
				val eObjects = entry.value;
				if (eObjects.size > 1) {
					//appearance order
					val lstEO = new ArrayList(eObjects);
					lstEO.sort(
						[EObject e1, EObject e2|
							val n1=NodeModelUtils.getNode(e1);
							val n2=NodeModelUtils.getNode(e2);
							// null-node will happen if artificially enriched AST,
							// for example instance of {@link LocalArgumentsVariable} is encountered.
							return if( n1===null || n2===null ) { 0 } else { n1.getOffset() - n2.getOffset() };
						]);
					val ListIterator<EObject> iter = lstEO.listIterator();
					val EObject baseEO = iter.next();
					iter.forEachRemaining(
						[ dupeEO |
							{

								if (baseEO instanceof N4ClassExpression || dupeEO instanceof N4ClassExpression) {
									return;
								}

								// in case of when we duplicate element creating given scope (like function names)
								// then we issue shadowing
								if (baseEO.equals(vee)) {

									if (dupeEO instanceof FormalParameter) {
										addIssue(
											StringExtensions.toFirstUpper(
												getMessageForAST_NAME_SHADOW_ERR(
													messageHelper.description(dupeEO, name),
													messageHelper.description(baseEO, name))), dupeEO,
											findNameEAttribute(dupeEO), AST_NAME_SHADOW_ERR);
									} else {
										addIssue(
											StringExtensions.toFirstUpper(
												getMessageForAST_NAME_SHADOW_ERR(
													messageHelper.description(dupeEO, name),
													messageHelper.descriptionWithLine(baseEO, name))), dupeEO,
											findNameEAttribute(dupeEO), AST_NAME_SHADOW_ERR);
									}
									return;
								}

								// otherwise mark duplicates
								if (dupeEO instanceof FormalParameter) {
									addIssue(
										StringExtensions.toFirstUpper(
											getMessageForAST_NAME_DUPLICATE_ERR(
												messageHelper.description(dupeEO, name),
												messageHelper.description(baseEO, name))), dupeEO,
										findNameEAttribute(dupeEO), AST_NAME_DUPLICATE_ERR);
								} else if ((dupeEO instanceof NamedImportSpecifier &&
									baseEO instanceof NamedImportSpecifier) ||
									(dupeEO instanceof NamespaceImportSpecifier &&
									baseEO instanceof NamespaceImportSpecifier)) {

									/*
									 * Duplication only between ImportSpecifiers is handled in N4JSImportValidator
									 */
									return;
								} else {
									if (jsVariantHelper.isExternalMode(baseEO)) {
										// allow name sharing only in N4JSD files
										if (!isEqualNameDuplicate(baseEO, dupeEO)) {
											return;
										}
									}
									
									
									if (!( // do not create issues for polyfills conflicting with imports, as they might fill them
										dupeEO instanceof N4ClassifierDeclaration && baseEO instanceof ImportSpecifier &&
										N4JSLanguageUtils.isNonStaticPolyfill(dupeEO as N4ClassifierDeclaration)
										// TODO IDE-1735 does this check need to be activated for static polyfills?
									)) {
										addIssue(
											StringExtensions.toFirstUpper(
												getMessageForAST_NAME_DUPLICATE_ERR(
													messageHelper.description(dupeEO, name),
													messageHelper.descriptionWithLine(baseEO, name))), dupeEO,
											findNameEAttribute(dupeEO), AST_NAME_DUPLICATE_ERR);
									}
								}
							}
						]
					)
				}
			}
		}
	}
	
	def private boolean isEqualNameDuplicate(EObject baseEO, EObject dupeEO) {
		if (isValueOnly(baseEO) && isHollow(dupeEO)) {
			return false;
		} else if (isValueOnly(dupeEO) && isHollow(baseEO)) {
			return false;
		}
		return true;
	}
	
	def private boolean isValueOnly(EObject eo) {
		var EObject eo2 = eo;
		if (eo instanceof NamedImportSpecifier) {
			eo2 = eo.importedElement
		}

		return (eo2 instanceof TypableElement && N4JSLanguageUtils.isValueOnlyElement(eo2 as TypableElement));
	}
	
	def private boolean isHollow(EObject eo) {
		var EObject eo2 = eo;
		if (eo instanceof NamedImportSpecifier) {
			eo2 = eo.importedElement
		}
		return (eo2 instanceof TypableElement && N4JSLanguageUtils.isHollowElement(eo2 as TypableElement));
	}

	/**
	 * This method handles a special case related to let/const:
	 * <pre>
	 * function foo1(param) {
	 *     let param; // <-- ES6 engines will throw error at runtime!
	 * }
	 * function foo2(param) {
	 *     {
	 *         let param; // <-- valid ES6!
	 *     }
	 * }
	 * </pre>
	 * This method takes care of producing a validation error in the first case above.
	 */
	def private checkLocalScopeNamesConflict_letConstSpecialCase(FunctionOrFieldAccessor fun) {
		val block = fun.body;
		val fpars = switch(fun) {
			FunctionDefinition: fun.fpars
			SetterDeclaration: #[fun.fpar]
			default: #[]
		};
		val fparNames = fpars.map[name].toSet;
		val declaredLetConst = getNameDeclarations(block).filter(VariableDeclaration).filter[N4JSASTUtils.isBlockScoped(it)];
		declaredLetConst.filter[fparNames.contains(declaredName)].forEach[dupeEO|
			val name = dupeEO.declaredName;
			val baseEO = fpars.filter[it.name==name].head;
			addIssue(
				StringExtensions.toFirstUpper(
					getMessageForAST_NAME_DUPLICATE_ERR(
						messageHelper.description(dupeEO, name),
						messageHelper.description(baseEO, name))), dupeEO,
				findNameEAttribute(dupeEO), AST_NAME_SHADOW_ERR);
		];
	}

	/**
	 * check (pre-computed) localNames of the given scope with (pre-computed) all names used in parent scopes.
	 * If conflict is detected traverse containers of the scope, until {@link EObject} creating using the name is found.
	 * If it meets error conditions we issue proper error, otherwise ast is traversed further up.
	 * If no {@link EObject} that meets error conditions is found, no error is issued.
	 * AST traversing stops at first {@link EObject} that meets error conditions, so no multiple errors should be issued on a given declaration.
	 */
	def private void checkOuterScopesNamesConflict(VariableEnvironmentElement scope, ListMultimap<String,EObject> localNames,
		List<String> localNamesWithConflictToOuterNames, CancelIndicator ci) {

		for (n : localNamesWithConflictToOuterNames) {
			operationCanceledManager.checkCanceled(ci);

			val isExceptionCase = n.equals("arguments"); //special case, separate handling
			if (!isExceptionCase) {

				val innerScopeObjects = localNames.get(n);
				for (innerScopeObject : innerScopeObjects) {
					var EObject conflict;
					var EObject conflictContainer = scope.eContainer
					while (conflict === null) {
						if (conflictContainer === null) {
							return; //no conflict found in AST, allowed shadowing
						}

						val z = conflictContainer.findOuterDeclaration(n)
						if (z.nullOrEmpty) {

							//no conflicts in this parent, loop over its parent
							conflictContainer = conflictContainer.eContainer
						} else {
							conflict = z.head

							var EObject outerScopeObject = conflict
							var name = n

							if (outerScopeObject instanceof N4ClassExpression ||
								innerScopeObject instanceof N4ClassExpression) {
								return;
							}

							// only FD and FE, we don't want N4Methods, thus don't check against FunctionDefinition
							if (outerScopeObject instanceof FunctionDeclaration ||
								outerScopeObject instanceof FunctionExpression) {

								if (innerScopeObject instanceof FunctionExpression ||
									innerScopeObject instanceof N4ClassExpression) {

									// named expression don't introduce name in outer scope
									return;
								}

								if (innerScopeObject instanceof FormalParameter
									&& outerScopeObject instanceof FunctionDefinition
									&& innerScopeObject.eContainer === outerScopeObject) {

									// formal parameters hides containing function name
									addIssue(
										StringExtensions.toFirstUpper(
											getMessageForAST_NAME_SHADOW_ERR(
												messageHelper.description(innerScopeObject, name),
												messageHelper.description(outerScopeObject, name))), innerScopeObject,
										findNameEAttribute(innerScopeObject), AST_NAME_SHADOW_ERR);
									return;
								}

								if (outerScopeObject.equals(scope) == false) {

									// you can shadow function
									return;
								}

								// but not containing function
								addIssue(
									StringExtensions.toFirstUpper(
										getMessageForAST_NAME_SHADOW_ERR(
											messageHelper.description(innerScopeObject, name),
											messageHelper.descriptionWithLine(outerScopeObject, name))),
									innerScopeObject, findNameEAttribute(innerScopeObject), AST_NAME_SHADOW_ERR);
								return;
							}

							if (innerScopeObject instanceof CatchVariable) {
								if(outerScopeObject instanceof CatchVariable){
									//both http://jslint.com/ and http://jshint.com/
									//produce warning here, additionally
									//this does not work in some JS environments, e.g. IE8
									//adding a warning, consider removing if it gets annoying
									//in our platform code
									if(jsVariantHelper.isN4JSMode(innerScopeObject)){
										addIssue(
											StringExtensions.toFirstUpper(
												getMessageForAST_NAME_SHADOW_WARN(
													messageHelper.description(innerScopeObject, name),
													messageHelper.descriptionWithLine(outerScopeObject, name))),
											innerScopeObject, findNameEAttribute(innerScopeObject), AST_NAME_SHADOW_WARN);
										return;
									}
									//if js mode
									return;
								}
								addIssue(
									StringExtensions.toFirstUpper(
										getMessageForAST_NAME_SHADOW_ERR(
											messageHelper.description(innerScopeObject, name),
											messageHelper.descriptionWithLine(outerScopeObject, name))),
									innerScopeObject, findNameEAttribute(innerScopeObject), AST_NAME_SHADOW_ERR);
								return;
							}
							return;

						}
					}
				}
			}
		}
	}

	def private Iterable<EObject> findOuterDeclaration(EObject scope, String name) {

		val decl = scope.eContents.filter[name.equals(it.declaredName)]
		return decl
	}

	/**
	 * returns {@link List} of names declared in the global object.
	 */
	def ListMultimap<String, TMember> getGlobalNames(Resource resource) {
		val result = MultimapBuilder.linkedHashKeys.linkedListValues.build();
		for (member : EcoreUtil2.getAllContentsOfType(getGlobalObject(resource), TMember)) {
			val name = member.name;
			if (!name.nullOrEmpty) {
				result.put(name, member);
			}
		}
		return Multimaps.unmodifiableListMultimap(result);
	}

	/**
	 * returns {@link EObject} instance of the global object
	 * Returned data is cached, resolved only if new (or changed) {@link Resource}
	 */
	def TClass getGlobalObject(Resource resource) {
		GlobalObjectScope.get(resource.getResourceSet()).getGlobalObject()
	}

	// TODO after cleanup, the following methods should be put into the N4JS.xcore (or put in N4JSASTUtils and add convenience methods to N4JS.xcore ??)
	/**
	 * Returns all elements that declare a name in scope 'scope' without(!) considering nested scopes.
	 */
	def private Iterable<EObject> getNameDeclarations(VariableEnvironmentElement scope) {
		val resource = scope.eResource;
		val namedEOs = <EObject>newArrayList

		// in some cases, the scopes we obtain from ordinary scoping are not sufficient for the purpose of this
		// validation, so add a few additional elements:
		switch(scope) {
			Script: 
				namedEOs += scope.scriptElements.filter(ImportDeclaration).flatMap[importSpecifiers].toList
			FunctionOrFieldAccessor:
				namedEOs += scope.implicitArgumentsVariable
		}

		// add all elements from the scope as computed by ordinary scoping:
		namedEOs += sourceElementExtensions.collectVisibleIdentifiableElements(scope).map[backToAST(resource)];

		return namedEOs.filter[!it.declaredName.nullOrEmpty];
	}

	/**
	 * Returns list of names declared in scope 'scope' without(!) considering nested scopes.
	 */
	def private ListMultimap<String, EObject> getLocalNames(VariableEnvironmentElement scope) {
		val result = MultimapBuilder.linkedHashKeys.linkedListValues.build();
		for (nameDecl : scope.nameDeclarations) {
			val name = nameDecl.declaredName;
			result.put(name, nameDecl);
		}
		return Multimaps.unmodifiableListMultimap(result);
	}

	/**
	 * Returns nested scopes of 'scope'. Only direct sub-scopes of 'scope' are returned, no sub-sub-scopes,
	 * i.e. sub-scopes of sub-scopes.
	 */
	def private Iterator<VariableEnvironmentElement> getNestedScopes(VariableEnvironmentElement scope) {
		EcoreUtilN4.getAllContentsFiltered(scope, [!(it.eContainer.createsScope && it.eContainer !== scope)]).filter[
			createsScope].filter(VariableEnvironmentElement)
	}

	def private boolean createsScope(EObject eo) {
		return eo instanceof VariableEnvironmentElement
	}

	/**
	 * Resolves name of given {@link EObject} if possible, null otherwise.
	 * Will not return actual name for all objects, only the ones interesting from duplicates/shadowing point of view.
	 * (so variable declarations, function definitions, type definitions...). In case of named import specifiers returns alias
	 * if possible, otherwise name of imported element.
	 * returns null in other cases.
	 * Does not check value of the returned name, so it can be null or empty string.
	 */
	def private String getDeclaredName(EObject eo) {

		if (eo instanceof FunctionDeclaration || eo instanceof FunctionExpression || eo instanceof N4TypeDefinition ||
			eo instanceof AbstractVariable) {
			return eo.findName
		}

		if (eo instanceof NamedImportSpecifier) {
			val NamedImportSpecifier namedIS = eo;
			if (!namedIS.getAlias().nullOrEmpty) {
				return namedIS.getAlias()
			} else {
				return namedIS.getImportedElementAsText();
			}
		}

		if (eo instanceof NamespaceImportSpecifier) {
			//practical approach : ignoring names in the namespace, as it is the only case of name with dot in the name
			//proper approach: check against all elements contributed by the namespace
			 return eo.alias;
		}

		return null
	}

	/** helper dispatch because we lack one uniform interface for getName */
	def private dispatch String findName(NamedElement it) {
		name
	}

	/** helper dispatch because we lack one uniform interface for getName */
	def private dispatch String findName(IdentifiableElement it) {
		name
	}

	/**
	 * returns attribute holding name for given EObject. Throws error if provided EObject does not define name
	 * attribute.
	 */
	def private EStructuralFeature findNameEAttribute(EObject eo) {
		if (eo instanceof N4NamespaceDeclaration) {
			return N4JSPackage.Literals.N4_NAMESPACE_DECLARATION__NAME;
		}

		if (eo instanceof N4TypeDeclaration) {
			return N4JSPackage.Literals.N4_TYPE_DECLARATION__NAME;
		}

		if (eo instanceof N4ClassExpression) {
			return N4JSPackage.Literals.N4_CLASS_EXPRESSION__NAME;
		}

		if (eo instanceof FunctionExpression) {
			return N4JSPackage.Literals.FUNCTION_EXPRESSION__NAME;
		}

		if (eo instanceof FunctionDeclaration) {
			return N4JSPackage.Literals.FUNCTION_DECLARATION__NAME;
		}

		if (eo instanceof AbstractVariable) {
			return N4JSPackage.Literals.ABSTRACT_VARIABLE__NAME;
		}

		if (eo instanceof N4TypeVariable) {
			return N4JSPackage.Literals.N4_TYPE_VARIABLE__NAME;
		}

		if (eo instanceof NamedImportSpecifier) {
			if (eo.getAlias() !== null) {
				return N4JSPackage.Literals.NAMED_IMPORT_SPECIFIER__ALIAS;
			}
			if (eo.getImportedElement().getName() !== null) {
				return TypesPackage.Literals.IDENTIFIABLE_ELEMENT__NAME;
			}
		}

		if (eo instanceof NamespaceImportSpecifier) {
			if (eo.getAlias() !== null) {
				return N4JSPackage.Literals.NAMESPACE_IMPORT_SPECIFIER__ALIAS;
			}
		}

		if (eo instanceof IdentifiableElement) {
			return TypesPackage.Literals.IDENTIFIABLE_ELEMENT__NAME;
		}

		if (eo instanceof PropertyNameValuePair && (eo as PropertyNameValuePair).property !== null) {
			return N4JSPackage.Literals.PROPERTY_NAME_VALUE_PAIR__PROPERTY;
		}

		if (eo instanceof PropertyAssignment) {
			return N4JSPackage.Literals.PROPERTY_NAME_OWNER__DECLARED_NAME;
		}

		throw new RuntimeException("cannot obtain name attribute for " + eo);
	}

	/**
	 * If <code>potentialTModuleElement</code> is a TModule element that has a related AST node within
	 * <code>resource</code>, then return this AST node, otherwise return <code>potentialTModuleElement</code> itself.
	 */
	def private EObject backToAST(EObject potentialTModuleElement, Resource resource) {
		if(potentialTModuleElement instanceof SyntaxRelatedTElement) {
			if(potentialTModuleElement.eResource === resource) {
				return potentialTModuleElement.astElement;
			}
		}
		return potentialTModuleElement;
	}
}
