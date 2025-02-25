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
package org.eclipse.n4js.scoping.imports

import com.google.common.base.Optional
import com.google.common.base.Throwables
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.HashMap
import org.apache.log4j.Logger
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.n4js.n4JS.DefaultImportSpecifier
import org.eclipse.n4js.n4JS.ImportDeclaration
import org.eclipse.n4js.n4JS.ImportSpecifier
import org.eclipse.n4js.n4JS.NamedImportSpecifier
import org.eclipse.n4js.n4JS.NamespaceImportSpecifier
import org.eclipse.n4js.n4JS.Script
import org.eclipse.n4js.resource.N4JSCache
import org.eclipse.n4js.resource.N4JSResource
import org.eclipse.n4js.scoping.ExportedElementsCollector
import org.eclipse.n4js.scoping.N4JSScopeProvider
import org.eclipse.n4js.scoping.accessModifiers.AbstractTypeVisibilityChecker
import org.eclipse.n4js.scoping.accessModifiers.InvisibleTypeOrVariableDescription
import org.eclipse.n4js.scoping.accessModifiers.TypeVisibilityChecker
import org.eclipse.n4js.scoping.accessModifiers.VariableVisibilityChecker
import org.eclipse.n4js.scoping.builtin.BuiltInTypeScope
import org.eclipse.n4js.scoping.builtin.GlobalObjectScope
import org.eclipse.n4js.scoping.builtin.NoPrimitiveTypesScope
import org.eclipse.n4js.scoping.members.MemberScope.MemberScopeFactory
import org.eclipse.n4js.scoping.utils.LocallyKnownTypesScopingHelper
import org.eclipse.n4js.scoping.utils.MultiImportedElementsMap
import org.eclipse.n4js.scoping.utils.ScopeSnapshotHelper
import org.eclipse.n4js.scoping.utils.UberParentScope
import org.eclipse.n4js.ts.types.IdentifiableElement
import org.eclipse.n4js.ts.types.ModuleNamespaceVirtualType
import org.eclipse.n4js.ts.types.TDynamicElement
import org.eclipse.n4js.ts.types.TExportableElement
import org.eclipse.n4js.ts.types.TMember
import org.eclipse.n4js.ts.types.TVariable
import org.eclipse.n4js.ts.types.Type
import org.eclipse.n4js.utils.ResourceType
import org.eclipse.n4js.validation.IssueCodes
import org.eclipse.xtext.naming.IQualifiedNameProvider
import org.eclipse.xtext.naming.QualifiedName
import org.eclipse.xtext.resource.EObjectDescription
import org.eclipse.xtext.resource.IEObjectDescription
import org.eclipse.xtext.resource.impl.AliasedEObjectDescription
import org.eclipse.xtext.scoping.IScope

/** internal helper collection type */
class IEODesc2ISpec extends HashMap<IEObjectDescription, ImportSpecifier> {}

/**
 * Helper for {@link N4JSScopeProvider N4JSScopeProvider}
 * {@link N4JSScopeProvider#scope_IdentifierRef_id(org.eclipse.n4js.n4JS.VariableEnvironmentElement) .scope_IdentifierRef_id()},
 * also used by helper {@link LocallyKnownTypesScopingHelper LocallyKnownTypesScopingHelper}.
 */
@Singleton
class ImportedElementsScopingHelper {

	private final static Logger LOGGER = Logger.getLogger(ImportedElementsScopingHelper);

	@Inject
	N4JSCache cache

	@Inject
	private TypeVisibilityChecker typeVisibilityChecker
	
	@Inject
	private IQualifiedNameProvider qualifiedNameProvider

	@Inject
	private VariableVisibilityChecker variableVisibilityChecker

	@Inject
	private ImportedElementsMap.Provider elementsMapProvider

	@Inject
	private MemberScopeFactory memberScopeFactory

	@Inject
	private ScopeSnapshotHelper scopesHelper;
	
	@Inject
	private ExportedElementsCollector exportedElementsCollector



	def IScope getImportedTypes(IScope parentScope, Script script) {
		val IScope scriptScope = cache.get(script.eResource, [|
			return findImportedElements(script, parentScope, true, false);
		], 'importedTypes', script)
		return scriptScope
	}

	def IScope getImportedValues(IScope parentScope, Script script) {
		val IScope scriptScope = cache.get(script.eResource, [|
			// filter out primitive types in next line (otherwise code like "let x = int;" would be allowed)
			val noPrimitiveBuiltIns = new NoPrimitiveTypesScope(BuiltInTypeScope.get(script.eResource.resourceSet));
			val uberParent = new UberParentScope("ImportedElementsScopingHelper-uberParent", noPrimitiveBuiltIns, parentScope);
			val globalObjectScope = getGlobalObjectProperties(uberParent, script);
			val result = findImportedElements(script, globalObjectScope, false, true);
			return result;
		], 'importedValues', script);
		return scriptScope
	}

	/**
	 * Creates a new QualifiedNamed for the given named import specifier.
	 *
	 * Determines the local name of the imported element based on the given import specifier.
	 */
	private def QualifiedName createQualifiedNameForAlias(NamedImportSpecifier specifier,
		TExportableElement importedElement) {
		val importedName = if (specifier.isDefaultImport) {
				// we got a default import of the form: import localName from "some/module"
				// -> use the string 'localName' (but this is not the alias property!)
				specifier.importedElementAsText
			} else {
				specifier.alias ?: specifier.importedElementAsText
		};
		return QualifiedName.create(importedName)
	}

	private def QualifiedName createImportedQualifiedTypeName(Type type) {
		return QualifiedName.create(getImportedName(type));
	}

	private def String getImportedName(Type type) {
		return type.name;
	}

	private def QualifiedName createImportedQualifiedTypeName(String namespace, Type type) {
		return QualifiedName.create(namespace, getImportedName(type));
	}

	private def IScope findImportedElements(Script script, IScope parentScope, boolean includeHollows, boolean includeValueOnlyElements) {
		val contextResource = script.eResource as N4JSResource;
		val imports = script.scriptElements.filter(ImportDeclaration)

		if (imports.empty) return parentScope;

		/** broken/invisible imported eObjects descriptions 
		 *  - in case of broken state of imports this can be {@link AmbiguousImportDescription}
		 *  - in case of alias/namespace imports multiple imported elements can have the same qualified name
		 */
		val invalidImports = new MultiImportedElementsMap();
		/** valid imported eObjects descriptions (in case of broken state of imports this can be {@link AmbiguousImportDescription})*/
		val validImports = elementsMapProvider.get(script);
		val originatorMap = new IEODesc2ISpec

		for (imp : imports) {
			val module = imp?.module;
			if (module !== null) {

				val topLevelElements = exportedElementsCollector.getExportedElements(module, contextResource, Optional.of(imp), includeHollows, includeValueOnlyElements);
				val tleScope = scopesHelper.scopeFor("scope_AllTopLevelElementsFromModule", module, IScope.NULLSCOPE, false, topLevelElements)

				for (specifier : imp.importSpecifiers) {
					switch (specifier) {
						NamedImportSpecifier: {
							processNamedImportSpecifier(specifier, imp, contextResource, originatorMap, validImports,
								invalidImports, includeValueOnlyElements, tleScope)
						}
						NamespaceImportSpecifier: {
							processNamespaceSpecifier(specifier, imp, script, contextResource, originatorMap, validImports,
								invalidImports, includeValueOnlyElements)
						}
					}
				}
			}
		}


//		 local broken elements are hidden by parent scope, both are hidden by valid local elements
		val invalidLocalScope = scopesHelper.scopeFor("findImportedElements-invalidImports", script, invalidImports.values)
		val localValidScope = scopesHelper.scopeFor("findImportedElements-validImports", script, parentScope, validImports.values)
		val importScope = new UberParentScope("findImportedElements-uberParent", localValidScope, invalidLocalScope)
		return new OriginAwareScope(script, importScope, originatorMap);
	}

	private def void processNamedImportSpecifier(NamedImportSpecifier specifier, ImportDeclaration imp,
			Resource contextResource, IEODesc2ISpec originatorMap,
			ImportedElementsMap validImports,
			ImportedElementsMap invalidImports, boolean includeValueOnlyElements, IScope tleScope) {

		val element = if (specifier.declaredDynamic) {
			(specifier.eResource as N4JSResource).module.internalDynamicElements.findFirst[it.astElement === specifier];
		} else {
			val name = if (specifier instanceof DefaultImportSpecifier)
						"default" else specifier.importedElementAsText;
			val qName = QualifiedName.create(name);

			val importedElem = tleScope.getSingleElement(qName);
			if (importedElem !== null && importedElem.EObjectOrProxy instanceof TExportableElement) {
				importedElem.EObjectOrProxy as TExportableElement
			} else {
				null
			}
		};

		if (element !== null && !element.eIsProxy) {

			if (!includeValueOnlyElements && element.isValueOnlyFrom(imp)) {
				return;
			}

			val importedQName = createQualifiedNameForAlias(specifier, element);
			val typeVisibility = isVisible(contextResource, element);
			if (typeVisibility.visibility) {

				addNamedImports(specifier, element, importedQName,
					originatorMap, validImports);

				val originalName = QualifiedName.create(element.name)

				if (specifier.alias !== null) {
					element.handleAliasedAccess(originalName, importedQName.toString, invalidImports, originatorMap, specifier)
				}
			} else {
				element.handleInvisible(invalidImports, importedQName, typeVisibility.accessModifierSuggestion,
					originatorMap, specifier)
			}
		}
	}

	private def void addNamedImports(NamedImportSpecifier specifier, TExportableElement element, QualifiedName importedName,
		IEODesc2ISpec originatorMap, ImportedElementsMap validImports) {
		val ieod = validImports.putOrError(element, importedName, IssueCodes.IMP_AMBIGUOUS);
		originatorMap.putWithOrigin(ieod, specifier)
	}

	private def void processNamespaceSpecifier(
		NamespaceImportSpecifier specifier,
		ImportDeclaration imp,
		Script script,
		Resource contextResource,
		IEODesc2ISpec originatorMap,
		ImportedElementsMap validImports,
		ImportedElementsMap invalidImports,
		boolean includeValueOnlyElements
	) {
		if (specifier.alias === null) {
			return; // if broken code, e.g. "import * as 123 as N from 'some/Module'"
		}
		if (script.module === null) {
			return; // when reconciliation of TModule fails due to hash mismatch
		}

		// add namespace to scope
		val namespaceName = specifier.alias;
		val namespaceQName = QualifiedName.create(namespaceName)
		val Type namespaceType = (script.module.internalTypes + script.module.exposedInternalTypes).findFirst [ interType |
			interType instanceof ModuleNamespaceVirtualType &&
				(interType as ModuleNamespaceVirtualType).module === imp.module
		]
		if (namespaceType === null) {
			// TODO GH-2002 remove this temporary debug logging
			val sb = new StringBuilder();
			sb.append("contextResource?.getURI(): " + contextResource?.getURI() + "\n");
			sb.append("specifier.definedType: " + specifier.definedType + "\n");
			sb.append("imp.module: " + imp.module + "\n");
			sb.append("script.module: " + script.module + "\n");
			sb.append("script.module.isPreLinkingPhase: " + script.module.isPreLinkingPhase + "\n");
			sb.append("script.module.isReconciled: " + script.module.isReconciled + "\n");
			sb.append("script.module.internalTypes.size: " + script.module.internalTypes.size + "\n");
			sb.append("script.module.exposedInternalTypes.size: " + script.module.exposedInternalTypes.size + "\n");
			for (type : (script.module.internalTypes + script.module.exposedInternalTypes)) {
				if (type instanceof ModuleNamespaceVirtualType) {
					sb.append("type[n].module: " + type.module + "\n");
				}
			}
			sb.append("\n");
			sb.append(Throwables.getStackTraceAsString(new IllegalStateException()));
			LOGGER.error("namespaceType not found\n" + sb.toString);
			return;
		}
		val ieodx = validImports.putOrError(namespaceType, namespaceQName, IssueCodes.IMP_AMBIGUOUS)
		originatorMap.putWithOrigin(ieodx, specifier)

		if (includeValueOnlyElements) {
			// add vars to namespace
			// (this is *only* about adding some IEObjectDescriptionWithError to improve error messages)
			for (importedVar : imp.module.exportedVariables) {
				val varVisibility = variableVisibilityChecker.isVisible(contextResource, importedVar);
				val varName = importedVar.name
				val qn = QualifiedName.create(namespaceName, varName)
				if (varVisibility.visibility) {
					val originalName = QualifiedName.create(varName)
					if (!invalidImports.containsElement(originalName)) {
						importedVar.handleNamespacedAccess(originalName, qn, invalidImports, originatorMap, specifier)
					}
				}
			}
			// add functions to namespace
			// (this is *only* about adding some IEObjectDescriptionWithError to improve error messages)
			for (importedFun : imp.module.functions) {
				val varVisibility = typeVisibilityChecker.isVisible(contextResource, importedFun);
				val varName = importedFun.name
				val qn = QualifiedName.create(namespaceName, varName)
				if (varVisibility.visibility) {
					val originalName = QualifiedName.create(varName)
					if (!invalidImports.containsElement(originalName)) {
						importedFun.handleNamespacedAccess(originalName, qn, invalidImports, originatorMap, specifier)
					}
				}
			}
		}

		// add types
		// (this is *only* about adding some IEObjectDescriptionWithError to improve error messages)
		for (importedType : imp.module.types) {
			val typeVisibility = typeVisibilityChecker.isVisible(contextResource, importedType);

			val qn = createImportedQualifiedTypeName(namespaceName, importedType)
			if (typeVisibility.visibility) {
				val originalName = createImportedQualifiedTypeName(importedType)
				if (!invalidImports.containsElement(originalName)) {
					importedType.handleNamespacedAccess(originalName, qn, invalidImports, originatorMap, specifier)
				}
			}
		}
	}

	private def handleAliasedAccess(IdentifiableElement element, QualifiedName originalName, String importedName,
		ImportedElementsMap invalidImports, IEODesc2ISpec originatorMap, ImportSpecifier specifier) {
		val invalidAccess = new PlainAccessOfAliasedImportDescription(EObjectDescription.create(originalName, element),
			importedName)
		invalidImports.put(originalName, invalidAccess)
	// TODO IDEBUG-702 originatorMap.putWithOrigin(invalidAccess, specifier)
	}

	private def handleNamespacedAccess(IdentifiableElement importedType, QualifiedName originalName, QualifiedName qn,
		ImportedElementsMap invalidImports, IEODesc2ISpec originatorMap, ImportSpecifier specifier) {
		val invalidAccess = new PlainAccessOfNamespacedImportDescription(
			EObjectDescription.create(originalName, importedType), qn)
		invalidImports.put(originalName, invalidAccess)
	// TODO IDEBUG-702 originatorMap.putWithOrigin(invalidAccess, specifier)
	}

	private def handleInvisible(IdentifiableElement importedElement, ImportedElementsMap invalidImports, QualifiedName qn,
		String visibilitySuggestion, IEODesc2ISpec originatorMap, ImportSpecifier specifier) {
		// TODO IDEBUG-702 val invalidAccess = new InvisibleTypeOrVariableDescription(EObjectDescription.create(qn, importedElement))
		val invalidAccess = invalidImports.putOrError(importedElement, qn, null)
		invalidAccess.addAccessModifierSuggestion(visibilitySuggestion)
		originatorMap.putWithOrigin(invalidAccess, specifier)
	}

	/** Add the description to the orginatorMap and include trace to the specifier in special Error-Aware IEObjectDesciptoins
	 * like AmbigousImportDescriptions.
	 */
	private def putWithOrigin(HashMap<IEObjectDescription, ImportSpecifier> originiatorMap,
		IEObjectDescription description, ImportSpecifier specifier) {
		originiatorMap.put(description, specifier);
		switch (description) {
			AmbiguousImportDescription: {
				description.originatingImports.add(specifier);
				// Handling of wrapped delegatee, since this information will not be available in other cases:
				val firstPlaceSpec = originiatorMap.get(description.delegate)
				// add only if not there yet:
				if (firstPlaceSpec !== null && ! description.originatingImports.contains(firstPlaceSpec)) {
					description.originatingImports.add(firstPlaceSpec)
				}
			}
		}
	}

	def private boolean isValueOnlyFrom(IdentifiableElement element, ImportDeclaration imp) {
		if (imp?.module === null) {
			return false;
		}
		if (imp.module.functions.contains(element)) {
			return true
		}
		if (imp.module.exportedVariables.contains(element)) {
			return true
		}
		return false;
	}

	private def AbstractTypeVisibilityChecker.TypeVisibility isVisible(Resource contextResource,
		IdentifiableElement element) {
		if (element instanceof TMember && ResourceType.getResourceType(element) == ResourceType.DTS)
			new AbstractTypeVisibilityChecker.TypeVisibility(true)
		else if (element instanceof Type)
			typeVisibilityChecker.isVisible(contextResource, element)
		else if (element instanceof TVariable)
			variableVisibilityChecker.isVisible(contextResource, element)
		else if (element instanceof TDynamicElement)
			return new AbstractTypeVisibilityChecker.TypeVisibility(true)
		else
			return new AbstractTypeVisibilityChecker.TypeVisibility(false);
	}

	/**
	 * Returns {@code true} if an import of the given {@link IEObjectDescription} should be
	 * regarded as ambiguous with the given {@link IdentifiableElement}.
	 */
	protected def boolean isAmbiguous(IEObjectDescription existing, IdentifiableElement element) {
		// make sure ambiguity is only detected in case of the same imported version of a type
		return true;
	}

	private def IEObjectDescription putOrError(ImportedElementsMap result,
		IdentifiableElement element, QualifiedName importedName, String issueCode) {
		// TODO IDEBUG-702 refactor InvisibleTypeOrVariableDescription / AmbiguousImportDescription relation
		var IEObjectDescription ret = null;
		val existing = result.getElements(importedName)

		if (!existing.empty && existing.get(0) !== null) {
			if (issueCode !== null) {
				switch existing {
					AmbiguousImportDescription: {
						existing.elements += element;
						ret = existing
					}
					default: {
						val error = new AmbiguousImportDescription(existing.head, issueCode, element)
						result.put(importedName, error)
						error.elements += element;
						ret = error
					}
				}
			}
		} else if (issueCode === null) {
			ret = createDescription(importedName, element)
			ret = new InvisibleTypeOrVariableDescription(ret)
			result.put(importedName, ret)
		} else {
			ret = createDescription(importedName, element)
			result.put(importedName, ret)
		}
		return ret;
	}
	
	private def IEObjectDescription createDescription(QualifiedName name, IdentifiableElement element) {
		if (name.lastSegment != element.name) {
			var qn = qualifiedNameProvider.getFullyQualifiedName(element);
			if (qn === null) {
				// non-directly-exported variable / function / type alias that is exported under an alias via a separate export declaration:
				qn = qualifiedNameProvider.getFullyQualifiedName(element.containingModule)?.append(element.name);
			}
			return new AliasedEObjectDescription(name, EObjectDescription.create(qn, element))
		} else {
			return EObjectDescription.create(name, element)
		}
	}

	/**
	 * global object scope indirectly cached, as this method is only called by getImportedIdentifiables (which is cached)
	 */
	private def IScope getGlobalObjectProperties(IScope parent, EObject context) {
		val globalObject = GlobalObjectScope.get(context.eResource.resourceSet).getGlobalObject
		memberScopeFactory.create(parent, globalObject, context, false, false, false)
	}

	private def void addAccessModifierSuggestion(IEObjectDescription description, String suggestion) {
		if (description instanceof InvisibleTypeOrVariableDescription) {
			description.accessModifierSuggestion = suggestion;
		}
	}
}
