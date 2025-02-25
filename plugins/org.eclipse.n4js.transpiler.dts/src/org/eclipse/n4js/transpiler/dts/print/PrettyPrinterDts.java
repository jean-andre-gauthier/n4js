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
package org.eclipse.n4js.transpiler.dts.print;

import static org.eclipse.n4js.transpiler.TranspilerBuilderBlocks._N4FieldDecl;
import static org.eclipse.n4js.transpiler.utils.TranspilerUtils.isLegalIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.n4js.N4JSGlobals;
import org.eclipse.n4js.N4JSLanguageConstants;
import org.eclipse.n4js.n4JS.Annotation;
import org.eclipse.n4js.n4JS.Block;
import org.eclipse.n4js.n4JS.DefaultImportSpecifier;
import org.eclipse.n4js.n4JS.ExportDeclaration;
import org.eclipse.n4js.n4JS.ExportableElement;
import org.eclipse.n4js.n4JS.Expression;
import org.eclipse.n4js.n4JS.FormalParameter;
import org.eclipse.n4js.n4JS.FunctionDeclaration;
import org.eclipse.n4js.n4JS.FunctionDefinition;
import org.eclipse.n4js.n4JS.ImportDeclaration;
import org.eclipse.n4js.n4JS.ImportSpecifier;
import org.eclipse.n4js.n4JS.LiteralOrComputedPropertyName;
import org.eclipse.n4js.n4JS.N4ClassDeclaration;
import org.eclipse.n4js.n4JS.N4ClassifierDeclaration;
import org.eclipse.n4js.n4JS.N4EnumDeclaration;
import org.eclipse.n4js.n4JS.N4EnumLiteral;
import org.eclipse.n4js.n4JS.N4FieldAccessor;
import org.eclipse.n4js.n4JS.N4FieldDeclaration;
import org.eclipse.n4js.n4JS.N4GetterDeclaration;
import org.eclipse.n4js.n4JS.N4InterfaceDeclaration;
import org.eclipse.n4js.n4JS.N4MemberDeclaration;
import org.eclipse.n4js.n4JS.N4MethodDeclaration;
import org.eclipse.n4js.n4JS.N4Modifier;
import org.eclipse.n4js.n4JS.N4NamespaceDeclaration;
import org.eclipse.n4js.n4JS.N4SetterDeclaration;
import org.eclipse.n4js.n4JS.N4TypeAliasDeclaration;
import org.eclipse.n4js.n4JS.N4TypeVariable;
import org.eclipse.n4js.n4JS.NamedExportSpecifier;
import org.eclipse.n4js.n4JS.NamedImportSpecifier;
import org.eclipse.n4js.n4JS.NamespaceElement;
import org.eclipse.n4js.n4JS.NamespaceImportSpecifier;
import org.eclipse.n4js.n4JS.NumericLiteral;
import org.eclipse.n4js.n4JS.PropertyNameKind;
import org.eclipse.n4js.n4JS.PropertyNameOwner;
import org.eclipse.n4js.n4JS.Script;
import org.eclipse.n4js.n4JS.Statement;
import org.eclipse.n4js.n4JS.StringLiteral;
import org.eclipse.n4js.n4JS.TypeDefiningElement;
import org.eclipse.n4js.n4JS.TypeProvidingElement;
import org.eclipse.n4js.n4JS.TypeReferenceNode;
import org.eclipse.n4js.n4JS.VariableDeclaration;
import org.eclipse.n4js.n4JS.VariableDeclarationOrBinding;
import org.eclipse.n4js.n4JS.VariableStatement;
import org.eclipse.n4js.n4JS.VariableStatementKeyword;
import org.eclipse.n4js.n4JS.util.N4JSSwitch;
import org.eclipse.n4js.tooling.N4JSDocumentationProvider;
import org.eclipse.n4js.transpiler.TranspilerState;
import org.eclipse.n4js.transpiler.im.Script_IM;
import org.eclipse.n4js.transpiler.im.TypeReferenceNode_IM;
import org.eclipse.n4js.transpiler.print.LineColTrackingAppendable;
import org.eclipse.n4js.ts.typeRefs.ParameterizedTypeRef;
import org.eclipse.n4js.ts.typeRefs.TypeRef;
import org.eclipse.n4js.ts.types.TVariable;
import org.eclipse.n4js.ts.types.Type;
import org.eclipse.n4js.ts.types.TypeAccessModifier;
import org.eclipse.n4js.typesystem.utils.RuleEnvironmentExtensions;
import org.eclipse.n4js.utils.N4JSLanguageUtils;
import org.eclipse.n4js.utils.N4JSLanguageUtils.EnumKind;
import org.eclipse.n4js.utils.parser.conversion.ValueConverterUtils;
import org.eclipse.xtext.EcoreUtil2;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

/**
 * Traverses an intermediate model and serializes it to a {@link LineColTrackingAppendable}. Client code should only use
 * the static method {@link #append(LineColTrackingAppendable, TranspilerState, Optional, N4JSDocumentationProvider)}.
 */
public final class PrettyPrinterDts extends N4JSSwitch<Boolean> {

	/**
	 * Appends the given transpiler state's intermediate model to the given {@link LineColTrackingAppendable}.
	 */
	public static void append(LineColTrackingAppendable out, TranspilerState state, Optional<String> optPreamble,
			N4JSDocumentationProvider documentationProvider) {
		final PrettyPrinterDts theSwitch = new PrettyPrinterDts(out, state, optPreamble, documentationProvider);
		theSwitch.doSwitch(state.im);
	}

	/** Value to be returned from case-methods to indicate that processing is completed and should not be continued. */
	private static final Boolean DONE = Boolean.TRUE;

	private static final Set<N4Modifier> ACCESSIBILITY_MODIFIERS = Sets.newHashSet(
			N4Modifier.PRIVATE,
			N4Modifier.PROTECTED,
			N4Modifier.PROJECT,
			N4Modifier.PUBLIC);

	private final LineColTrackingAppendable out;
	private final Optional<String> optPreamble;
	private final TranspilerState state;
	private final N4JSDocumentationProvider documentationProvider;

	private PrettyPrinterDts(LineColTrackingAppendable out, TranspilerState state, Optional<String> optPreamble,
			N4JSDocumentationProvider documentationProvider) {
		this.out = out;
		this.optPreamble = optPreamble;
		this.state = state;
		this.documentationProvider = documentationProvider;
	}

	@Override
	protected Boolean doSwitch(EClass eClass, EObject eObject) {
		// here we can check for entities of IM.xcore that do not have a super-class in n4js.xcore
		if (eObject instanceof TypeReferenceNode<?>) {
			processTypeRefNode((TypeReferenceNode<?>) eObject);
			return DONE;
		}
		return super.doSwitch(eClass, eObject);
	}

	@Override
	public Boolean defaultCase(EObject object) {
		throw new IllegalStateException(
				"PrettyPrinterSwitch missing a case for objects of type " + object.eClass().getName());
	}

	@Override
	public Boolean caseScript(Script original) {
		final Script_IM original_IM = (Script_IM) original;
		processHashbang(original_IM.getHashbang());
		processPreamble();
		processAnnotations(original_IM.getAnnotations());

		write(N4JSGlobals.IMPORT_N4JSGLOBALS);
		newLine();

		process(original_IM.getScriptElements(), () -> {
			newLine();
		});

		return DONE;
	}

	@Override
	public Boolean caseExportDeclaration(ExportDeclaration original) {
		if (original.getModule() != null) {
			throwUnsupportedSyntax();
		}
		writeJsdoc(original);
		processAnnotations(original.getAnnotations());
		write("export ");
		final List<NamedExportSpecifier> namedExports = original.getNamedExports();
		if (!namedExports.isEmpty()) {
			write("{ ");
			process(namedExports, ", ");
			write(" }");
		} else {
			if (original.isDefaultExport()) {
				write("default ");
			}
			final ExportableElement exportedElement = original.getExportedElement();
			if (exportedElement != null) {
				process(exportedElement);
			} else {
				final Expression exportedExpression = original.getDefaultExportedExpression();
				if (exportedExpression != null && original.isDefaultExport()) {
					process(exportedExpression);
					write(';');
				}
			}
		}
		return DONE;
	}

	@Override
	public Boolean caseNamedExportSpecifier(NamedExportSpecifier original) {
		process(original.getExportedElement());
		final String alias = original.getAlias();
		if (alias != null) {
			write(" as ");
			write(alias);
		}
		return DONE;
	}

	@Override
	public Boolean caseImportDeclaration(ImportDeclaration original) {
		String moduleSpecifier = original.getModuleSpecifierAsText() != null
				? original.getModuleSpecifierAsText().replace("%3A", ":") // see ModuleSpecifierValueConverter
				: original.getModule().getQualifiedName();

		processAnnotations(original.getAnnotations());
		write("import ");
		// 1) import specifiers
		List<ImportSpecifier> importSpecifiers = new ArrayList<>(original.getImportSpecifiers());
		if (!importSpecifiers.isEmpty() && importSpecifiers.get(0) instanceof DefaultImportSpecifier) {
			process(importSpecifiers.remove(0));
			if (!importSpecifiers.isEmpty()) {
				write(", ");
			}
		}
		if (!importSpecifiers.isEmpty()) {
			final boolean isNamespaceImport = importSpecifiers.get(0) instanceof NamespaceImportSpecifier;
			if (isNamespaceImport) {
				process(importSpecifiers.get(0)); // syntax does not allow more than one namespace import
			} else {
				write('{');
				process(importSpecifiers, ", ");
				write('}');
			}
		}
		// 2) "from"
		if (original.isImportFrom()) {
			write(" from ");
		}
		// 3) module specifier
		write(quote(moduleSpecifier));
		// 4) empty line after block of imports
		boolean isLastImport = !(EcoreUtil2.getNextSibling(original) instanceof ImportDeclaration);
		if (isLastImport) {
			newLine();
		}
		return DONE;
	}

	/** Also handles DefaultImportSpecifier (which is a subclass of NamedImportSpecifier). */
	@Override
	public Boolean caseNamedImportSpecifier(NamedImportSpecifier original) {
		write(original.getImportedElementAsText());
		final String alias = original.getAlias();
		if (alias != null && !original.isDefaultImport()) {
			write(" as ");
			write(alias);
		}
		return DONE;
	}

	@Override
	public Boolean caseNamespaceImportSpecifier(NamespaceImportSpecifier original) {
		write("* as ");
		write(original.getAlias());
		return DONE;
	}

	@Override
	public Boolean caseN4NamespaceDeclaration(N4NamespaceDeclaration original) {
		handleDeclareAndNamespaceElements(original);

		write("namespace ");
		write(original.getName());
		write(" ");

		processBlockLike(original.getOwnedElementsRaw(), '{', null, null, '}', true);

		return DONE;
	}

	private void handleDeclareAndNamespaceElements(EObject original) {
		if (((NamespaceElement) original).isInNamespace()) {
			Type definedType = state.info.getOriginalDefinedType((TypeDefiningElement) original);
			if (definedType != null && definedType.getTypeAccessModifier() != TypeAccessModifier.PRIVATE) {
				write("export ");
			}
		} else {
			if (!((ExportableElement) original).isDirectlyExported()) {
				writeJsdoc(original); // already written in #caseExportDeclaration()
				write("declare ");
			}
		}

	}

	@Override
	public Boolean caseN4ClassDeclaration(N4ClassDeclaration original) {
		handleDeclareAndNamespaceElements(original);

		processTopLevelElementModifiers(original.getDeclaredModifiers());
		write("class ");
		write(original.getName());
		if (!original.getTypeVars().isEmpty()) {
			write("<");
			for (int i = 0; i < original.getTypeVars().size(); i++) {
				if (i > 0) {
					write(", ");
				}
				N4TypeVariable typeVar = original.getTypeVars().get(i);
				process(typeVar);
			}
			write(">");
		}
		write(' ');

		final TypeReferenceNode<ParameterizedTypeRef> superClassRef = original.getSuperClassRef();
		final Expression superClassExpression = original.getSuperClassExpression();
		if (superClassRef != null) {
			write("extends ");
			process(superClassRef);
			write(' ');
		} else if (superClassExpression != null) {
			throw new IllegalStateException("in " + PrettyPrinterDts.class.getSimpleName()
					+ " property 'superClassExpression' of class declarations is expected to be 'null', but was: "
					+ superClassExpression);
		}

		if (!original.getImplementedInterfaceRefs().isEmpty()) {
			write("implements ");
			for (int i = 0; i < original.getImplementedInterfaceRefs().size(); i++) {
				if (i > 0) {
					write(", ");
				}
				TypeReferenceNode<ParameterizedTypeRef> superInterfRef = original.getImplementedInterfaceRefs().get(i);
				process(superInterfRef);
			}
			write(' ');
		}

		// Workaround since TypeScript classes do not support const fields
		createNamespaceForConstsOfClass(original);

		return DONE;
	}

	private void createNamespaceForConstsOfClass(N4ClassDeclaration original) {
		List<N4MemberDeclaration> constMembers = new ArrayList<>();
		List<N4MemberDeclaration> nonConstMembers = new ArrayList<>();
		for (N4MemberDeclaration member : original.getOwnedMembersRaw()) {
			if (member instanceof N4FieldDeclaration && ((N4FieldDeclaration) member).isConst()) {
				if (N4JSLanguageConstants.RESERVED_WORDS.contains(member.getName())) {
					// reserved words are allowed as name of a member but not of variables,
					// so we can't rewrite the member in this case to a namespace
					continue;
				}
				constMembers.add(member);
			} else {
				nonConstMembers.add(member);
			}
		}
		processBlockLike(nonConstMembers, '{', null, null, '}');

		if (!constMembers.isEmpty()) {
			newLine();
			if (!original.isDirectlyExported()) {
				write("declare ");
			} else {
				write("export ");
			}
			write("namespace ");
			write(original.getName());
			write(' ');

			processBlockLike(constMembers, '{', null, null, '}', true, this::processConstFieldsOfClass);
		}
	}

	private void processConstFieldsOfClass(EObject eObject) {
		N4FieldDeclaration field = (N4FieldDeclaration) eObject;
		N4ClassDeclaration parent = (N4ClassDeclaration) field.getOwner();
		writeJsdoc(field);
		processAnnotations(field.getAnnotations());
		writeIf("export ", parent.isDirectlyExported());
		write("const ");
		processPropertyName(field);
		processDeclaredTypeRef(field);
		write(';');
	}

	@Override
	public Boolean caseN4InterfaceDeclaration(N4InterfaceDeclaration original) {
		handleDeclareAndNamespaceElements(original);

		processTopLevelElementModifiers(original.getDeclaredModifiers());
		write("interface ");
		write(original.getName());
		if (!original.getTypeVars().isEmpty()) {
			write("<");
			for (int i = 0; i < original.getTypeVars().size(); i++) {
				if (i > 0) {
					write(", ");
				}
				N4TypeVariable typeVar = original.getTypeVars().get(i);
				process(typeVar);
			}
			write(">");
		}
		write(' ');

		if (!original.getSuperInterfaceRefs().isEmpty()) {
			write("extends ");
			for (int i = 0; i < original.getSuperInterfaceRefs().size(); i++) {
				if (i > 0) {
					write(", ");
				}
				TypeReferenceNode<ParameterizedTypeRef> superInterfRef = original.getSuperInterfaceRefs().get(i);
				process(superInterfRef);
			}
			write(' ');
		}

		// Workaround since TypeScript interfaces do not support static members
		createNamespaceForStaticsOfInterface(original);

		return DONE;
	}

	private void createNamespaceForStaticsOfInterface(N4ClassifierDeclaration classifier) {
		List<N4MemberDeclaration> staticMembers = new ArrayList<>();
		List<N4MemberDeclaration> nonStaticMembers = new ArrayList<>();
		Map<String, N4FieldAccessor> getterSetterNames = new HashMap<>();
		for (N4MemberDeclaration member : new ArrayList<>(classifier.getOwnedMembersRaw())) {
			if (member.isStatic()) {
				if (N4JSLanguageConstants.RESERVED_WORDS.contains(member.getName())) {
					// reserved words are allowed as name of a member but not of variables,
					// so we can't rewrite the member in this case to a namespace
					continue;
				}
				if (member instanceof N4FieldAccessor) {
					if (!getterSetterNames.containsKey(member.getName())) {
						staticMembers.add(member);
					} else {
						N4FieldAccessor accessor = getterSetterNames.get(member.getName());
						staticMembers.remove(accessor);
						N4FieldDeclaration fieldForGSPair = _N4FieldDecl(true, accessor.getName(), null);
						fieldForGSPair.setOwner(classifier);
						fieldForGSPair.setDeclaredTypeRefNode(accessor.getDeclaredTypeRefNode());
						staticMembers.add(fieldForGSPair);
					}
					getterSetterNames.put(member.getName(), (N4FieldAccessor) member);
				} else {
					staticMembers.add(member);
				}
			} else {
				nonStaticMembers.add(member);
			}
		}
		processBlockLike(nonStaticMembers, '{', null, null, '}');

		if (!staticMembers.isEmpty()) {
			newLine();
			if (!classifier.isDirectlyExported()) {
				write("declare ");
			} else {
				write("export ");
			}
			write("namespace ");
			write(classifier.getName());
			write(' ');

			processBlockLike(staticMembers, '{', null, null, '}', true, this::processStaticsOfInterface);
		}
	}

	private void processStaticsOfInterface(EObject eObject) {
		N4MemberDeclaration member = (N4MemberDeclaration) eObject;
		N4InterfaceDeclaration parent = (N4InterfaceDeclaration) member.getOwner();
		writeJsdoc(member);
		processAnnotations(member.getAnnotations());
		writeIf("export ", parent.isDirectlyExported());
		if (member instanceof N4FieldDeclaration) {
			N4FieldDeclaration field = (N4FieldDeclaration) member;
			if (field.isConst()) {
				write("const ");
			} else if (field.isStatic()) {
				write("let ");
			}
			processPropertyName(field);
			processDeclaredTypeRef(field);
			write(';');

		} else if (member instanceof N4FieldAccessor) {
			N4FieldAccessor accessor = (N4FieldAccessor) member;
			write("let ");
			processPropertyName(accessor);
			processDeclaredTypeRef(accessor);
			String kind = member instanceof N4GetterDeclaration ? "getter" : "setter";
			write(';');
			write(" // Attention: Use as " + kind + " only!");

		} else if (member instanceof N4MethodDeclaration) {
			N4MethodDeclaration method = (N4MethodDeclaration) member;
			write("function ");
			// processMemberModifiers(original);
			if (method.isAsync()) {
				// in TypeScript, the 'async' keyword is not allowed in .d.ts files
				// write("async ");
			}
			if (method.isGenerator()) {
				// in TypeScript, the '*' is not allowed in .d.ts files
				// write("* ");
			}
			processPropertyName(method);
			if (!method.getTypeVars().isEmpty()) {
				processTypeParams(method.getTypeVars());
			}
			write('(');
			process(method.getFpars(), ", ");
			write(")");
			processReturnTypeRef(method);
			// process(original.getBody());
			write(';');
		}
	}

	@Override
	public Boolean caseN4EnumDeclaration(N4EnumDeclaration original) {
		handleDeclareAndNamespaceElements(original);

		EnumKind enumKind = N4JSLanguageUtils.getEnumKind(original);
		boolean literalBased = enumKind != EnumKind.Normal;
		writeIf("const ", literalBased);
		processTopLevelElementModifiers(original.getDeclaredModifiers());
		write("enum ");
		write(original.getName());
		write(' ');

		processBlockLike(original.getLiterals(), '{', ",", null, '}');
		newLine();

		if (!literalBased) {
			// Workaround since TypeScript enums do not support static methods

			if (!original.isDirectlyExported()) {
				write("declare ");
			} else {
				write("export ");
			}
			write("namespace ");
			write(original.getName());
			write(" {");
			out.indent();
			newLine();

			write("export const literals: Array<" + original.getName() + ">;");
			newLine();
			write("export function findLiteralByName(name: string): " + original.getName() + ";");
			newLine();
			write("export function findLiteralByValue (value: string): " + original.getName() + ";");

			out.undent();
			newLine();
			write('}');
			newLine();
		}

		return DONE;
	}

	@Override
	public Boolean caseN4EnumLiteral(N4EnumLiteral literal) {
		writeJsdoc(literal);
		write(literal.getName());
		Expression expression = literal.getValueExpression();
		if (expression != null) {
			if (expression instanceof StringLiteral) {
				String string = ((StringLiteral) expression).getValueAsString();
				writeIf(" = \"" + string + "\"", string != null);
			}
			if (expression instanceof NumericLiteral) {
				String string = ((NumericLiteral) expression).getValueAsString();
				writeIf(" = " + string, string != null);
			}
		}
		return DONE;
	}

	@Override
	public Boolean caseN4TypeAliasDeclaration(N4TypeAliasDeclaration alias) {
		handleDeclareAndNamespaceElements(alias);

		write("type ");
		write(alias.getName());
		if (!alias.getTypeVars().isEmpty()) {
			write("<");
			for (int i = 0; i < alias.getTypeVars().size(); i++) {
				if (i > 0) {
					write(", ");
				}
				N4TypeVariable typeVar = alias.getTypeVars().get(i);
				process(typeVar);
			}
			write(">");
		}
		write(" = ");
		process(alias.getDeclaredTypeRefNode());
		write(";");
		return DONE;
	}

	@Override
	public Boolean caseN4FieldDeclaration(N4FieldDeclaration original) {
		writeJsdoc(original);
		processAnnotations(original.getAnnotations());
		processMemberModifiers(original);
		processPropertyName(original);
		if (original.isDeclaredOptional()) {
			write('?');
		}
		processDeclaredTypeRef(original);
		write(";");
		return DONE;
	}

	@Override
	public Boolean caseN4GetterDeclaration(N4GetterDeclaration original) {
		boolean isOptional = original.isOptional();
		if (isOptional) {
			// omit optional getters since these do not exist in TypeScript
			out.startCommentingOut();
			write("// ");
		}
		writeJsdoc(original);
		processAnnotations(original.getAnnotations());
		processMemberModifiers(original);
		write("get ");
		processPropertyName(original);
		if (isOptional) {
			write("?");
		}
		write("()");
		processDeclaredTypeRef(original);
		// process(original.getBody());
		write(";");
		if (isOptional) {
			write(" // optional getter omitted");
			out.endCommentingOut();
		}
		return DONE;
	}

	@Override
	public Boolean caseN4SetterDeclaration(N4SetterDeclaration original) {
		boolean isOptional = original.isOptional();
		if (isOptional) {
			// omit optional setters since these do not exist in TypeScript
			out.startCommentingOut();
			write("// ");
		}
		writeJsdoc(original);
		processAnnotations(original.getAnnotations());
		processMemberModifiers(original);
		write("set ");
		processPropertyName(original);
		if (isOptional) {
			write("?");
		}
		write('(');
		process(original.getFpar());
		write(")");
		// process(original.getBody());
		write(";");
		if (isOptional) {
			write(" // optional setter omitted");
			out.endCommentingOut();
		}
		return DONE;
	}

	@Override
	public Boolean caseN4MethodDeclaration(N4MethodDeclaration original) {
		writeJsdoc(original);
		processAnnotations(original.getAnnotations());
		processMemberModifiers(original);
		if (original.isAsync()) {
			// in TypeScript, the 'async' keyword is not allowed in .d.ts files
			// write("async ");
		}
		if (original.isGenerator()) {
			// in TypeScript, the '*' is not allowed in .d.ts files
			// write("* ");
		}
		if (original.getDeclaredName() != null) {
			processPropertyName(original);
		} else {
			// deal with e.g.: class C { (i: number); }
		}
		// methods can be optional in TypeScript
		// if (original.isDeclaredOptional()) {
		// write('?');
		// }
		if (!original.getTypeVars().isEmpty()) {
			processTypeParams(original.getTypeVars());
		}
		write('(');
		process(original.getFpars(), ", ");
		write(")");
		processReturnTypeRef(original);
		// process(original.getBody());
		write(';');
		return DONE;
	}

	@Override
	public Boolean caseLiteralOrComputedPropertyName(LiteralOrComputedPropertyName original) {
		processPropertyName(original);
		return DONE;
	}

	@Override
	public Boolean caseFunctionDeclaration(FunctionDeclaration original) {
		handleDeclareAndNamespaceElements(original);

		processAnnotations(original.getAnnotations());
		processTopLevelElementModifiers(original.getDeclaredModifiers());
		if (original.isAsync()) {
			// in TypeScript, the 'async' keyword is not allowed in .d.ts files
			// write("async ");
		}
		write("function ");
		if (original.isGenerator()) {
			// in TypeScript, the '*' is not allowed in .d.ts files
			// write("* ");
		}
		write(original.getName());
		if (!original.getTypeVars().isEmpty()) {
			processTypeParams(original.getTypeVars());
		}
		write('(');
		process(original.getFpars(), ", ");
		write(")");
		processReturnTypeRef(original);
		// process(original.getBody());
		write(';');
		return DONE;
	}

	@Override
	public Boolean caseFormalParameter(FormalParameter original) {
		processAnnotations(original.getAnnotations(), false);
		if (original.isVariadic()) {
			write("...");
		}
		write(original.getName());
		if (original.isHasInitializerAssignment() && !original.isVariadic()) {
			write('?');
		}

		write(": ");
		TypeReferenceNode<TypeRef> declaredTypeRefNode = original.getDeclaredTypeRefNode();
		TypeRef declaredTypeRef = declaredTypeRefNode != null
				? state.info.getOriginalProcessedTypeRef(declaredTypeRefNode)
				: null;
		if (declaredTypeRef != null) {
			boolean requiresParens = original.isVariadic()
					&& !(declaredTypeRef instanceof ParameterizedTypeRef);
			if (requiresParens) {
				write('(');
			}
			processTypeRefNode(declaredTypeRefNode);
			if (requiresParens) {
				write(')');
			}
		} else {
			write("any");
		}
		if (original.isVariadic()) {
			// in TypeScript, the type of a rest parameter must explicitly be declared as an array
			write("[]");
		} else if (original.getInitializer() != null) {
			// not allowed in .d.ts; covered by '?' above
			// write("=");
			// process(original.getInitializer());
		}
		return DONE;
	}

	@Override
	public Boolean caseBlock(Block original) {
		processBlock(original.getStatements());
		return DONE;
	}

	@Override
	public Boolean caseVariableStatement(VariableStatement original) {
		if (original.isExportedAsDefault()) {
			EList<VariableDeclarationOrBinding> declsOrBindings = original.getVarDeclsOrBindings();
			// the default export does only support a single element. Hence, only the first entry is used.
			if (!declsOrBindings.isEmpty()) {
				VariableDeclarationOrBinding declOrBinding = declsOrBindings.get(0);
				EList<VariableDeclaration> declarations = declOrBinding.getAllVariableDeclarations();
				if (!declarations.isEmpty()) {
					VariableDeclaration declaration = declarations.get(0);
					write(declaration.getName());
				}
			}
			write(';');
			newLine();

			write("declare ");

		} else if (original.isDirectlyExported()) {
			if (((NamespaceElement) original).isInNamespace()) {
				if (!original.getVarDecl().isEmpty()) {

					VariableDeclaration evd = original.getVarDecl().get(0);
					TVariable tVariable = state.info.getOriginalDefinedVariable(evd);
					if (tVariable != null && tVariable.getTypeAccessModifier() != TypeAccessModifier.PRIVATE) {
						write("export ");
					}
				}
			}

			processTopLevelElementModifiers(original.getDeclaredModifiers());

		} else {
			// not exported
			if (!((NamespaceElement) original).isInNamespace()) {
				write("declare ");
			}
		}

		write(keyword(original.getVarStmtKeyword()));
		write(' ');
		process(original.getVarDeclsOrBindings(), ", ");
		// alternative to previous line would be:
		// out.indent();
		// process(original.getVarDeclsOrBindings(), () -> {
		// write(',');
		// newLine();
		// });
		// out.undent();
		write(';');
		return DONE;
	}

	private String keyword(VariableStatementKeyword varStmtKeyword) {
		switch (varStmtKeyword) {
		case LET:
			return "let";
		case CONST:
			return "const";
		case VAR:
			return "var";
		default:
			throw new UnsupportedOperationException("unsupported variable statement keyword");
		}
	}

	@Override
	public Boolean caseVariableDeclaration(VariableDeclaration original) {
		processAnnotations(original.getAnnotations());
		write(original.getName());
		processDeclaredTypeRef(original);
		// if (original.getExpression() != null) {
		// write(" = ");
		// process(original.getExpression());
		// }
		return DONE;
	}

	@Override
	public Boolean caseN4TypeVariable(N4TypeVariable typeVar) {
		write(typeVar.getName());
		TypeReferenceNode<TypeRef> ub = typeVar.getDeclaredUpperBoundNode();
		if (ub != null) {
			Type n4EnumType = RuleEnvironmentExtensions.n4EnumType(state.G);
			TypeRef ubTypeRef = state.info.getOriginalProcessedTypeRef(ub);
			if (ubTypeRef.getDeclaredType() == n4EnumType) {
				// TODO IDE-3526 reconsider handling of N4Enum as upper bound
				return DONE;
			}
			write(" extends ");
			processTypeRefNode(ub);
		}
		if (typeVar.isOptional()) {
			write(" = ");
			TypeReferenceNode<TypeRef> defArg = typeVar.getDeclaredDefaultArgumentNode();
			if (defArg != null) {
				processTypeRefNode(defArg);
			} else {
				write("any");
			}
		}
		return DONE;
	}

	// ###############################################################################################################
	// UTILITY AND CONVENIENCE METHODS

	private void writeJsdoc(EObject original) {
		EObject originalASTNode = state.tracer.getOriginalASTNode(original);
		if (originalASTNode != null) {
			String documentation = documentationProvider.findComment(originalASTNode, false);
			if (documentation != null) {
				documentation = documentation.replaceAll("\n\t+", "\n");
				documentation = documentation.replaceAll("\n\\s+\\*", "\n \\*");
				write(documentation);
				newLine();
			}
		}
	}

	/* package */ void write(char c) {
		try {
			out.append(c);
		} catch (IOException e) {
			throw new WrappedException(e);
		}
	}

	/* package */ void write(CharSequence csq) {
		try {
			out.append(csq);
		} catch (IOException e) {
			throw new WrappedException(e);
		}
	}

	private void writeIf(CharSequence csq, boolean condition) {
		if (condition) {
			write(csq);
		}
	}

	private void writeQuoted(String csq) {
		write(quote(csq));
	}

	private void writeQuotedIfNonIdentifier(String csq) {
		if (!isLegalIdentifier(csq)) {
			writeQuoted(csq);
		} else {
			write(csq);
		}
	}

	private void newLine() {
		try {
			out.newLine();
		} catch (IOException e) {
			throw new WrappedException(e);
		}
	}

	private void process(Iterable<? extends EObject> elemsInIM, String separator) {
		final Iterator<? extends EObject> iter = elemsInIM.iterator();
		while (iter.hasNext()) {
			doSwitch(iter.next());
			if (separator != null && iter.hasNext()) {
				write(separator);
			}
		}
	}

	private void process(Iterable<? extends EObject> elemsInIM, Runnable separator) {
		process(elemsInIM, this::process, separator);
	}

	private void process(Iterable<? extends EObject> elemsInIM, Consumer<EObject> process, Runnable separator) {
		final Iterator<? extends EObject> iter = elemsInIM.iterator();
		while (iter.hasNext()) {
			process.accept(iter.next());
			if (separator != null && iter.hasNext()) {
				separator.run();
			}
		}
	}

	@SuppressWarnings("unused")
	private void processIfNonNull(EObject elemInIM) {
		if (elemInIM != null) {
			doSwitch(elemInIM);
		}
	}

	private void process(EObject elemInIM) {
		if (elemInIM == null) {
			throw new IllegalArgumentException("element to process may not be null");
		}
		doSwitch(elemInIM);
	}

	private void processHashbang(String hashbang) {
		if (!Strings.isNullOrEmpty(hashbang)) {
			write(prependHashbang(hashbang));
			newLine();
		}
	}

	private void processPreamble() {
		if (optPreamble.isPresent()) {
			write(optPreamble.get()); // #append(CharSequence) will convert '\n' to correct line separator
			newLine();
		}
	}

	private void processAnnotations(Iterable<? extends Annotation> annotations) {
		processAnnotations(annotations, true);
	}

	private void processAnnotations(@SuppressWarnings("unused") Iterable<? extends Annotation> annotations,
			@SuppressWarnings("unused") boolean multiLine) {
		// throw exception if
		// if (annotations.iterator().hasNext()) {
		// throw new IllegalStateException("Annotations left in the code: " + Joiner.on(",").join(annotations));
		// }
	}

	private void processPropertyName(PropertyNameOwner owner) {
		final LiteralOrComputedPropertyName name = owner.getDeclaredName();
		processPropertyName(name);
	}

	private void processPropertyName(LiteralOrComputedPropertyName name) {
		final PropertyNameKind kind = name.getKind();
		if (kind == PropertyNameKind.COMPUTED) {
			// computed property names:
			// (we cannot simply emit the expression enclosed in '[' and ']', as we do in PrettyPrinterEcmaScript,
			// because expressions are trimmed in the .d.ts case and not supported by this class; therefore we emit the
			// computed name)
			String computedName = name.getComputedName();
			if (computedName != null) {
				if (name.isComputedSymbol() && computedName.startsWith(N4JSLanguageUtils.SYMBOL_IDENTIFIER_PREFIX)) {
					write("[Symbol.");
					write(computedName.substring(1));
					write(']');
				} else {
					writeQuotedIfNonIdentifier(computedName);
				}
			}
		} else {
			// all other cases than computed property names: IDENTIFIER, STRING, NUMBER
			final String propName = name.getName();
			if (propName.startsWith(N4JSLanguageUtils.SYMBOL_IDENTIFIER_PREFIX)) {
				// we have a name like "#iterator" that represents a Symbol --> emit as: "[Symbol.iterator]"
				// (note: we have to do this special handling here in the pretty printer because there is, at the
				// moment, no way to represent a property assignment with a Symbol as name other than using a name
				// starting with the SYMBOL_IDENTIFIER_PREFIX)
				write("[Symbol.");
				write(propName.substring(1));
				write(']');
			} else {
				// standard case:
				writeQuotedIfNonIdentifier(propName);
			}
		}
	}

	private boolean processModifiers(List<N4Modifier> modifiers, Set<N4Modifier> ignoredModifiers) {
		final int len = modifiers.size();
		boolean didEmitSomething = false;
		for (int idx = 0; idx < len; idx++) {
			N4Modifier m = modifiers.get(idx);
			if (ignoredModifiers != null && ignoredModifiers.contains(m)) {
				continue;
			}
			if (m == N4Modifier.PROJECT) {
				m = N4Modifier.PUBLIC;
			}
			if (didEmitSomething) {
				write(' ');
			}
			write(m.getName());
			didEmitSomething = true;
		}
		return didEmitSomething;
	}

	private boolean processModifiers(List<N4Modifier> modifiers, Set<N4Modifier> ignoredModifiers, String suffix) {
		boolean didEmitSomething = processModifiers(modifiers, ignoredModifiers);
		if (didEmitSomething) {
			write(suffix);
		}
		return didEmitSomething;
	}

	private boolean processMemberModifiers(N4MemberDeclaration member) {
		List<N4Modifier> modifiers = member.getDeclaredModifiers();
		Set<N4Modifier> ignore = member.getOwner() instanceof N4InterfaceDeclaration ? ACCESSIBILITY_MODIFIERS
				: Collections.emptySet();
		return processModifiers(modifiers, ignore, " ");
	}

	private boolean processTopLevelElementModifiers(List<N4Modifier> modifiers) {
		Set<N4Modifier> ignore = new HashSet<>();
		ignore.add(N4Modifier.EXTERNAL);
		ignore.addAll(ACCESSIBILITY_MODIFIERS);
		return processModifiers(modifiers, ignore, " ");
	}

	private void processReturnTypeRef(FunctionDefinition funDef) {
		if (funDef instanceof N4MethodDeclaration && ((N4MethodDeclaration) funDef).isConstructor()) {
			return;
		}
		write(": ");
		processTypeRefNode(funDef.getDeclaredReturnTypeRefNode());
	}

	private void processDeclaredTypeRef(TypeProvidingElement elem) {
		TypeReferenceNode<?> declaredTypeRefNode = elem.getDeclaredTypeRefNode();
		if (declaredTypeRefNode != null) {
			write(": ");
			processTypeRefNode(declaredTypeRefNode);
		}
	}

	private void processTypeRefNode(TypeReferenceNode<?> typeRefNode) {
		if (typeRefNode == null) {
			return; // do not emit suffx in this case
		}
		if (!(typeRefNode instanceof TypeReferenceNode_IM<?>)) {
			throw new IllegalStateException("");
		}
		String code = ((TypeReferenceNode_IM<?>) typeRefNode).getCodeToEmit();
		if (code == null) {
			throw new IllegalStateException(
					"encountered a TypeReferenceNode_IM without 'typeRefAsCode' (transformations are expected to either remove this node or set the code string)");
		}
		write(code);
	}

	private void processTypeParams(EList<N4TypeVariable> typeParams) {
		if (typeParams.isEmpty())
			return;

		processBlockLike(typeParams, '<', ",", null, '>', false);
	}

	private void processBlock(Collection<? extends Statement> statements) {
		processBlockLike(statements, '{', null, null, '}');
	}

	private void processBlockLike(Collection<? extends EObject> elemsInIM, char open, String lineEnd,
			String lastLineEnd, char close) {
		processBlockLike(elemsInIM, open, lineEnd, lastLineEnd, close, true);
	}

	private void processBlockLike(Collection<? extends EObject> elemsInIM, char open, String lineEnd,
			String lastLineEnd, char close, boolean newLines) {
		processBlockLike(elemsInIM, open, lineEnd, lastLineEnd, close, newLines, this::process);
	}

	/**
	 * Process and indent the given elements in the same way as blocks are indented but using the given characters for
	 * opening and closing the code section.
	 */
	private void processBlockLike(Collection<? extends EObject> elemsInIM, char open, String lineEnd,
			String lastLineEnd, char close, boolean newLines, Consumer<EObject> process) {
		if (elemsInIM.isEmpty()) {
			write(open);
			write(close);
			return;
		}
		write(open);
		out.indent();
		if (newLines) {
			newLine();
		}
		process(elemsInIM, process, () -> {
			if (lineEnd != null) {
				write(lineEnd);
			}
			if (newLines) {
				newLine();
			}
		});
		if (lastLineEnd != null) {
			write(lineEnd);
		}
		out.undent();
		if (newLines) {
			newLine();
		}
		write(close);
	}

	private String quote(String txt) {
		return '\'' + ValueConverterUtils.convertToEscapedString(txt, false) + '\'';
	}

	private String prependHashbang(String txt) {
		return "#!" + txt;
	}

	/**
	 * We call this method in methods that we do not want to delete but aren't used and tests for now.
	 */
	private void throwUnsupportedSyntax() {
		throw new UnsupportedOperationException("syntax not supported by pretty printer");
	}
}
