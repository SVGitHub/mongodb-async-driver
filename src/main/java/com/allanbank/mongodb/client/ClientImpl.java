/*
 * Copyright 2011-2012, Allanbank Consulting, Inc. 
 *           All Rights Reserved
 */

package com.allanbank.mongodb.client;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.allanbank.mongodb.Durability;
import com.allanbank.mongodb.MongoClientConfiguration;
import com.allanbank.mongodb.MongoDbException;
import com.allanbank.mongodb.ReadPreference;
import com.allanbank.mongodb.connection.ClusterType;
import com.allanbank.mongodb.connection.Connection;
import com.allanbank.mongodb.connection.ConnectionFactory;
import com.allanbank.mongodb.connection.Message;
import com.allanbank.mongodb.connection.ReconnectStrategy;
import com.allanbank.mongodb.connection.bootstrap.BootstrapConnectionFactory;
import com.allanbank.mongodb.error.CannotConnectException;
import com.allanbank.mongodb.error.ConnectionLostException;
import com.allanbank.mongodb.util.IOUtils;

/**
 * Implementation of the internal {@link Client} interface which all requests to
 * the MongoDB servers pass.
 * 
 * @api.no This class is <b>NOT</b> part of the drivers API. This class may be
 *         mutated in incompatible ways between any two releases of the driver.
 * @copyright 2011-2012, Allanbank Consulting, Inc., All Rights Reserved
 */
public class ClientImpl extends AbstractClient {

    /**
     * The maximum number of connections to scan looking for idle/lightly used
     * connections.
     */
    public static final int MAX_CONNECTION_SCAN = 5;

    /** The logger for the {@link ClientImpl}. */
    protected static final Logger LOG = Logger.getLogger(ClientImpl.class
            .getCanonicalName());

    /** Counter for the number of reconnects currently being attempted. */
    private int myActiveReconnects;

    /** The configuration for interacting with MongoDB. */
    private final MongoClientConfiguration myConfig;

    /** Factory for creating connections to MongoDB. */
    private final ConnectionFactory myConnectionFactory;

    /** The listener for changes to the state of connections. */
    private final PropertyChangeListener myConnectionListener;

    /** The set of open connections. */
    private final List<Connection> myConnections;

    /** The set of open connections. */
    private final BlockingQueue<Connection> myConnectionsToClose;

    /** The sequence of the connection that was last used. */
    private final AtomicInteger myNextConnectionSequence = new AtomicInteger(0);

    /**
     * Create a new ClientImpl.
     * 
     * @param config
     *            The configuration for interacting with MongoDB.
     */
    public ClientImpl(final MongoClientConfiguration config) {
        this(config, new BootstrapConnectionFactory(config));
    }

    /**
     * Create a new ClientImpl.
     * 
     * @param config
     *            The configuration for interacting with MongoDB.
     * @param connectionFactory
     *            The source of connection for the client.
     */
    public ClientImpl(final MongoClientConfiguration config,
            final ConnectionFactory connectionFactory) {
        myConfig = config;
        myConnectionFactory = connectionFactory;
        myConnections = new CopyOnWriteArrayList<Connection>();
        myConnectionsToClose = new LinkedBlockingQueue<Connection>();
        myConnectionListener = new ConnectionListener();
        myActiveReconnects = 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to close all of the open connections.
     * </p>
     * 
     * @see Closeable#close()
     */
    @Override
    public void close() {

        while (!myConnections.isEmpty()) {
            try {
                final Connection conn = myConnections.remove(0);
                myConnectionsToClose.add(conn);
                conn.shutdown();
            }
            catch (final ArrayIndexOutOfBoundsException aiob) {
                // There is a race between the isEmpty() and the remove we can't
                // avoid. Next check if isEmpty() will bounce us out of the
                // loop.
                aiob.getCause(); // Shhhh - PMD.
            }
        }

        // Work off the connections to close until they are all closed.
        final List<Connection> conns = new ArrayList<Connection>(
                myConnectionsToClose);
        for (final Connection conn : conns) {
            conn.waitForClosed(myConfig.getReadTimeout(), TimeUnit.MILLISECONDS);
            if (conn.isOpen()) {
                // Force the connection to close.
                close(conn);
            }
        }

        // Shutdown the connections factory.
        IOUtils.close(myConnectionFactory);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return the {@link ClusterType} of the
     * {@link ConnectionFactory}.
     * </p>
     */
    @Override
    public ClusterType getClusterType() {
        return myConnectionFactory.getClusterType();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return the configuration used when the client was
     * constructed.
     * </p>
     */
    @Override
    public MongoClientConfiguration getConfig() {
        return myConfig;
    }

    /**
     * Returns the current number of open connections.
     * 
     * @return The current number of open connections.
     */
    public int getConnectionCount() {
        return myConnections.size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return the configurations default durability.
     * </p>
     * 
     * @see Client#getDefaultDurability()
     */
    @Override
    public Durability getDefaultDurability() {
        return myConfig.getDefaultDurability();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return the configurations default read preference.
     * </p>
     * 
     * @see Client#getDefaultReadPreference()
     */
    @Override
    public ReadPreference getDefaultReadPreference() {
        return myConfig.getDefaultReadPreference();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tries to locate a connection that can quickly dispatch the message to a
     * MongoDB server. The basic metrics for determining if a connection is idle
     * is to look at the number of messages waiting to be sent. The basic logic
     * for finding a connection is:
     * <ol>
     * <li>Scan the list of connection looking for an idle connection. If one is
     * found use it.</li>
     * <li>If there are no idle connections determine the maximum number of
     * allowed connections and if there are fewer that the maximum allowed then
     * take the connection creation lock, create a new connection, use it, and
     * add to the set of available connections and release the lock.</li>
     * <li>If there are is still not a connection idle then sort the connections
     * based on a snapshot of pending messages and use the connection with the
     * least messages.</li>
     * <ul>
     */
    @Override
    protected Connection findConnection(final Message message1,
            final Message message2) throws MongoDbException {
        // Make sure we shrink connections when the max changes.
        final int limit = Math.max(1, myConfig.getMaxConnectionCount());
        if (limit < myConnections.size()) {
            synchronized (myConnectionFactory) {
                // Mark the connections as persona non grata.
                while (limit < myConnections.size()) {
                    try {
                        final Connection conn = myConnections.remove(0);
                        myConnectionsToClose.add(conn);
                        conn.shutdown();
                    }
                    catch (final ArrayIndexOutOfBoundsException aiob) {
                        // Race between the size() and remove(0).
                        // Next loop should resolve.
                        aiob.getCause(); // Shhhh - PMD.
                    }
                }
            }
        }

        // Locate a connection to use.
        Connection conn = findIdleConnection();
        if (conn == null) {
            conn = tryCreateConnection();
            if (conn == null) {
                conn = findMostIdleConnection();
                if (conn == null) {
                    conn = waitForReconnect(message1, message2);
                }
            }
        }

        if (conn == null) {
            throw new CannotConnectException(
                    "Could not create a connection to the server.");
        }

        return conn;
    }

    /**
     * Tries to reconnect previously open {@link Connection}s. If a connection
     * was being closed then cleans up the remaining state.
     * 
     * @param connection
     *            The connection that was closed.
     */
    protected void handleConnectionClosed(final Connection connection) {
        // Look for the connection in the "active" set first.
        if (myConnections.contains(connection)) {
            // Attempt a reconnect.
            LOG.info("Unexpected MongoDB Connection closed: " + connection
                    + ". Will try to reconnect.");
            reconnect(connection);
        }
        else if (myConnectionsToClose.remove(connection)) {
            LOG.fine("MongoDB Connection closed: " + connection);
            connection.removePropertyChangeListener(myConnectionListener);
        }
        else {
            LOG.info("Unknown MongoDB Connection closed: " + connection);
            connection.removePropertyChangeListener(myConnectionListener);
        }
    }

    /**
     * Runs the reconnect logic for the connection.
     * 
     * @param connection
     *            The connection to reconnect.
     */
    protected void reconnect(final Connection connection) {
        final ReconnectStrategy strategy = myConnectionFactory
                .getReconnectStrategy();

        try {
            synchronized (this) {
                myActiveReconnects += 1;
            }

            // Raise errors for all of the pending messages - there is no way to
            // know their state of flight between here and the server.
            MongoDbException exception = new ConnectionLostException(
                    "Connection lost to MongoDB: " + connection);
            connection.raiseErrors(exception, false);

            final Connection newConnection = strategy.reconnect(connection);
            if (newConnection != null) {

                // Get the new connection in the rotation.
                myConnections.remove(connection);
                myConnections.add(newConnection);
                connection.removePropertyChangeListener(myConnectionListener);
            }
            else {
                // Reconnect failed.
                // Raise errors for all of the to be sent and pending messages.
                exception = new CannotConnectException(
                        "Could not reconnect to MongoDB.");
                connection.raiseErrors(exception, true);
                myConnections.remove(connection);
            }
        }
        finally {
            synchronized (this) {
                myActiveReconnects -= 1;
                notifyAll();
            }
        }
    }

    /**
     * Silently closes the connection.
     * 
     * @param conn
     *            The connection to close.
     */
    private void close(final Connection conn) {
        try {
            conn.close();
        }
        catch (final IOException ioe) {
            LOG.log(Level.WARNING, "Error closing connection to MongoDB: "
                    + conn, ioe);
        }
        finally {
            myConnections.remove(conn);
            myConnectionsToClose.remove(conn);

            conn.removePropertyChangeListener(myConnectionListener);
        }
    }

    /**
     * Tries to find an idle connection to use from up to the next
     * {@value #MAX_CONNECTION_SCAN} items.
     * 
     * @return The idle connection, if found.
     */
    private Connection findIdleConnection() {
        if (!myConnections.isEmpty()) {
            final int toScan = Math.min(myConnections.size(),
                    MAX_CONNECTION_SCAN);
            for (int loop = 0; loop < toScan; ++loop) {
                final int size = myConnections.size();
                final long sequence = Math.abs((long) myNextConnectionSequence
                        .getAndIncrement()); // Cast up to long to make sure abs
                                             // works.
                final int index = (int) (sequence % size);

                try {
                    final Connection conn = myConnections.get(index);
                    if (conn.isOpen() && (conn.getPendingCount() == 0)) {
                        return conn;
                    }
                }
                catch (final ArrayIndexOutOfBoundsException aiob) {
                    // Race between the size and get.
                    // Next loop should fix.
                    aiob.getCause(); // Shhh - PMD.
                }
            }
        }

        return null;
    }

    /**
     * Locates the most idle connection to use from up to the next
     * {@value #MAX_CONNECTION_SCAN} items.
     * 
     * @return The most idle connection.
     */
    private Connection findMostIdleConnection() {
        if (!myConnections.isEmpty()) {
            final SortedMap<Integer, Connection> connections = new TreeMap<Integer, Connection>();
            final int toScan = Math.min(myConnections.size(),
                    MAX_CONNECTION_SCAN);
            for (int loop = 0; loop < toScan; ++loop) {
                final int size = myConnections.size();
                final long sequence = Math.abs((long) myNextConnectionSequence
                        .getAndIncrement());
                final int index = (int) (sequence % size);

                try {
                    final Connection conn = myConnections.get(index);
                    if (conn.isOpen()) {
                        connections.put(
                                Integer.valueOf(conn.getPendingCount()), conn);
                    }
                }
                catch (final ArrayIndexOutOfBoundsException aiob) {
                    // Race between the size and get.
                    // Next loop should fix.
                    aiob.getCause(); // Shhh - PMD.
                }
            }

            if (!connections.isEmpty()) {
                return connections.get(connections.firstKey());
            }
        }

        return null;
    }

    /**
     * Tries to create a new connection.
     * 
     * @return The created connection or null if a connection could not be
     *         created by policy or error.
     */
    private Connection tryCreateConnection() {
        if (myConnections.size() < myConfig.getMaxConnectionCount()) {
            synchronized (myConnectionFactory) {
                final int limit = Math.max(1, myConfig.getMaxConnectionCount());
                if (myConnections.size() < limit) {
                    try {
                        final Connection conn = myConnectionFactory.connect();

                        myConnections.add(conn);

                        // Add a listener for if the connection is closed.
                        conn.addPropertyChangeListener(myConnectionListener);

                        return conn;
                    }
                    catch (final IOException ioe) {
                        LOG.log(Level.WARNING,
                                "Could not create a connection.", ioe);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks if there is an active reconnect attempt on-going. If so waits for
     * it to finish (with a timeout) and then searches for a connection again.
     * 
     * @param message1
     *            The first message that will be sent. The connection return
     *            should be compatible with all of the messages
     *            {@link ReadPreference}.
     * @param message2
     *            The second message that will be sent. The connection return
     *            should be compatible with all of the messages
     *            {@link ReadPreference}. May be <code>null</code>.
     * @return The connection found after waiting or <code>null</code> if there
     *         was no active reconnect or there was still no connection.
     */
    private Connection waitForReconnect(final Message message1,
            final Message message2) {
        Connection conn = null;
        boolean wasReconnecting = false;
        synchronized (this) {
            wasReconnecting = (myActiveReconnects > 0);
            if (wasReconnecting) {
                long now = System.currentTimeMillis();
                final long deadline = (myConfig.getReconnectTimeout() <= 0) ? Long.MAX_VALUE
                        : now + myConfig.getReconnectTimeout();

                while (now < deadline) {
                    try {
                        LOG.fine("Waiting for reconnect to MongoDB.");
                        wait(deadline - now);

                        now = System.currentTimeMillis();
                    }
                    catch (final InterruptedException e) {
                        // Ignored - Handled by the loop.
                    }
                }
            }
        }

        if (wasReconnecting) {
            // Look again now that we may have reconnected.
            conn = findConnection(message1, message2);
        }
        return conn;
    }

    /**
     * ConnectionListener provides the call back for events occurring on a
     * connection.
     * 
     * @copyright 2012, Allanbank Consulting, Inc., All Rights Reserved
     */
    protected class ConnectionListener implements PropertyChangeListener {

        /**
         * Creates a new ConnectionListener.
         */
        public ConnectionListener() {
            super();
        }

        /**
         * {@inheritDoc}
         * <p>
         * Overridden to try reconnecting a connection that has closed.
         * </p>
         */
        @Override
        public void propertyChange(final PropertyChangeEvent event) {
            if (Connection.OPEN_PROP_NAME.equals(event.getPropertyName())
                    && Boolean.FALSE.equals(event.getNewValue())) {
                handleConnectionClosed((Connection) event.getSource());
            }
        }
    }
}
