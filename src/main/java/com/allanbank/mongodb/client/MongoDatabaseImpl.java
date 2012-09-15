/*
 * Copyright 2011-2012, Allanbank Consulting, Inc. 
 *           All Rights Reserved
 */

package com.allanbank.mongodb.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import com.allanbank.mongodb.Callback;
import com.allanbank.mongodb.ClosableIterator;
import com.allanbank.mongodb.MongoCollection;
import com.allanbank.mongodb.MongoDatabase;
import com.allanbank.mongodb.MongoDbException;
import com.allanbank.mongodb.ProfilingStatus;
import com.allanbank.mongodb.ReadPreference;
import com.allanbank.mongodb.bson.Document;
import com.allanbank.mongodb.bson.DocumentAssignable;
import com.allanbank.mongodb.bson.Element;
import com.allanbank.mongodb.bson.NumericElement;
import com.allanbank.mongodb.bson.builder.BuilderFactory;
import com.allanbank.mongodb.bson.builder.DocumentBuilder;
import com.allanbank.mongodb.bson.element.StringElement;
import com.allanbank.mongodb.connection.FutureCallback;
import com.allanbank.mongodb.connection.message.Command;
import com.allanbank.mongodb.connection.message.Query;
import com.allanbank.mongodb.util.FutureUtils;

/**
 * Implementation of the {@link MongoDatabase} interface.
 * 
 * @api.no This class is <b>NOT</b> part of the drivers API. This class may be
 *         mutated in incompatible ways between any two releases of the driver.
 * @copyright 2011-2012, Allanbank Consulting, Inc., All Rights Reserved
 */
public class MongoDatabaseImpl implements MongoDatabase {

    /** An empty query document. */
    public static final Document EMPTY_QUERY = BuilderFactory.start().build();

    /** The client for interacting with MongoDB. */
    protected final Client myClient;

    /** The 'admin' database. */
    private MongoDatabase myAdminDatabase;

    /** The name of the database we interact with. */
    private final String myName;

    /**
     * Create a new MongoDatabaseClient.
     * 
     * @param client
     *            The client for interacting with MongoDB.
     * @param name
     *            The name of the database we interact with.
     */
    public MongoDatabaseImpl(final Client client, final String name) {
        myClient = client;
        myName = name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Issues a command to create the collection with the specified name and
     * options.
     * </p>
     */
    @Override
    public boolean createCappedCollection(final String name, final long size)
            throws MongoDbException {
        return createCollection(name, BuilderFactory.start()
                .add("capped", true).add("size", size));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Issues a command to create the collection with the specified name and
     * options.
     * </p>
     */
    @Override
    public boolean createCollection(final String name,
            final DocumentAssignable options) throws MongoDbException {
        final Document result = runCommand("create", name, options);
        final NumericElement okElem = result.get(NumericElement.class, "ok");

        return ((okElem != null) && (okElem.getIntValue() > 0));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to issue a "dropDatabase" command.
     * </p>
     * 
     * @see MongoDatabase#drop()
     */
    @Override
    public boolean drop() {
        final Document result = runCommand("dropDatabase");
        final NumericElement okElem = result.get(NumericElement.class, "ok");

        return ((okElem != null) && (okElem.getIntValue() > 0));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to create a new {@link MongoCollectionImpl}.
     * </p>
     * 
     * @see MongoDatabase#getCollection(String)
     */
    @Override
    public MongoCollection getCollection(final String name) {
        return new MongoCollectionImpl(myClient, this, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return myName;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to query the system.namespace collection for the names of all
     * of the collections.
     * </p>
     * 
     * @see MongoDatabase#getProfilingStatus()
     */
    @Override
    public ProfilingStatus getProfilingStatus() throws MongoDbException {
        final Document result = runCommand("profile", -1, null);

        final NumericElement level = result.get(NumericElement.class, "was");
        final NumericElement millis = result
                .get(NumericElement.class, "slowms");

        if ((level != null) && (millis != null)) {
            final ProfilingStatus.Level l = ProfilingStatus.Level
                    .fromValue(level.getIntValue());
            if (l != null) {
                switch (l) {
                case NONE:
                    return ProfilingStatus.OFF;
                case ALL:
                    return ProfilingStatus.ON;
                case SLOW_ONLY:
                    return ProfilingStatus.slow(millis.getIntValue());
                }
            }
        }

        // undefined?
        return null;

    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to query the system.namespace collection for the names of all
     * of the collections.
     * </p>
     * 
     * @see MongoDatabase#listCollections()
     */
    @Override
    public List<String> listCollections() {
        final Query query = new Query(myName, "system.namespaces", EMPTY_QUERY,
        /* fields= */null,
        /* batchSize= */0, /* limit= */0, /* numberToSkip= */0,
        /* tailable= */false, ReadPreference.PRIMARY,
        /* noCursorTimeout= */false, /* awaitData= */false,
        /* exhaust= */false, /* partial= */false);

        final FutureCallback<ClosableIterator<Document>> iterFuture = new FutureCallback<ClosableIterator<Document>>();
        final QueryCallback callback = new QueryCallback(myClient, query,
                iterFuture);

        final String addr = myClient.send(query, callback);
        callback.setAddress(addr);

        final List<String> names = new ArrayList<String>();
        final Iterator<Document> iter = FutureUtils.unwrap(iterFuture);
        while (iter.hasNext()) {
            final Document collection = iter.next();
            for (final StringElement nameElement : collection.find(
                    StringElement.class, "name")) {
                final String name = nameElement.getValue();
                if ((name.indexOf('$') >= 0) && (name.indexOf(".oplog.$") < 0)) {
                    continue;
                }

                names.add(name.substring(myName.length() + 1));
            }
        }

        return names;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call {@link #runCommand(String)} on the 'admin' database.
     * </p>
     * 
     * @see #runCommandAsync(String, DocumentAssignable)
     */
    @Override
    public Document runAdminCommand(final String command)
            throws MongoDbException {
        return getAdminDatabase().runCommand(command);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(String, DocumentAssignable)
     */
    @Override
    public Document runAdminCommand(final String command,
            final DocumentAssignable options) throws MongoDbException {
        return getAdminDatabase().runCommand(command, options);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, String, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(String, String, DocumentAssignable)
     */
    @Override
    public Document runAdminCommand(final String commandName,
            final String commandValue, final DocumentAssignable options)
            throws MongoDbException {
        return getAdminDatabase()
                .runCommand(commandName, commandValue, options);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, DocumentAssignable)} method with
     * <code>null</code> options.
     * </p>
     * 
     * @see #runCommandAsync(String, DocumentAssignable)
     */
    @Override
    public Document runCommand(final String command) throws MongoDbException {
        return FutureUtils.unwrap(runCommandAsync(command, null));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(String, DocumentAssignable)
     */
    @Override
    public Document runCommand(final String command,
            final DocumentAssignable options) throws MongoDbException {
        return FutureUtils.unwrap(runCommandAsync(command, options));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, int, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(String, int, DocumentAssignable)
     */
    @Override
    public Document runCommand(final String commandName,
            final int commandValue, final DocumentAssignable options)
            throws MongoDbException {
        return FutureUtils.unwrap(runCommandAsync(commandName, commandValue,
                options));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(String, String, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(String, String, DocumentAssignable)
     */
    @Override
    public Document runCommand(final String commandName,
            final String commandValue, final DocumentAssignable options)
            throws MongoDbException {
        return FutureUtils.unwrap(runCommandAsync(commandName, commandValue,
                options));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(Callback, String, DocumentAssignable)} method
     * with <code>null</code> for the options.
     * </p>
     * 
     * @see #runCommandAsync(Callback, String, DocumentAssignable)
     */
    @Override
    public void runCommandAsync(final Callback<Document> reply,
            final String command) throws MongoDbException {
        runCommandAsync(reply, command, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to build a {@link Command} message and send it to the server.
     * </p>
     */
    @Override
    public void runCommandAsync(final Callback<Document> reply,
            final String command, final DocumentAssignable options)
            throws MongoDbException {
        final DocumentBuilder builder = BuilderFactory.start();
        builder.addInteger(command, 1);
        if (options != null) {
            for (final Element element : options.asDocument()) {
                if (!command.equals(element.getName())) {
                    builder.add(element);
                }
            }
        }

        final Command commandMessage = new Command(myName, builder.build());

        myClient.send(commandMessage, new ReplyCommandCallback(reply));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to build a {@link Command} message and send it to the server.
     * </p>
     */
    @Override
    public void runCommandAsync(final Callback<Document> reply,
            final String commandName, final int commandValue,
            final DocumentAssignable options) throws MongoDbException {
        final DocumentBuilder builder = BuilderFactory.start();
        builder.add(commandName, commandValue);
        if (options != null) {
            for (final Element element : options.asDocument()) {
                if (!commandName.equals(element.getName())) {
                    builder.add(element);
                }
            }
        }

        final Command commandMessage = new Command(myName, builder.build());

        myClient.send(commandMessage, new ReplyCommandCallback(reply));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to build a {@link Command} message and send it to the server.
     * </p>
     */
    @Override
    public void runCommandAsync(final Callback<Document> reply,
            final String commandName, final String commandValue,
            final DocumentAssignable options) throws MongoDbException {
        final DocumentBuilder builder = BuilderFactory.start();
        builder.add(commandName, commandValue);
        if (options != null) {
            for (final Element element : options.asDocument()) {
                if (!commandName.equals(element.getName())) {
                    builder.add(element);
                }
            }
        }

        final Command commandMessage = new Command(myName, builder.build());

        myClient.send(commandMessage, new ReplyCommandCallback(reply));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(Callback, String, DocumentAssignable)} method
     * with <code>null</code> options.
     * </p>
     * 
     * @see #runCommandAsync(Callback, String, DocumentAssignable)
     */
    @Override
    public Future<Document> runCommandAsync(final String command)
            throws MongoDbException {
        final FutureCallback<Document> future = new FutureCallback<Document>();

        runCommandAsync(future, command, null);

        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(Callback, String, DocumentAssignable)} method.
     * </p>
     * 
     * @see #runCommandAsync(Callback, String, DocumentAssignable)
     */
    @Override
    public Future<Document> runCommandAsync(final String command,
            final DocumentAssignable options) throws MongoDbException {
        final FutureCallback<Document> future = new FutureCallback<Document>();

        runCommandAsync(future, command, options);

        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(Callback, String, int, DocumentAssignable)}
     * method.
     * </p>
     * 
     * @see #runCommandAsync(Callback, String, int, DocumentAssignable)
     */
    @Override
    public Future<Document> runCommandAsync(final String commandName,
            final int commandValue, final DocumentAssignable options)
            throws MongoDbException {
        final FutureCallback<Document> future = new FutureCallback<Document>();

        runCommandAsync(future, commandName, commandValue, options);

        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to call the
     * {@link #runCommandAsync(Callback, String, String, DocumentAssignable)}
     * method.
     * </p>
     * 
     * @see #runCommandAsync(Callback, String, String, DocumentAssignable)
     */
    @Override
    public Future<Document> runCommandAsync(final String commandName,
            final String commandValue, final DocumentAssignable options)
            throws MongoDbException {
        final FutureCallback<Document> future = new FutureCallback<Document>();

        runCommandAsync(future, commandName, commandValue, options);

        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to update the databases profile level.
     * </p>
     * 
     * @see MongoDatabase#setProfilingStatus
     */
    @Override
    public boolean setProfilingStatus(final ProfilingStatus profileLevel)
            throws MongoDbException {
        final Document result = runCommand(
                "profile",
                profileLevel.getLevel().getValue(),
                BuilderFactory.start().add("slowms",
                        profileLevel.getSlowMillisThreshold()));

        final NumericElement level = result.get(NumericElement.class, "was");
        final NumericElement millis = result
                .get(NumericElement.class, "slowms");

        if ((level != null) && (millis != null)) {
            final ProfilingStatus.Level l = ProfilingStatus.Level
                    .fromValue(level.getIntValue());
            if (l != null) {
                switch (l) {
                case NONE:
                    return !ProfilingStatus.Level.NONE.equals(profileLevel
                            .getLevel());
                case ALL:
                    return !ProfilingStatus.Level.ALL.equals(profileLevel
                            .getLevel());
                case SLOW_ONLY:
                    final ProfilingStatus before = ProfilingStatus.slow(millis
                            .getIntValue());
                    return !before.equals(profileLevel);
                }
            }
        }

        // From undefined to defined is a change?
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to send a {@code dbStats} command to the MongoDB server.
     * </p>
     * 
     * @see MongoDatabase#stats
     */
    @Override
    public Document stats() throws MongoDbException {
        return runCommand("dbStats");
    }

    /**
     * Returns a {@link MongoDatabase} interface to the 'admin' database.
     * 
     * @return A reference to a {@link MongoDatabase} for interacting with the
     *         'admin' database.
     */
    private MongoDatabase getAdminDatabase() {
        if (myAdminDatabase == null) {
            if (myName.equals("admin")) {
                myAdminDatabase = this;
            }
            else {
                myAdminDatabase = new MongoDatabaseImpl(myClient, "admin");
            }
        }

        return myAdminDatabase;
    }
}
