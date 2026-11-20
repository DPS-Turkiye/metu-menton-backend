package com.dps.teknokent.security.institution;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * What CAS answers when asked whether a ticket is good ({@code /serviceValidate}, {@code /p3/serviceValidate}): who
 * signed in, or why not. The XML arrives from another server, so it is read with document types and every outside
 * reference switched off (no XXE, no entity expansion), and anything that is not exactly one answer in CAS's own
 * namespace is refused.
 */
sealed interface CasServiceResponse {

	String NAMESPACE = "http://www.yale.edu/tp/cas";

	/** {@code attributes}: lower-cased name to its values; empty for a CAS 2.0 server, which sends none. */
	record Success(String user, Map<String, List<String>> attributes) implements CasServiceResponse {
	}

	/** {@code code}: CAS's own, such as INVALID_TICKET or INVALID_SERVICE. */
	record Failure(String code) implements CasServiceResponse {
	}

	/** An {@link IllegalArgumentException} for anything that is not a CAS answer; its message never quotes the XML. */
	static CasServiceResponse parse(String xml) {
		Element root = read(xml).getDocumentElement();
		if (!isCas(root, "serviceResponse")) {
			throw new IllegalArgumentException("the root is not cas:serviceResponse");
		}
		List<Element> answers = children(root);
		if (answers.size() != 1) {
			throw new IllegalArgumentException("expected one answer, found " + answers.size());
		}
		Element answer = answers.getFirst();
		if (isCas(answer, "authenticationFailure")) {
			return new Failure(answer.getAttribute("code"));
		}
		if (!isCas(answer, "authenticationSuccess")) {
			throw new IllegalArgumentException("the answer is neither a success nor a failure");
		}
		List<Element> users = children(answer).stream().filter(child -> isCas(child, "user")).toList();
		if (users.size() != 1) {
			throw new IllegalArgumentException("expected one cas:user, found " + users.size());
		}
		String user = users.getFirst().getTextContent().strip();
		if (user.isEmpty()) {
			throw new IllegalArgumentException("cas:user is empty");
		}
		Map<String, List<String>> attributes = new LinkedHashMap<>();
		for (Element group : children(answer)) {
			if (isCas(group, "attributes")) {
				for (Element attribute : children(group)) {
					String value = attribute.getTextContent().strip();
					if (!value.isEmpty()) {
						attributes.computeIfAbsent(attribute.getLocalName().toLowerCase(Locale.ROOT),
								name -> new ArrayList<>()).add(value);
					}
				}
			}
		}
		return new Success(user, attributes);
	}

	private static Document read(String xml) {
		try {
			DocumentBuilder builder = hardenedFactory().newDocumentBuilder();
			// The default handler prints to stderr; this one only throws on a fatal error.
			builder.setErrorHandler(new DefaultHandler());
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (ParserConfigurationException | SAXException | IOException exception) {
			throw new IllegalArgumentException("the answer is not XML a hardened parser accepts", exception);
		}
	}

	/** Refusing the document type alone stops XXE; the rest holds if a parser ever ignores that one switch. */
	private static DocumentBuilderFactory hardenedFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setNamespaceAware(true);
		return factory;
	}

	private static boolean isCas(Element element, String name) {
		return NAMESPACE.equals(element.getNamespaceURI()) && name.equals(element.getLocalName());
	}

	private static List<Element> children(Element parent) {
		List<Element> children = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element) {
				children.add(element);
			}
		}
		return children;
	}
}
