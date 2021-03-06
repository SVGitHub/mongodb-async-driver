/*
 * #%L
 * ReplyArrayCallback.java - mongodb-async-driver - Allanbank Consulting, Inc.
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

package com.allanbank.mongodb.client.callback;

import java.util.Collections;
import java.util.List;

import com.allanbank.mongodb.Callback;
import com.allanbank.mongodb.MongoDbException;
import com.allanbank.mongodb.MongoIterator;
import com.allanbank.mongodb.bson.Document;
import com.allanbank.mongodb.bson.Element;
import com.allanbank.mongodb.bson.element.ArrayElement;
import com.allanbank.mongodb.client.SimpleMongoIteratorImpl;
import com.allanbank.mongodb.client.message.Reply;
import com.allanbank.mongodb.error.ReplyException;

/**
 * Callback to expect and extract a single document from the reply and then
 * extract its contained array.
 *
 * @api.no This class is <b>NOT</b> part of the drivers API. This class may be
 *         mutated in incompatible ways between any two releases of the driver.
 * @copyright 2011-2013, Allanbank Consulting, Inc., All Rights Reserved
 */
public class ReplyArrayCallback
        extends AbstractReplyCallback<MongoIterator<Element>> {

    /** The default name for the values array. */
    public static final String DEFAULT_NAME = "values";

    /** The name of the array element in the reply document. */
    private final String myName;

    /**
     * Create a new ReplyDocumentCallback.
     *
     * @param results
     *            The callback to notify of the reply document.
     */
    public ReplyArrayCallback(final Callback<MongoIterator<Element>> results) {
        this(DEFAULT_NAME, results);
    }

    /**
     * Create a new ReplyDocumentCallback.
     *
     * @param name
     *            The name of the array element in the reply document.
     * @param results
     *            The callback to notify of the reply document.
     */
    public ReplyArrayCallback(final String name,
            final Callback<MongoIterator<Element>> results) {
        super(results);

        myName = name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates an exception if the {@link Reply} has less than or more than a
     * single reply document.
     * </p>
     *
     * @param reply
     *            The raw reply.
     * @return The exception created.
     */
    @Override
    protected MongoDbException asError(final Reply reply) {
        MongoDbException error = super.asError(reply);
        if (error == null) {
            final List<Document> results = reply.getResults();
            if (results.size() != 1) {
                error = new ReplyException(reply,
                        "Should only be a single document in the reply.");
            }
            else if (reply.getResults().get(0).find(ArrayElement.class, myName)
                    .isEmpty()) {
                error = new ReplyException(reply, "No '" + myName
                        + "' array in the reply.");
            }
        }
        return error;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return the reply document.
     * </p>
     *
     * @see AbstractReplyCallback#convert(Reply)
     */
    @Override
    protected MongoIterator<Element> convert(final Reply reply)
            throws MongoDbException {
        final List<Document> results = reply.getResults();
        if (results.size() == 1) {
            List<Element> entries = Collections.emptyList();
            final Document document = results.get(0);
            final ArrayElement array = document.findFirst(ArrayElement.class,
                    myName);
            if (array != null) {
                entries = array.getEntries();
            }

            return new SimpleMongoIteratorImpl<Element>(entries);
        }

        return null;
    }
}