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
package org.eclipse.n4js.ide.server.hover;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.n4js.jsdoc2spec.adoc.Html2ADocConverter;
import org.eclipse.n4js.n4JS.IdentifierRef;
import org.eclipse.n4js.n4JS.LiteralOrComputedPropertyName;
import org.eclipse.n4js.ts.types.TNamespace;
import org.eclipse.n4js.validation.N4JSElementKeywordProvider;
import org.eclipse.xtext.documentation.IEObjectDocumentationProvider;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.hover.HoverContext;
import org.eclipse.xtext.ide.server.hover.HoverService;
import org.eclipse.xtext.ide.server.hover.IHoverService;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.ITextRegion;
import org.eclipse.xtext.util.TextRegion;

import com.google.common.base.Strings;
import com.google.inject.Inject;

/**
 * Subclass of {@link HoverService} to show signature information
 */
@SuppressWarnings("deprecation") // due to MarkedString
public class N4JSHoverService extends HoverService {

	private static final String MARKUP_KIND_MARKDOWN = "markdown";

	@Inject
	private N4JSElementSignatureProvider signatureProvider;
	@Inject
	private N4JSElementKeywordProvider keywordProvider;
	@Inject
	private IEObjectDocumentationProvider documentationProvider;
	@Inject
	private EObjectAtOffsetHelper eobjectHelper;

	final private Html2ADocConverter html2adocConverter = new Html2ADocConverter();

	@Override
	protected Hover hover(HoverContext ctx) {
		if (ctx == null) {
			return IHoverService.EMPTY_HOVER;
		}

		List<Either<String, MarkedString>> contents = new LinkedList<>();
		EObject element = ctx.getElement();
		EObject idRef = getIdentifierRefOrElement(ctx);

		String signature = signatureProvider.get(idRef);
		if (signature != null) {
			String keyword = keywordProvider.keyword(element);
			String signatureLabel = composeFirstLine(keyword, signature);
			CharSequence signatureAdoc = html2adocConverter.transformHTML(signatureLabel);
			MarkedString mdSignatureLabel = new MarkedString("n4js", signatureAdoc.toString());
			contents.add(Either.forRight(mdSignatureLabel));
		}

		String documentation = documentationProvider.getDocumentation(element);
		if (documentation != null) {
			CharSequence docuAdoc = html2adocConverter.transformHTML(documentation);
			MarkedString mdDocumentation = new MarkedString(MARKUP_KIND_MARKDOWN, docuAdoc.toString());
			contents.add(Either.forRight(mdDocumentation));
		}

		return new Hover(contents, getRange(ctx));
	}

	private EObject getIdentifierRefOrElement(HoverContext ctx) {
		EObject idRef = null;

		if (ctx instanceof N4JSHoverContext) {
			idRef = ((N4JSHoverContext) ctx).getIdentifierRef();
		}
		if (idRef == null) {
			idRef = ctx.getElement();
		}
		if (ctx.getElement() instanceof TNamespace) {
			idRef = ctx.getElement();
		}
		return idRef;
	}

	private String composeFirstLine(String keyword, String label) {
		// TFunction#getFunctionAsString() already contains 'function'
		keyword = ("function".equals(keyword)) ? "" : keyword;
		String htmlKeyword = Strings.isNullOrEmpty(keyword) ? "" : keyword + " ";
		String htmlLabel = Strings.nullToEmpty(label);
		String line = htmlKeyword + htmlLabel;

		return line;
	}

	@Override
	protected HoverContext createContext(Document document, XtextResource resource, int offset) {
		HoverContext hoverContext = super.createContext(document, resource, offset);
		if (hoverContext == null) {
			return null;
		}

		EObject idRef = null;
		INode node = eobjectHelper.getCrossReferenceNode(resource, new TextRegion(offset, 0));
		if (node != null) {
			idRef = NodeModelUtils.findActualSemanticObjectFor(node);
		}

		return new N4JSHoverContext(hoverContext, idRef);
	}

	/** Subclass of {@link HoverContext} to add a field to the {@link IdentifierRef} of the {@link EObject}. */
	static public class N4JSHoverContext extends HoverContext {
		final private EObject idRef;

		/** Constructor */
		public N4JSHoverContext(Document document, XtextResource resource, int offset, ITextRegion region,
				EObject element) {
			this(document, resource, offset, region, element, null);
		}

		/** Constructor */
		public N4JSHoverContext(HoverContext hoverContext, EObject idRef) {
			this(hoverContext.getDocument(), hoverContext.getResource(), hoverContext.getOffset(),
					hoverContext.getRegion(), hoverContext.getElement(), idRef);
		}

		/** Constructor */
		public N4JSHoverContext(Document document, XtextResource resource, int offset, ITextRegion region,
				EObject element, EObject idRef) {

			super(document, resource, offset, region, redirectElement(element));
			this.idRef = idRef;
		}

		/** @return {@link IdentifierRef} of the {@link #getElement()}. Can be null. */
		public EObject getIdentifierRef() {
			return this.idRef;
		}

		static private EObject redirectElement(EObject element) {
			if (element instanceof LiteralOrComputedPropertyName) {
				element = element.eContainer();
			}
			return element;
		}
	}

}
