/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.utils.xml;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Common XML utils
 */
public class XMLUtils {

    public static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    public static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    public static final String FEATURE_DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";

    @NotNull
    public static DocumentBuilderFactory newSecureDocumentBuilderFactory() throws XMLException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(XMLUtils.FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(XMLUtils.FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(XMLUtils.FEATURE_DISALLOW_DOCTYPE_DECL, true);
            return factory;
        } catch (ParserConfigurationException e) {
            throw new XMLException("Exception while setting security feature for DocumentBuilderFactory", e);
        }
    }

    @NotNull
    public static Document parseDocument(@NotNull String fileName) throws XMLException {
        return parseDocument(Path.of(fileName));
    }

    @NotNull
    public static Document parseDocument(@NotNull Path file) throws XMLException {
        try (InputStream is = Files.newInputStream(file)) {
            return parseDocument(new InputSource(is));
        } catch (IOException e) {
            throw new XMLException("Error opening file '" + file + "'", e);
        }
    }

    @NotNull
    public static Document parseDocument(@NotNull InputStream is) throws XMLException {
        return parseDocument(new InputSource(is));
    }

    @NotNull
    public static Document parseDocument(@NotNull Reader is) throws XMLException {
        return parseDocument(new InputSource(is));
    }

    @NotNull
    public static Document parseDocument(@NotNull InputSource source) throws XMLException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature(FEATURE_DISALLOW_DOCTYPE_DECL, true);
            DocumentBuilder xmlBuilder = dbf.newDocumentBuilder();
            return xmlBuilder.parse(source);
        } catch (Exception er) {
            throw new XMLException("Error parsing XML document", er);
        }
    }

    @NotNull
    public static Document createDocument() throws XMLException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder xmlBuilder = dbf.newDocumentBuilder();
            return xmlBuilder.newDocument();
        } catch (Exception er) {
            throw new XMLException("Error creating XML document", er);
        }
    }

    @Nullable
    public static Element getChildElement(@Nullable Element element, @NotNull String childName) {
        if (element == null) {
            return null;
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE &&
                ((Element) node).getTagName().equals(childName)) {
                return (Element) node;
            }
        }
        return null;
    }

    @Nullable
    public static String getChildElementBody(@Nullable Element element, @NotNull String childName) {
        if (element == null) {
            return null;
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE &&
                ((Element) node).getTagName().equals(childName)) {
                return getElementBody((Element) node);
            }
        }
        return null;
    }

    @Nullable
    public static String getElementBody(@NotNull Element element) {
        return element.getTextContent();
    }

    // Get list of all child elements of specified node
    @NotNull
    public static List<Element> getChildElementList(
        Element parent,
        String nodeName
    ) {
        List<Element> list = new ArrayList<>();
        if (parent != null) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() == Node.ELEMENT_NODE &&
                    nodeName.equals(node.getNodeName())) {
                    list.add((Element) node);
                }
            }
        }
        return list;
    }

    // Get list of all child elements of specified node
    @NotNull
    public static Collection<Element> getChildElementList(@NotNull Element parent, @NotNull String[] nodeNameList) {
        List<Element> list = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                for (String s : nodeNameList) {
                    if (node.getNodeName().equals(s)) {
                        list.add((Element) node);
                    }
                }
            }
        }
        return list;
    }
    @Nullable
    public static String escapeXml(@Nullable CharSequence str) {
        if (str == null) {
            return null;
        }
        StringBuilder res = null;
        int strLength = str.length();
        for (int i = 0; i < strLength; i++) {
            char c = str.charAt(i);
            String repl = encodeXMLChar(c);
            if (repl == null) {
                if (res != null) {
                    res.append(c);
                }
            } else {
                if (res == null) {
                    res = new StringBuilder(str.length() + 5);
                    for (int k = 0; k < i; k++) {
                        res.append(str.charAt(k));
                    }
                }
                res.append(repl);
            }
        }
        return res == null ? str.toString() : res.toString();
    }

    /**
     * Encodes a char to XML-valid form replacing &amp;,',",&lt;,&gt; with special XML encoding.
     *
     * @param ch char to convert
     * @return XML-encoded text
     */
    @Nullable
    public static String encodeXMLChar(char ch) {
        return switch (ch) {
            case '&' -> "&amp;";
            case '\"' -> "&quot;";
            case '\'' -> "&#39;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            default -> null;
        };
    }

    @NotNull
    public static XMLException adaptSAXException(@NotNull Exception toCatch) {
        if (toCatch instanceof XMLException) {
            return (XMLException) toCatch;
        } else if (toCatch instanceof org.xml.sax.SAXException) {
            String message = toCatch.getMessage();
            Exception embedded = ((org.xml.sax.SAXException) toCatch).getException();
            if (embedded != null && embedded.getMessage() != null && embedded.getMessage().equals(message)) {
                // Just SAX wrapper - skip it
                return adaptSAXException(embedded);
            } else {
                return new XMLException(
                    message,
                    embedded != null ? adaptSAXException(embedded) : null);
            }
        } else {
            return new XMLException(toCatch.getMessage(), toCatch);
        }
    }

    @NotNull
    public static Collection<Element> getChildElementList(@Nullable Element element) {
        List<Element> children = new ArrayList<>();
        if (element != null) {
            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    children.add((Element) node);
                }
            }
        }
        return children;
    }

    @NotNull
    public static String xmlHeader() {
        return "<?xml version=\"1.0\"?>";
    }

    @NotNull
    public static String xmlHeader(@NotNull String encoding) {
        return "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>";
    }
}
