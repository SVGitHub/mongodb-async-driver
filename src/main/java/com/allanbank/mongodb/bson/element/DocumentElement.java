/*
 * Copyright 2011, Allanbank Consulting, Inc. 
 *           All Rights Reserved
 */
package com.allanbank.mongodb.bson.element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.allanbank.mongodb.bson.Document;
import com.allanbank.mongodb.bson.Element;
import com.allanbank.mongodb.bson.ElementType;
import com.allanbank.mongodb.bson.Visitor;

/**
 * Wraps a single BSON document that may contain nested documents.
 * 
 * @copyright 2011, Allanbank Consulting, Inc., All Rights Reserved
 */
public class DocumentElement extends AbstractElement implements Document {

    /** The BSON type for a document. */
    public static final ElementType TYPE = ElementType.DOCUMENT;

    /**
     * Constructed when a user tries to access the elements of the document by
     * name.
     */
    private Map<String, Element> myElementMap;

    /** The elements of the document. */
    private List<Element> myElements;

    /**
     * Constructs a new {@link DocumentElement}.
     * 
     * @param name
     *            The name for the BSON document.
     * @param value
     *            The document to copy elements from.
     */
    public DocumentElement(final String name, final Document value) {
        super(TYPE, name);

        final List<Element> elements = new ArrayList<Element>();
        for (final Element element : value) {
            elements.add(element);
        }
        myElements = Collections.unmodifiableList(elements);
    }

    /**
     * Constructs a new {@link DocumentElement}.
     * 
     * @param name
     *            The name for the BSON document.
     * @param elements
     *            The sub-elements for the document.
     */
    public DocumentElement(final String name, final Element... elements) {
        super(TYPE, name);

        if (elements.length > 0) {
            myElements = Collections.unmodifiableList(new ArrayList<Element>(
                    Arrays.asList(elements)));
        }
        else {
            myElements = Collections.emptyList();
        }
    }

    /**
     * Constructs a new {@link DocumentElement}.
     * 
     * @param name
     *            The name for the BSON document.
     * @param elements
     *            The sub-elements for the document.
     */
    public DocumentElement(final String name, final List<Element> elements) {
        super(TYPE, name);

        if ((elements != null) && !elements.isEmpty()) {
            myElements = Collections.unmodifiableList(new ArrayList<Element>(
                    elements));
        }
        else {
            myElements = Collections.emptyList();
        }

    }

    /**
     * Accepts the visitor and calls the {@link Visitor#visitDocument} method.
     * 
     * @see Element#accept(Visitor)
     */
    @Override
    public void accept(final Visitor visitor) {
        visitor.visitDocument(getName(), getElements());
    }

    /**
     * Returns true if the document contains an element with the specified name.
     * 
     * @see Document#contains(String)
     */
    @Override
    public boolean contains(final String name) {
        return getElementMap().containsKey(name);
    }

    /**
     * Determines if the passed object is of this same type as this object and
     * if so that its fields are equal.
     * 
     * @param object
     *            The object to compare to.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object object) {
        boolean result = false;
        if (this == object) {
            result = true;
        }
        else if ((object != null) && (getClass() == object.getClass())) {
            final DocumentElement other = (DocumentElement) object;

            result = super.equals(object)
                    && myElements.equals(other.myElements);
        }
        return result;
    }

    /**
     * Returns the element with the specified name or null if no element with
     * that name exists.
     * 
     * @see Document#get(String)
     */
    @Override
    public Element get(final String name) {
        return getElementMap().get(name);
    }

    /**
     * Returns the elements in the document.
     * 
     * @return The elements in the document.
     */
    public List<Element> getElements() {
        return myElements;
    }

    /**
     * Computes a reasonable hash code.
     * 
     * @return The hash code value.
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = (31 * result) + super.hashCode();
        result = (31 * result)
                + ((myElements == null) ? 0 : myElements.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to add an {@link ObjectIdElement} to the head of the document.
     * </p>
     * 
     * @see com.allanbank.mongodb.bson.Document#injectId()
     */
    @Override
    public synchronized void injectId() {
        if (!contains("_id")) {
            final List<Element> newElements = new ArrayList<Element>();
            newElements.add(new ObjectIdElement("_id", new ObjectId()));
            newElements.addAll(myElements);

            if (myElementMap != null) {
                myElementMap.put("_id", newElements.get(0));
            }
            myElements = newElements;
        }
    }

    /**
     * Returns an iterator over the documents elements.
     * 
     * @see Iterable#iterator()
     */
    @Override
    public Iterator<Element> iterator() {
        return getElements().iterator();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Searches this sub-elements for matching elements on the path and are of
     * the right type.
     * </p>
     * 
     * @see Element#queryPath
     */
    @Override
    public <E extends Element> List<E> queryPath(final Class<E> clazz,
            final String... nameRegexs) {
        if (0 < nameRegexs.length) {
            final List<E> elements = new ArrayList<E>();
            final String nameRegex = nameRegexs[0];
            final String[] subNameRegexs = Arrays.copyOfRange(nameRegexs, 1,
                    nameRegexs.length);
            try {
                final Pattern pattern = Pattern.compile(nameRegex);
                for (final Element element : myElements) {
                    if (pattern.matcher(element.getName()).matches()) {
                        elements.addAll(queryPath(clazz, subNameRegexs));
                    }
                }

            }
            catch (final PatternSyntaxException pse) {
                // Assume a non-pattern?
                for (final Element element : myElements) {
                    if (nameRegex.equals(element.getName())) {
                        elements.addAll(queryPath(clazz, subNameRegexs));
                    }
                }
            }

            return elements;
        }

        // End of the path -- are we the right type
        if (clazz.isAssignableFrom(this.getClass())) {
            return Collections.singletonList(clazz.cast(this));
        }
        return Collections.emptyList();
    }

    /**
     * String form of the object.
     * 
     * @return A human readable form of the object.
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append('"');
        builder.append(getName());
        builder.append("\" : { ");

        boolean first = true;
        for (final Element element : myElements) {
            if (!first) {
                builder.append(",\n");
            }
            builder.append(element.toString());
            first = false;
        }
        builder.append("}\n");

        return builder.toString();
    }

    /**
     * Returns a map from the element names to the elements in the document.
     * Used for faster by-name access.
     * 
     * @return The element name to element mapping.
     */
    private Map<String, Element> getElementMap() {
        if (myElementMap == null) {
            final Map<String, Element> mapping = new HashMap<String, Element>(
                    myElements.size() + myElements.size());

            for (final Element element : myElements) {
                mapping.put(element.getName(), element);
            }

            // Swap the finished map into position.
            myElementMap = mapping;
        }

        return myElementMap;
    }
}
