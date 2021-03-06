/*
 * #%L
 * Header.java - mongodb-async-driver - Allanbank Consulting, Inc.
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
package com.allanbank.mongodb.client.message;

import java.io.IOException;

import com.allanbank.mongodb.bson.io.BsonInputStream;
import com.allanbank.mongodb.client.Operation;

/**
 * The header of a message.
 *
 * <pre>
 * <code>
 * struct MsgHeader {
 *     int32   messageLength; // total message size, including this
 *     int32   requestID;     // identifier for this message
 *     int32   responseTo;    // requestID from the original request
 *                            //   (used in reponses from db)
 *     int32   opCode;        // request type - see table below
 * }
 * </code>
 * </pre>
 *
 * @api.no This class is <b>NOT</b> part of the drivers API. This class may be
 *         mutated in incompatible ways between any two releases of the driver.
 * @copyright 2011-2013, Allanbank Consulting, Inc., All Rights Reserved
 */
public class Header {

    /** The size of a message header. */
    public static final int SIZE = 16;

    /**
     * The length of the message in bytes. This includes the length of the
     * header (16 bytes).
     */
    private final int myLength;

    /** The operation for the message. */
    private final Operation myOperation;

    /** The request id for the message. */
    private final int myRequestId;

    /** The response id for the message. */
    private final int myResponseId;

    /**
     * Creates a new header.
     *
     * @param in
     *            The stream to read the header from.
     * @throws IOException
     *             On a failure reading the header.
     */
    public Header(final BsonInputStream in) throws IOException {
        myLength = in.readInt();
        myRequestId = in.readInt();
        myResponseId = in.readInt();
        myOperation = Operation.fromCode(in.readInt());
    }

    /**
     * Creates a new header.
     *
     * @param length
     *            The length of the message in bytes.
     * @param requestId
     *            The request id for the message.
     * @param responseId
     *            The response id for the message.
     * @param operation
     *            The operation for the message.
     */
    public Header(final int length, final int requestId, final int responseId,
            final Operation operation) {
        myLength = length;
        myRequestId = requestId;
        myResponseId = responseId;
        myOperation = operation;
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
            final Header other = (Header) object;

            result = (myLength == other.myLength)
                    && (myOperation == other.myOperation)
                    && (myRequestId == other.myRequestId)
                    && (myResponseId == other.myResponseId);
        }
        return result;
    }

    /**
     * Returns the length of the message in bytes. This includes the
     * {@link #SIZE} of the header.
     *
     * @return The length of the message in bytes.
     */
    public int getLength() {
        return myLength;
    }

    /**
     * Returns the operation for the message.
     *
     * @return The operation for the message.
     */
    public Operation getOperation() {
        return myOperation;
    }

    /**
     * Returns the request id for the message.
     *
     * @return The request id for the message.
     */
    public int getRequestId() {
        return myRequestId;
    }

    /**
     * Returns the response id for the message.
     *
     * @return The response id for the message.
     */
    public int getResponseId() {
        return myResponseId;
    }

    /**
     * Computes a reasonable hash code.
     *
     * @return The hash code value.
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = (31 * result) + myLength;
        result = (31 * result) + myRequestId;
        result = (31 * result) + myResponseId;
        result = (31 * result) + myOperation.hashCode();
        return result;
    }
}
