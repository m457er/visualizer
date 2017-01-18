/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.graalvm.visualizer.data.serialization;

import org.graalvm.visualizer.data.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class XMLParser implements ContentHandler {

    public static class MissingAttributeException extends SAXException {

        private String name;

        public MissingAttributeException(String name) {
            super("Missing attribute \"" + name + "\"");
            this.name = name;
        }

        public String getAttributeName() {
            return this.name;
        }
    }

    public static class HandoverElementHandler<P> extends ElementHandler<P, P> {

        @Override
        protected P start() throws SAXException {
            return getParentObject();
        }

        public HandoverElementHandler(String name) {
            super(name);
        }

        public HandoverElementHandler(String name, boolean needsText) {
            super(name, needsText);
        }
    }

    public static class TopElementHandler<P> extends ElementHandler<P, Object> {

        public TopElementHandler() {
            super(null);
        }
    }

    public static class ElementHandler<T, P> {

        private String name;
        private final ArrayList<T> object = new ArrayList<>();
        private Attributes attr;
        private StringBuilder currentText;
        private ParseMonitor monitor;
        private HashMap<String, ElementHandler<?, ? super T>> hashtable;
        private boolean needsText;
        private final ArrayList<ElementHandler<P, ?>> parentElement = new ArrayList<>();
        private final ArrayList<P> parentObject = new ArrayList<>();

        public ElementHandler(String name) {
            this(name, false);
        }

        public ElementHandler<P, ?> getParentElement() {
            return parentElement.get(parentElement.size() - 1);
        }

        public P getParentObject() {
            return parentObject.get(parentElement.size() - 1);
        }

        protected boolean needsText() {
            return needsText;
        }

        public ElementHandler(String name, boolean needsText) {
            this.hashtable = new HashMap<>();
            this.name = name;
            this.needsText = needsText;
        }

        public ParseMonitor getMonitor() {
            return monitor;
        }

        public ElementHandler<?, ? super T> getChild(String name) {
            return hashtable.get(name);
        }

        public void addChild(ElementHandler<?, ? super T> handler) {
            assert handler != null;
            hashtable.put(handler.getName(), handler);
        }

        public String getName() {
            return name;
        }

        public T getObject() {
            return object.isEmpty() ? null : object.get(object.size() - 1);
        }

        public String readAttribute(String name) {
            return attr.getValue(name);
        }

        public String readRequiredAttribute(String name) throws SAXException {
            String s = readAttribute(name);
            if (s == null) {
                throw new MissingAttributeException(name);
            }
            return s;
        }

        public void processAttributesAsProperties(Properties p) {
            int length = attr.getLength();
            for (int i = 0; i < length; i++) {
                String val = attr.getValue(i);
                String localName = attr.getLocalName(i);
                p.setProperty(val, localName);
            }
        }

        public void startElement(ElementHandler<P, ?> parentElement, Attributes attr, ParseMonitor monitor) throws SAXException {
            this.currentText = new StringBuilder();
            this.attr = attr;
            this.monitor = monitor;
            this.parentElement.add(parentElement);
            parentObject.add(parentElement.getObject());
            object.add(start());
        }

        protected T start() throws SAXException {
            return null;
        }

        protected void end(String text) throws SAXException {

        }

        public void endElement() throws SAXException {
            end(currentText.toString());
            object.remove(object.size() - 1);
            parentElement.remove(parentElement.size() - 1);
            parentObject.remove(parentObject.size() - 1);
        }

        protected void text(char[] c, int start, int length) {
            assert currentText != null;
            currentText.append(c, start, length);
        }
    }
    private ArrayList<ElementHandler<?, ?>> stack;
    private ParseMonitor monitor;

    public XMLParser(TopElementHandler<?> rootHandler, ParseMonitor monitor) {
        this.stack = new ArrayList<>();
        this.monitor = monitor;
        this.stack.add(rootHandler);
    }

    @Override
    public void setDocumentLocator(Locator locator) {

    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        assert !stack.isEmpty();

        ElementHandler<?,?> parent = stack.get(stack.size() - 1);
        if (parent != null) {
            ElementHandler child = parent.getChild(qName);
            if (child != null) {
                child.startElement(parent, atts, monitor);
                stack.add(child);
                return;
            }
        }

        stack.add(null);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        ElementHandler handler = stack.remove(stack.size() - 1);
        if (handler != null) {
            handler.endElement();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        assert !stack.isEmpty();


        ElementHandler top = stack.get(stack.size() - 1);
        if (top != null && top.needsText()) {
            top.text(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
    }
}
