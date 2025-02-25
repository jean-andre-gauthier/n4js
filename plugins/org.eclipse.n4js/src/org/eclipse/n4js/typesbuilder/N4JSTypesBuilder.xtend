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
package org.eclipse.n4js.typesbuilder

import com.google.inject.Inject
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.n4js.n4JS.ExportDeclaration
import org.eclipse.n4js.n4JS.ExportableElement
import org.eclipse.n4js.n4JS.FunctionDeclaration
import org.eclipse.n4js.n4JS.FunctionDefinition
import org.eclipse.n4js.n4JS.FunctionExpression
import org.eclipse.n4js.n4JS.MethodDeclaration
import org.eclipse.n4js.n4JS.ModifiableElement
import org.eclipse.n4js.n4JS.N4ClassDeclaration
import org.eclipse.n4js.n4JS.N4ClassExpression
import org.eclipse.n4js.n4JS.N4EnumDeclaration
import org.eclipse.n4js.n4JS.N4InterfaceDeclaration
import org.eclipse.n4js.n4JS.N4JSASTUtils
import org.eclipse.n4js.n4JS.N4Modifier
import org.eclipse.n4js.n4JS.N4NamespaceDeclaration
import org.eclipse.n4js.n4JS.N4TypeAliasDeclaration
import org.eclipse.n4js.n4JS.NamespaceExportSpecifier
import org.eclipse.n4js.n4JS.NamespaceImportSpecifier
import org.eclipse.n4js.n4JS.ObjectLiteral
import org.eclipse.n4js.n4JS.Script
import org.eclipse.n4js.n4JS.TryStatement
import org.eclipse.n4js.n4JS.TypeDefiningElement
import org.eclipse.n4js.n4JS.VariableDeclarationContainer
import org.eclipse.n4js.naming.ModuleNameComputer
import org.eclipse.n4js.naming.SpecifierConverter
import org.eclipse.n4js.resource.N4JSResource
import org.eclipse.n4js.smith.N4JSDataCollectors
import org.eclipse.n4js.ts.typeRefs.FunctionTypeExpression
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef
import org.eclipse.n4js.ts.typeRefs.StructuralTypeRef
import org.eclipse.n4js.ts.typeRefs.TypeRef
import org.eclipse.n4js.ts.types.AbstractNamespace
import org.eclipse.n4js.ts.types.TClass
import org.eclipse.n4js.ts.types.TInterface
import org.eclipse.n4js.ts.types.TModule
import org.eclipse.n4js.ts.types.TVariable
import org.eclipse.n4js.ts.types.Type
import org.eclipse.n4js.ts.types.TypesFactory
import org.eclipse.n4js.utils.ResourceType
import org.eclipse.n4js.validation.JavaScriptVariantHelper
import org.eclipse.n4js.workspace.WorkspaceAccess
import org.eclipse.xtext.naming.IQualifiedNameConverter
import org.eclipse.xtext.resource.DerivedStateAwareResource

import static extension org.eclipse.n4js.utils.N4JSLanguageUtils.*

/**
 * This class with its {@link N4JSTypesBuilder#createTModuleFromSource(DerivedStateAwareResource,boolean) createTModuleFromSource()}
 * method is the single entry point for the types builder package. The only exception is the public method
 * {@link N4JSFunctionDefinitionTypesBuilder#updateTFunction(FunctionExpression, FunctionTypeExprOrRef, TypeRef) updateTFunction()}
 * in N4JSFunctionDefinitionTypesBuilder, which is called from Xsemantics.
 * <p>
 * Derives the types model from the AST, i.e. creates a {@link TModule} with its contents
 * from a {@link Script} and its children. The types model is stored at the second index of the resource.
 * The types model contains for each exportable or type defining element a corresponding entry e.g.
 * for a {@link N4ClassDeclaration} a {@link TClass} is created. Later when linking only the types are
 * used in place of the original objects.
 * <p>
 * The types builder must not use type inference because this would lead to a resolution of lazy
 * cross-references at a too early stage. Instead, the types builder creates ComputedTypeRefs that
 * will later be resolved either on demand or by calling {@link N4JSResource#flattenModule()}.
 */
public class N4JSTypesBuilder {

	@Inject(optional=true) TypesFactory typesFactory = TypesFactory.eINSTANCE
	@Inject extension N4JSTypesBuilderHelper
	@Inject extension N4JSNamespaceDeclarationTypesBuilder
	@Inject extension N4JSClassDeclarationTypesBuilder
	@Inject extension N4JSInterfaceDeclarationTypesBuilder
	@Inject extension N4JSEnumDeclarationTypesBuilder
	@Inject extension N4JSTypeAliasDeclarationTypesBuilder
	@Inject extension N4JSObjectLiteralTypesBuilder
	@Inject extension N4JSFunctionDefinitionTypesBuilder
	@Inject extension N4JSVariableStatementTypesBuilder
	@Inject extension N4JSTypesFromTypeRefBuilder
	@Inject extension N4JSImportTypesBuilder
	@Inject extension N4JSExportDefinitionTypesBuilder

	@Inject extension ModuleNameComputer
	@Inject private WorkspaceAccess workspaceAccess
	@Inject private IQualifiedNameConverter qualifiedNameConverter
	@Inject private SpecifierConverter specifierConverter
	@Inject protected JavaScriptVariantHelper jsVariantHelper;

	/** Thrown when reconciliation of a TModule fails due to a hash mismatch. */
	public static class RelinkTModuleHashMismatchException extends IllegalStateException {

		public new(URI resourceURI) {
			super("cannot link existing TModule to new AST due to hash mismatch: " + resourceURI);
		}
	}

	/**
	 * When demand-loading an AST for a resource that already has a TModule (usually retrieved from the index) by
	 * calling SyntaxRelatedTElement#getAstElement(), we are facing a challenge: we could simply replace the original
	 * TModule by a new TModule created in the same way as in the standard case of loading an empty resource from
	 * source, i.e. with method {@link N4JSTypesBuilder#createTModuleFromSource(DerivedStateAwareResource, boolean)
	 * #createTModuleFromSource()}. However, this would lead to the issue of all existing references to the original,
	 * now replaced TModule to now have an invalid target object (not contained in a resource anymore, proxy resolution
	 * impossible, etc.).
	 * <p>
	 * As a solution, this method provides a 2nd mode of the types builder in which not a new TModule is created from
	 * the AST, but an existing TModule is reused, i.e. the types builder does not create anything but simply creates
	 * the bidirectional links between AST nodes and TModule elements.
	 * <p>
	 * This method should be called after the AST has been loaded, with the original TModule at second position in the
	 * resource's contents. If the AST and TModule were created from different versions of the source, checked via an
	 * MD5 hash, or the rewiring fails for other reasons, an {@link IllegalStateException} is thrown. In that case, the
	 * state of the AST and TModule are undefined (i.e. linking may have taken place partially).
	 */
	def public void relinkTModuleToSource(DerivedStateAwareResource resource, boolean preLinkingPhase) {
		try (val m1 = N4JSDataCollectors.dcTypesBuilder.getMeasurement();
			 val m2 = N4JSDataCollectors.dcTypesBuilderRelink.getMeasurement()) {
			m1.getClass(); // suppress unused variable warning from Xtend
			m2.getClass(); // suppress unused variable warning from Xtend

			doRelinkTModuleToSource(resource, preLinkingPhase);
		}
	}

	def private void doRelinkTModuleToSource(DerivedStateAwareResource resource, boolean preLinkingPhase) {
		val parseResult = resource.getParseResult();
		if (parseResult !== null) {

			val script = parseResult.rootASTElement as Script;

			val TModule module = resource.contents.get(1) as TModule;

			val astMD5New = N4JSASTUtils.md5Hex(resource);
			if (astMD5New != module.astMD5) {
				throw new RelinkTModuleHashMismatchException(resource.getURI());
			}
			module.reconciled = true;

			script.relinkTypeModelElementsForImports(module, preLinkingPhase)

			script.buildTypesFromTypeRefs(module, preLinkingPhase);

			script.relinkTypes(module, preLinkingPhase, new RelinkIndices());

			module.astElement = script;
			script.module = module;

		} else {
			throw new IllegalStateException("resource has no parse result: " + resource.URI);
		}
	}

	/**
	 * This method is the single entry point for the types builder package. The only exception is the public method
	 * {@link N4JSFunctionDefinitionTypesBuilder#updateTFunction(FunctionExpression,FunctionTypeExprOrRef,TypeRef) updateTFunction()}
	 * in N4JSFunctionDefinitionTypesBuilder, which is called from Xsemantics.
	 * <p>
	 * Creates an {@link TModule} with the module path name (@see {@link ModuleNameComputer#getModulePath(Resource)})
	 * of the resource (replacing the dots with slashes). For all {@link ExportableElement} and
	 * {@link TypeDefiningElement} corresponding types like {@link TClass}, {@link TInterface} are created and assigned
	 * as containment references to {@link TModule}. Afterwards the so created {@link TModule} tree is browsed for
	 * {@link TVariable}s which do not have a type reference yet. For these there right hand side expressions is
	 * checked for a {@link TypeDefiningElement} for this a {@link ParameterizedTypeRef} is created having this element
	 * as declared type. The parameterized type ref is then assigned as type ref to the {@link TVariable}.
	 */
	def public void createTModuleFromSource(DerivedStateAwareResource resource, boolean preLinkingPhase) {
		try (val m1 = N4JSDataCollectors.dcTypesBuilder.getMeasurement();
			 val m2 = N4JSDataCollectors.dcTypesBuilderCreate.getMeasurement()) {
			m1.getClass(); // suppress unused variable warning from Xtend
			m2.getClass(); // suppress unused variable warning from Xtend

			doCreateTModuleFromSource(resource, preLinkingPhase);
		}
	}

	def private void doCreateTModuleFromSource(DerivedStateAwareResource resource, boolean preLinkingPhase) {
		val parseResult = resource.getParseResult();
		if (parseResult !== null) {

//			UtilN4.takeSnapshotInGraphView("TB start (preLinking=="+preLinkingPhase+")",resource.resourceSet);
			val script = parseResult.rootASTElement as Script;

			val TModule result = typesFactory.createTModule;

			result.astMD5 = N4JSASTUtils.md5Hex(resource);
			result.reconciled = false;

			val qualifiedModuleName = resource.qualifiedModuleName;
			result.simpleName = qualifiedModuleName.lastSegment;
			result.qualifiedName = qualifiedNameConverter.toString(qualifiedModuleName);
			result.preLinkingPhase = preLinkingPhase;

			val project = workspaceAccess.findProjectContaining(resource);
			if (project !== null) {
				result.projectID = project.name;
				result.packageName = project.packageName;
				result.vendorID = project.vendorId;

				// main module
				val mainModuleSpec = project.mainModule;
				if (mainModuleSpec !== null) {
					result.mainModule = qualifiedModuleName ==
						specifierConverter.toQualifiedName(mainModuleSpec);
				}
			}

			result.copyAnnotations(script, preLinkingPhase);

			script.createTypeModelElementsForImports(result, preLinkingPhase);

			result.n4jsdModule = jsVariantHelper.isExternalMode(script);

			// Setting Polyfill property.
			result.staticPolyfillModule = result.isContainedInStaticPolyfillModule;
			result.staticPolyfillAware = result.isContainedInStaticPolyfillAware;

			script.buildTypesFromTypeRefs(result, preLinkingPhase);

			script.buildTypes(result, preLinkingPhase);

			result.astElement = script;
			script.module = result;
			(resource as N4JSResource).sneakyAddToContent(result);
//			UtilN4.takeSnapshotInGraphView("TB end (preLinking=="+preLinkingPhase+")",resource.resourceSet);
		} else {
			throw new IllegalStateException(resource.URI + " has no parse result.");
		}
	}

	/**
	 * Create types for those TypeRefs that define a type if they play the role of an AST node.
	 * <p>
	 * This has to be done up-front, because in the rest of the types builder code we do not know where such a TypeRef
	 * shows up; to avoid having to check for them at every occurrence of a TypeRef, we do this ahead of the main types
	 * builder phase.
	 */
	def private void buildTypesFromTypeRefs(Script script, TModule target, boolean preLinkingPhase) {
		if (!preLinkingPhase) {
			// important to do the following in bottom-up order!
			// The structural members of a higher-level StructuralTypeRef STR may contain another StructuralTypeRef STR';
			// when the TStructuralType of STR is created, the structural members are copied, including contained TypeRefs;
			// at that time the lower-level STR' must already have been prepared for copying!
			// Example:
			// var ~Object with {
			// ~Object with { number fieldOfField; } field;
			// } x;
			val List<Type> addTypesToTargets = new ArrayList();
			for (tr : script.eAllContents.filter(TypeRef).toList.reverseView) {
				switch tr {
					StructuralTypeRef: {
						tr.createStructuralType()
						if (tr.structuralType !== null) {
							addTypesToTargets += tr.structuralType;
						}
					}
					FunctionTypeExpression: {
						tr.createTFunction()
						if (tr.declaredType !== null) {
							addTypesToTargets += tr.declaredType;
						}
					}
					N4NamespaceDeclaration: {
						for (Type type : addTypesToTargets) {
							target.internalTypes += type;
						}
						addTypesToTargets.clear;
					}
				}
			}
			
			for (Type type : addTypesToTargets) {
				target.internalTypes += type;
			}
		}
	}

	static class RelinkIndices {
		package var namespacesIdx = 0;
		package var typesIdx = 0;
		package var functionIdx = 0;
		package var variableIdx = 0;
	}

	def private void relinkTypes(EObject container, AbstractNamespace target, boolean preLinkingPhase, RelinkIndices rlis) {
		for (n : container.eContents) {
			// relink types of n (if applicable)
			switch n {
				N4NamespaceDeclaration:
					rlis.namespacesIdx = n.relinkNamespace(target, preLinkingPhase, rlis.namespacesIdx)
				FunctionDefinition:
					rlis.functionIdx = n.relinkFunction(target, preLinkingPhase, rlis.functionIdx)
				TypeDefiningElement:
					rlis.typesIdx = n.relinkType(target, preLinkingPhase, rlis.typesIdx)
				VariableDeclarationContainer:
					rlis.variableIdx = n.relinkType(target, preLinkingPhase, rlis.variableIdx)
				TryStatement:
					rlis.variableIdx = n.relinkType(target, preLinkingPhase, rlis.variableIdx)
			}
			// relink types of child nodes
			var AbstractNamespace nextTarget;
			var RelinkIndices nextRelinkIndices;
			if (n instanceof N4NamespaceDeclaration) {
				nextTarget = n.definedNamespace;
				nextRelinkIndices = new RelinkIndices();
			} else {
				nextTarget = target;
				nextRelinkIndices = rlis;
			}
			if (nextTarget !== null) { // can be null in broken ASTs
				relinkTypes(n, nextTarget, preLinkingPhase, nextRelinkIndices)
			}
			// handle exports
			// -> nothing to do
			// (AST nodes of type ExportDeclaration are not TypeDefiningElements and
			// type model elements of type ExportDefinition are not SyntaxRelatedTElements,
			// so we do not have to relink anything between AST and types model here)
		}
	}

	def protected int relinkNamespace(N4NamespaceDeclaration n4Namespace, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4Namespace.relinkTNamespace(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	def protected dispatch int relinkType(TypeDefiningElement other, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		throw new IllegalArgumentException("unknown subclass of TypeDefiningElement: " + other?.eClass.name);
	}

	def protected dispatch int relinkType(NamespaceImportSpecifier nsImpSpec, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		// already handled up-front in N4JSNamespaceImportTypesBuilder#relinkNamespaceTypes
		return idx;
	}

	def protected dispatch int relinkFunction(MethodDeclaration n4MethodDecl, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		// methods are handled in their containing class/interface -> ignore them here
		return idx;
	}

	def protected dispatch int relinkFunction(FunctionDeclaration n4FunctionDecl, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4FunctionDecl.relinkTFunction(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	/** Function expressions are special, see {@link N4JSFunctionDefinitionTypesBuilder#createTFunction(FunctionExpression,TModule,boolean)}. */
	def protected dispatch int relinkFunction(FunctionExpression n4FunctionExpr, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		n4FunctionExpr.createTFunction(target, preLinkingPhase);
		return idx;
	}

	def protected dispatch int relinkType(N4ClassDeclaration n4Class, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4Class.relinkTClass(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	def protected dispatch int relinkType(N4ClassExpression n4Class, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		n4Class.createTClass(target, preLinkingPhase)
		// do not increment the index
		return idx
	}

	def protected dispatch int relinkType(N4InterfaceDeclaration n4Interface, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4Interface.relinkTInterface(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	def protected dispatch int relinkType(N4EnumDeclaration n4Enum, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4Enum.relinkTEnum(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	def protected dispatch int relinkType(N4TypeAliasDeclaration n4TypeAlias, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		if (n4TypeAlias.relinkTypeAlias(target, preLinkingPhase, idx)) {
			return idx + 1;
		}
		return idx;
	}

	def protected dispatch int relinkType(ObjectLiteral objectLiteral, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		objectLiteral.createObjectLiteral(target, preLinkingPhase)
		return idx;
	}

	def protected dispatch int relinkType(VariableDeclarationContainer n4VarDeclContainer, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		return n4VarDeclContainer.relinkVariableTypes(target, preLinkingPhase, idx)
	}

	def protected dispatch int relinkType(TryStatement tryStmnt, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		return tryStmnt.relinkVariableTypes(target, preLinkingPhase, idx)
	}

	def protected dispatch int relinkType(NamespaceExportSpecifier n4NamespaceExportSpecifier, AbstractNamespace target, boolean preLinkingPhase, int idx) {
		// namespace export specifiers are handled as part of their containing ExportDeclaration -> ignore them here
		return idx;
	}


	def private void buildTypes(EObject container, AbstractNamespace target, boolean preLinkingPhase) {
		for (n : container.eContents) {
			// build type for n (if applicable)
			switch n {
				N4NamespaceDeclaration:
					n.createNamespace(target, preLinkingPhase)
				FunctionDefinition:
					n.createFunction(target, preLinkingPhase)
				TypeDefiningElement:
					n.createType(target, preLinkingPhase)
				VariableDeclarationContainer: // VariableStatement and ForStatement
					n.createType(target, preLinkingPhase)
				TryStatement:
					n.createType(target, preLinkingPhase)
			}
			// build types for child nodes
			val nextTarget = if (n instanceof N4NamespaceDeclaration) n.definedNamespace else target;
			if (nextTarget !== null) { // can be null in broken ASTs
				buildTypes(n, nextTarget, preLinkingPhase);
			}
			// handle exports
			if (n instanceof ExportDeclaration) {
				n.createType(target, preLinkingPhase);
			} else if (n instanceof ExportableElement) {
				if (!n.isDeclaredExported && n.isExportedByNamespace) {
					val isDtsExceptionCase = ResourceType.getResourceType(n) === ResourceType.DTS
						&& n instanceof ModifiableElement
						&& (n as ModifiableElement).declaredModifiers.contains(N4Modifier.PRIVATE);
					if (!isDtsExceptionCase) {
						n.createExportDefinitionForDirectlyExportedElement(target, preLinkingPhase);
					}
				}
			}
		}
	}

	def protected void createNamespace(N4NamespaceDeclaration n4Namespace, AbstractNamespace target, boolean preLinkingPhase) {
		n4Namespace.createTNamespace(target, preLinkingPhase)
	}

	def protected dispatch void createFunction(MethodDeclaration n4MethodDecl, AbstractNamespace target, boolean preLinkingPhase) {
		// methods are handled in their containing class/interface -> ignore them here
	}

	def protected dispatch void createFunction(FunctionDeclaration n4FunctionDecl, AbstractNamespace target, boolean preLinkingPhase) {
		n4FunctionDecl.createTFunction(target, preLinkingPhase)
	}

	/** Function expressions are special, see {@link N4JSFunctionDefinitionTypesBuilder#createTFunction(FunctionExpression,TModule,boolean)}. */
	def protected dispatch void createFunction(FunctionExpression n4FunctionExpr, AbstractNamespace target, boolean preLinkingPhase) {
		n4FunctionExpr.createTFunction(target, preLinkingPhase)
	}

	def protected dispatch void createType(TypeDefiningElement other, AbstractNamespace target, boolean preLinkingPhase) {
		throw new IllegalArgumentException("unknown subclass of TypeDefiningElement: " + other?.eClass.name);
	}

	def protected dispatch void createType(NamespaceImportSpecifier nsImpSpec, AbstractNamespace target, boolean preLinkingPhase) {
		// already handled up-front in #buildNamespacesTypesFromModuleImports()
	}

	def protected dispatch void createType(N4ClassDeclaration n4Class, AbstractNamespace target, boolean preLinkingPhase) {
		n4Class.createTClass(target, preLinkingPhase)
	}

	def protected dispatch void createType(N4ClassExpression n4Class, AbstractNamespace target, boolean preLinkingPhase) {
		n4Class.createTClass(target, preLinkingPhase)
	}

	def protected dispatch void createType(N4InterfaceDeclaration n4Interface, AbstractNamespace target, boolean preLinkingPhase) {
		n4Interface.createTInterface(target, preLinkingPhase)
	}

	def protected dispatch void createType(N4EnumDeclaration n4Enum, AbstractNamespace target, boolean preLinkingPhase) {
		n4Enum.createTEnum(target, preLinkingPhase)
	}

	def protected dispatch void createType(N4TypeAliasDeclaration n4TypeAliasDecl, AbstractNamespace target, boolean preLinkingPhase) {
		n4TypeAliasDecl.createTypeAlias(target, preLinkingPhase)
	}

	def protected dispatch void createType(ObjectLiteral objectLiteral, AbstractNamespace target, boolean preLinkingPhase) {
		objectLiteral.createObjectLiteral(target, preLinkingPhase)
	}

	def protected dispatch void createType(VariableDeclarationContainer n4VarDeclContainer, AbstractNamespace target, boolean preLinkingPhase) {
		n4VarDeclContainer.createVariableTypes(target, preLinkingPhase)
	}

	def protected dispatch void createType(TryStatement tryStmnt, AbstractNamespace target, boolean preLinkingPhase) {
		tryStmnt.createVariableTypes(target, preLinkingPhase)
	}

	def protected dispatch void createType(ExportDeclaration n4ExportDeclaration, AbstractNamespace target, boolean preLinkingPhase) {
		n4ExportDeclaration.createExportDefinition(target, preLinkingPhase)
	}

	def protected dispatch void createType(NamespaceExportSpecifier n4NamespaceExportSpecifier, AbstractNamespace target, boolean preLinkingPhase) {
		// namespace export specifiers are handled as part of their containing ExportDeclaration -> ignore them here
	}
}
