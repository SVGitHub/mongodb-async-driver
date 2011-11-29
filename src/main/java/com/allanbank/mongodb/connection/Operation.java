/*
 * Copyright 2011, Allanbank Consulting, Inc. 
 *           All Rights Reserved
 */
package com.allanbank.mongodb.connection;

/**
 * Enumeration of the possible operations allowed in MongoDB messages.
 * 
 * @copyright 2011, Allanbank Consulting, Inc., All Rights Reserved
 */
public enum Operation {
    /** Delete documents. */
    DELETE(2006),

    /** Get more data from a query. */
    GET_MORE(2005),

    /** Insert new document. */
    INSERT(2002),

    /** Tell database client is done with a cursor. */
    KILL_CURSORS(2007),

    /** Query a collection. */
    QUERY(2004),

    /** Reply to a client request. */
    REPLY(1),

    /** Update a document. */
    UPDATE(2001);

    /**
     * Returns the {@link Operation} for the provided opCode.
     * 
     * @param opCode
     *            The operation code for the {@link Operation}.
     * @return The {@link Operation} for the operation code or <code>null</code>
     *         if the operation code is invalid.
     */
    public static Operation fromCode(final int opCode) {
        if (opCode == REPLY.getCode()) {
            return REPLY;
        }
        else if (opCode == DELETE.getCode()) {
            return DELETE;
        }
        else if (opCode == GET_MORE.getCode()) {
            return GET_MORE;
        }
        else if (opCode == INSERT.getCode()) {
            return INSERT;
        }
        else if (opCode == KILL_CURSORS.getCode()) {
            return KILL_CURSORS;
        }
        else if (opCode == QUERY.getCode()) {
            return QUERY;
        }
        else if (opCode == UPDATE.getCode()) {
            return UPDATE;
        }
        return null;
    }

    /** The operation code. */
    private final int myCode;

    /**
     * Creates a new Operation.
     * 
     * @param code
     *            The operations code.
     */
    private Operation(final int code) {
        myCode = code;
    }

    /**
     * Returns the Operation's code.
     * 
     * @return The operation's code.
     */
    public int getCode() {
        return myCode;
    }
}
