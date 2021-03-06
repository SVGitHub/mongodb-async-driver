/*
 * #%L
 * Document.java - mongodb-async-driver - Allanbank Consulting, Inc.
 * %%
 * Copyright (C) 2011 - 2014 Allanbank Consulting, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.allanbank.mongodb.bson;

import java.io.Serializable;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Interface for a document.
 *
 * @api.yes This interface is part of the driver's API. Public and protected
 *          members will be deprecated for at least 1 non-bugfix release
 *          (version numbers are &lt;major&gt;.&lt;minor&gt;.&lt;bugfix&gt;)
 *          before being removed or modified.
 * @copyright 2011-2013, Allanbank Consulting, Inc., All Rights Reserved
 */
@ThreadSafe
public interface Document
        extends Iterable<Element>, DocumentAssignable, Serializable {

    /**
     * Accepts the visitor and calls the appropriate method on the visitor based
     * on the document type.
     *
     * @param visitor
     *            THe visitor for the document.
     */
    public void accept(Visitor visitor);

    /**
     * Returns true if the document contains an element with the specified name.
     *
     * @param name
     *            The name of the element to locate.
     * @return True if the document contains an element with the given name,
     *         false otherwise.
     */
    public boolean contains(String name);

    /**
     * Returns the elements matching the path of regular expressions.
     *
     * @param <E>
     *            The type of element to match.
     * @param clazz
     *            The class of elements to match.
     * @param nameRegexs
     *            The path of regular expressions.
     * @return The elements matching the path of regular expressions. May be an
     *         empty list but will never be <code>null</code>.
     */
    public <E extends Element> List<E> find(Class<E> clazz,
            String... nameRegexs);

    /**
     * Returns the elements matching the path of regular expressions.
     *
     * @param nameRegexs
     *            The path of regular expressions.
     * @return The elements matching the path of regular expressions. May be an
     *         empty list but will never be <code>null</code>.
     */
    public List<Element> find(String... nameRegexs);

    /**
     * Returns the first element matching the path of regular expressions.
     * <p>
     * Note: It is much faster to use the {@link #get(Class,String)} method on a
     * document than to use the {@link #findFirst(String...)} with a single
     * {@code nameRegexs}.
     * </p>
     *
     * @param <E>
     *            The type of element to match.
     * @param clazz
     *            The class of element to match.
     * @param nameRegexs
     *            The path of regular expressions.
     * @return The first element matching the path of regular expressions.
     */
    public <E extends Element> E findFirst(Class<E> clazz, String... nameRegexs);

    /**
     * Returns the first element matching the path of regular expressions.
     * <p>
     * Note: It is much faster to use the {@link #get(String)} method on a
     * document than to use the {@link #findFirst(String...)} with a single
     * {@code nameRegexs}.
     * </p>
     *
     * @param nameRegexs
     *            The path of regular expressions.
     * @return The first element matching the path of regular expressions.
     */
    public Element findFirst(String... nameRegexs);

    /**
     * Returns the element with the specified name or null if no element with
     * that name exists.
     *
     * @param <E>
     *            The type of element to get.
     * @param clazz
     *            The class of element to get.
     * @param name
     *            The name of the element to locate.
     * @return The sub-element in the document with the given name or null if
     *         element exists with the given name.
     */
    public <E extends Element> E get(Class<E> clazz, String name);

    /**
     * Returns the element with the specified name or null if no element with
     * that name exists.
     *
     * @param name
     *            The name of the element to locate.
     * @return The sub-element in the document with the given name or null if
     *         element exists with the given name.
     */
    public Element get(String name);

    /**
     * Returns the array of elements that create this document.
     *
     * @return The array of elements that create this document.
     */
    public List<Element> getElements();

    /**
     * Returns the size of the document when encoded as bytes.
     *
     * @return The size of the document when encoded as bytes.
     */
    public long size();
}
