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
import org.jkiss.utils.Base64;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 Stream oriented XML document builder.
 */
public class XMLBuilder {

    private static final String XMLNS = "xmlns";
    private static final String NS_XML = "http://www.w3.org/TR/REC-xml";
    private static final String PREFIX_XML = "xml";

    public final class Element implements AutoCloseable {

        private Element parent;
        private String name;
        private Map<String, String> nsStack = null;
        private int level;

        Element(Element parent, String name) {
            this.init(parent, name);
        }

        void init(Element parent, String name) {
            this.parent = parent;
            this.name = name;
            this.nsStack = null;
            this.level = parent == null ? 0 : parent.level + 1;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public void addNamespace(String nsURI, String nsPrefix) {
            if (nsStack == null) {
                nsStack = new HashMap<>();
            }
            nsStack.put(nsURI, nsPrefix);
        }

        public String getNamespacePrefix(String nsURI) {
            if (nsURI.equals(NS_XML)) {
                return PREFIX_XML;
            }
            String prefix = (nsStack == null ? null : nsStack.get(nsURI));
            return prefix != null ?
                prefix :
                (parent != null ? parent.getNamespacePrefix(nsURI) : null);
        }

        @Override
        public void close() throws IOException {
            XMLBuilder.this.endElement();
        }
    }

    // At the beginning and after tag closing
    private static final int STATE_NOTHING = 0;
    // After tag opening
    private static final int STATE_ELEM_OPENED = 1;
    // After text added
    private static final int STATE_TEXT_ADDED = 2;

    private static final int IO_BUFFER_SIZE = 8192;

    private Writer writer;

    private int state = STATE_NOTHING;

    private Element element = null;
    private boolean beautify = false;

    private final List<Element> trashElements = new ArrayList<>();

    public XMLBuilder(
        @NotNull OutputStream stream,
        @Nullable String documentEncoding
    ) throws IOException {
        this(stream, documentEncoding, true);
    }

    public XMLBuilder(
        @NotNull OutputStream stream,
        @Nullable String documentEncoding,
        boolean printHeader
    ) throws IOException {
        if (documentEncoding == null) {
            this.init(new java.io.OutputStreamWriter(stream), null, printHeader);
        } else {
            this.init(
                new java.io.OutputStreamWriter(stream, documentEncoding),
                documentEncoding,
                printHeader);
        }
    }

    public XMLBuilder(
        @NotNull Writer writer,
        @Nullable String documentEncoding
    ) throws IOException {
        this(writer, documentEncoding, true);
    }

    public XMLBuilder(
        @NotNull Writer writer,
        @Nullable String documentEncoding,
        boolean printHeader
    ) throws IOException {
        this.init(writer, documentEncoding, printHeader);
    }

    @NotNull
    private Element createElement(
        @NotNull Element parent,
        @NotNull String name
    ) {
        if (trashElements.isEmpty()) {
            return new Element(parent, name);
        } else {
            Element element = trashElements.remove(trashElements.size() - 1);
            element.init(parent, name);
            return element;
        }
    }

    private void deleteElement(@NotNull Element element) {
        trashElements.add(element);
    }

    private void init(
        @NotNull Writer writer,
        @Nullable String documentEncoding,
        boolean printHeader
    ) throws IOException {
        this.writer = new java.io.BufferedWriter(writer, IO_BUFFER_SIZE);

        if (printHeader) {
            if (documentEncoding != null) {
                this.writer.write(XMLUtils.xmlHeader(documentEncoding));
            } else {
                this.writer.write(XMLUtils.xmlHeader());
            }
        }
    }

    public boolean isBeautify() {
        return beautify;
    }

    public void setBeautify(boolean beautify) {
        this.beautify = beautify;
    }

    @NotNull
    public Element startElement(@NotNull String elementName) throws IOException {
        return this.startElement(null, null, elementName);
    }

    @NotNull
    public Element startElement(
        @Nullable String nsURI,
        @NotNull String elementName
    ) throws IOException {
        return this.startElement(nsURI, null, elementName);
    }

    /*
         NS prefix will be used in element name if its directly specified
         as nsPrefix parameter or if nsURI has been declared above
     */
    @NotNull
    public Element startElement(
        @Nullable String nsURI,
        @Nullable String nsPrefix,
        @NotNull String elementName
    ) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_NOTHING:
                if (beautify) {
                    writer.write('\n');
                }
                break;
            default:
                break;
        }
        if (beautify) {
            if (element != null) {
                for (int i = 0; i <= element.getLevel(); i++) {
                    writer.write('\t');
                }
            }
        }
        writer.write('<');

        boolean addNamespace = (nsURI != null);

        // If old nsURI specified - use prefix
        if (nsURI != null) {
            if (nsPrefix == null && element != null) {
                nsPrefix = element.getNamespacePrefix(nsURI);
                if (nsPrefix != null) {
                    // Do not add NS declaration - it was declared somewhere above
                    addNamespace = false;
                }
            }
        }

        // If we have prefix - use it in tag name
        if (nsPrefix != null) {
            elementName = nsPrefix + ':' + elementName;
        }

        writer.write(elementName);
        state = STATE_ELEM_OPENED;

        element = this.createElement(element, elementName);

        if (addNamespace) {
            this.addNamespace(nsURI, nsPrefix);
            element.addNamespace(nsURI, nsPrefix);
        }

        return element;
    }

    @NotNull
    public XMLBuilder endElement() throws IOException, IllegalStateException {
        if (element == null) {
            throw new IllegalStateException("Close tag without open");
        }

        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write("/>");
                break;
            case STATE_NOTHING:
                if (beautify) {
                    writer.write('\n');
                    for (int i = 0; i < element.getLevel(); i++) {
                        writer.write('\t');
                    }
                }
            case STATE_TEXT_ADDED:
                writer.write("</");
                writer.write(element.getName());
                writer.write('>');
            default:
                break;
        }

        this.deleteElement(element);
        element = element.parent;
        state = STATE_NOTHING;

        return this;
    }

    @NotNull
    public XMLBuilder addNamespace(@NotNull String nsURI) throws IOException {
        return this.addNamespace(nsURI, null);
    }

    @NotNull
    public XMLBuilder addNamespace(
        @NotNull String nsURI,
        @Nullable String nsPrefix
    ) throws IOException, IllegalStateException {
        if (element == null) {
            throw new IllegalStateException("Namespace outside of element");
        }
        String attrName = XMLNS;
        if (nsPrefix != null) {
            attrName = attrName + ':' + nsPrefix;
            element.addNamespace(nsURI, nsPrefix);
        }
        this.addAttribute(null, attrName, nsURI, true);

        return this;
    }

    @NotNull
    public XMLBuilder addAttribute(
        @NotNull String attributeName,
        @Nullable String attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, attributeValue, true);
    }

    @NotNull
    public XMLBuilder addAttribute(
        @NotNull String attributeName,
        int attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, String.valueOf(attributeValue), false);
    }

    @NotNull
    public XMLBuilder addAttribute(
        String attributeName,
        long attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, String.valueOf(attributeValue), false);
    }

    public XMLBuilder addAttribute(
        String attributeName,
        boolean attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, String.valueOf(attributeValue), false);
    }

    @NotNull
    public XMLBuilder addAttribute(
        @NotNull String attributeName,
        float attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, String.valueOf(attributeValue), false);
    }

    @NotNull
    public XMLBuilder addAttribute(
        @NotNull String attributeName,
        double attributeValue
    ) throws IOException {
        return this.addAttribute(null, attributeName, String.valueOf(attributeValue), false);
    }

    @NotNull
    public XMLBuilder addAttribute(
        @Nullable String nsURI,
        @NotNull String attributeName,
        @Nullable String attributeValue
    ) throws IOException {
        return this.addAttribute(nsURI, attributeName, attributeValue, true);
    }

    @NotNull
    private XMLBuilder addAttribute(
        @Nullable String nsURI,
        @NotNull String attributeName,
        @Nullable String attributeValue,
        boolean escape
    ) throws IOException, IllegalStateException {
        switch (state) {
            case STATE_ELEM_OPENED: {
                if (nsURI != null) {
                    String nsPrefix = element.getNamespacePrefix(nsURI);
                    if (nsPrefix == null) {
                        throw new IllegalStateException(
                            "Unknown attribute '" + attributeName + "' namespace URI '" + nsURI + "' in element '" + element.getName() + "'");
                    }
                    attributeName = nsPrefix + ':' + attributeName;
                }
                writer.write(' ');
                writer.write(attributeName);
                writer.write("=\"");
                if (attributeValue != null) {
                    writer.write(escape ? XMLUtils.escapeXml(attributeValue) : attributeValue);
                }
                writer.write('"');
                break;
            }
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                throw new IllegalStateException("Attribute outside of element");
            default:
                break;
        }

        return this;
    }

    @NotNull
    public XMLBuilder addText(@NotNull CharSequence textValue) throws IOException {
        return addText(textValue, true);
    }

    @NotNull
    public XMLBuilder addText(@NotNull CharSequence textValue, boolean escape) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                break;
            default:
                break;
        }
        this.writeText(textValue, escape);

        state = STATE_TEXT_ADDED;

        return this;
    }

    /**
     Adds entire content of specified reader as text

     @param reader text reader
     @return self reference
     @throws IOException on IO error
     */
    @NotNull
    public XMLBuilder addText(@NotNull Reader reader) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                break;
            default:
                break;
        }

        writer.write("<![CDATA[");
        char[] writeBuffer = new char[8192];
        for (int br = reader.read(writeBuffer); br != -1; br = reader.read(writeBuffer)) {
            writer.write(new String(writeBuffer, 0, br));
        }
        writer.write("]]>");

        state = STATE_TEXT_ADDED;

        return this;
    }

    @NotNull
    public XMLBuilder addTextData(@NotNull String text) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                break;
            default:
                break;
        }

        writer.write("<![CDATA[");
        writer.write(text);
        writer.write("]]>");

        state = STATE_TEXT_ADDED;

        return this;
    }

    /**
     Adds content of specified stream as Base64 encoded text

     @param stream Input content stream
     @param length Content length (this parameter must be correctly specified)
     @return self reference
     @throws IOException on IO error
     */
    @NotNull
    public XMLBuilder addBinary(@NotNull InputStream stream, int length) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                break;
            default:
                break;
        }

        Base64.encode(stream, length, writer);
        state = STATE_TEXT_ADDED;

        return this;
    }

    @NotNull
    public XMLBuilder addBinary(@NotNull byte[] buffer) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_TEXT_ADDED:
            case STATE_NOTHING:
                break;
            default:
                break;
        }

        Base64.encode(buffer, 0, buffer.length, writer);
        state = STATE_TEXT_ADDED;

        return this;
    }

    /**
     Adds character content as is without any escaping or validation

     @param textValue content
     @return self reference
     */
    @NotNull
    public XMLBuilder addContent(@NotNull CharSequence textValue) throws IOException {
        writer.write(textValue.toString());
        return this;
    }

    @NotNull
    public XMLBuilder addComment(@NotNull String commentValue) throws IOException {
        switch (state) {
            case STATE_ELEM_OPENED:
                writer.write('>');
            case STATE_NOTHING:
                if (beautify) {
                    writer.write('\n');
                }
                break;
            default:
                break;
        }
        writer.write("<!--");
        writer.write(commentValue);
        writer.write("-->");
        if (beautify) {
            writer.write('\n');
        }
        state = STATE_TEXT_ADDED;

        return this;
    }

    @NotNull
    public XMLBuilder addElement(@NotNull String elementName, @NotNull String elementValue) throws IOException {
        try (var ignored = this.startElement(elementName)) {
            this.addText(elementValue);
        }
        return this;
    }

    @NotNull
    public XMLBuilder addElementText(String elementName, String elementValue) throws IOException {
        try (var ignored = this.startElement(elementName)) {
            this.addTextData(elementValue);
        }
        return this;
    }

    @NotNull
    public XMLBuilder flush() throws IOException {
        writer.flush();
        return this;
    }

    @NotNull
    private XMLBuilder writeText(@Nullable CharSequence textValue, boolean escape) throws IOException {
        if (textValue != null) {
            writer.write(escape ? XMLUtils.escapeXml(textValue) : textValue.toString());
        }
        return this;
    }

}
