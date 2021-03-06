/*
 * #%L
 * Server.java - mongodb-async-driver - Allanbank Consulting, Inc.
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
package com.allanbank.mongodb.client.state;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.allanbank.mongodb.Version;
import com.allanbank.mongodb.bson.Document;
import com.allanbank.mongodb.bson.Element;
import com.allanbank.mongodb.bson.NumericElement;
import com.allanbank.mongodb.bson.builder.BuilderFactory;
import com.allanbank.mongodb.bson.element.BooleanElement;
import com.allanbank.mongodb.bson.element.DocumentElement;
import com.allanbank.mongodb.bson.element.StringElement;
import com.allanbank.mongodb.bson.element.TimestampElement;
import com.allanbank.mongodb.client.Client;
import com.allanbank.mongodb.util.ServerNameUtils;

/**
 * Server provides tracking of the state of a single MongoDB server.
 *
 * @api.no This class is <b>NOT</b> part of the drivers API. This class may be
 *         mutated in incompatible ways between any two releases of the driver.
 * @copyright 2013, Allanbank Consulting, Inc., All Rights Reserved
 */
public class Server {

    /** The name for the Server's canonical name property: '{@value} '. */
    public static final String CANONICAL_NAME_PROP = "canonicalName";

    /** The decay rate for the exponential average for the latency. */
    public static final double DECAY_ALPHA;

    /** The decay period (number of samples) for the average latency. */
    public static final double DECAY_SAMPLES = 1000.0D;

    /** The default MongoDB port. */
    public static final int DEFAULT_PORT = ServerNameUtils.DEFAULT_PORT;

    /** The document element type. */
    public static final Class<DocumentElement> DOCUMENT_TYPE = DocumentElement.class;

    /** The default number of max batched write operations. */
    public static final int MAX_BATCHED_WRITE_OPERATIONS_DEFAULT = 1000;

    /** The name for the Server's maximum BSON object size property: {@value} . */
    public static final String MAX_BATCHED_WRITE_OPERATIONS_PROP = "maxWriteBatchSize";

    /** The name for the Server's maximum BSON object size property: {@value} . */
    public static final String MAX_BSON_OBJECT_SIZE_PROP = "maxBsonObjectSize";

    /** The numeric element type. */
    public static final Class<NumericElement> NUMERIC_TYPE = NumericElement.class;

    /** The value for a primary server's state. */
    public static final int PRIMARY_STATE = 1;

    /** The value for a secondary (actively replicating) server's state. */
    public static final int SECONDARY_STATE = 2;

    /** The name for the Server's state property: {@value} . */
    public static final String STATE_PROP = "state";

    /** The string element type. */
    public static final Class<StringElement> STRING_TYPE = StringElement.class;

    /** The name for the Server's tags property: {@value} . */
    public static final String TAGS_PROP = "tags";

    /** The timestamp element type. */
    public static final Class<TimestampElement> TIMESTAMP_TYPE = TimestampElement.class;

    /** The name for the Server's version property: {@value} . */
    public static final String VERSION_PROP = "version";

    /** The number of nano-seconds per milli-second. */
    private static final double NANOS_PER_MILLI = TimeUnit.MILLISECONDS
            .toNanos(1);

    static {
        DECAY_ALPHA = (2.0D / (DECAY_SAMPLES + 1));
    }

    /**
     * Tracks the average latency for the server connection. This is set when
     * the connection to the server is first created and then updated
     * periodically using an exponential moving average.
     */
    private volatile double myAverageLatency;

    /**
     * The socket address provided by the user. This address will not be
     * updated.
     */
    private final InetSocketAddress myCanonicalAddress;

    /**
     * The host name for the {@link #myCanonicalAddress}. This is use to
     * re-resolve the IP address when a connection failure is experienced.
     */
    private final String myCanonicalHostName;

    /** The normalized name of the server being tracked. */
    private volatile String myCanonicalName;

    /** Provides support for the sending of property change events. */
    private final PropertyChangeSupport myEventSupport;

    /** The time of the last version update. */
    private long myLastVersionUpdate = 0;

    /**
     * The maximum number of write operations allowed in a single write command.
     * Defaults to {@value #MAX_BATCHED_WRITE_OPERATIONS_DEFAULT}.
     */
    private volatile int myMaxBatchedWriteOperations = MAX_BATCHED_WRITE_OPERATIONS_DEFAULT;

    /**
     * The maximum BSON object size the server will accept. Defaults to
     * {@link Client#MAX_DOCUMENT_SIZE}.
     */
    private volatile int myMaxBsonObjectSize = Client.MAX_DOCUMENT_SIZE;

    /**
     * Tracks the last report of how many seconds the server is behind the
     * primary.
     */
    private volatile double mySecondsBehind;

    /** Tracking the state of the server. */
    private volatile State myState;

    /** Tracking the tags for the server. */
    private volatile Document myTags;

    /** The version of the server. */
    private Version myVersion;

    /**
     * The socket address being actively used. This will be re-created using the
     * server's hostname if a connection attempt fails.
     */
    private volatile InetSocketAddress myWorkingAddress;

    /**
     * Creates a new {@link Server}. Package private to force creation through
     * the {@link Cluster}.
     *
     * @param server
     *            The server being tracked.
     */
    /* package */Server(final InetSocketAddress server) {
        myCanonicalAddress = server;
        myCanonicalHostName = server.getHostName();
        myCanonicalName = ServerNameUtils.normalize(server);
        myWorkingAddress = myCanonicalAddress;

        myEventSupport = new PropertyChangeSupport(this);

        myState = State.UNKNOWN;
        myAverageLatency = Double.MAX_VALUE;
        mySecondsBehind = Double.MAX_VALUE;
        myTags = null;

        myVersion = Version.UNKNOWN;
    }

    /**
     * Add a PropertyChangeListener to receive all future property changes for
     * the {@link Server}.
     *
     * @param listener
     *            The PropertyChangeListener to be added
     *
     * @see PropertyChangeSupport#addPropertyChangeListener(PropertyChangeListener)
     */
    public void addListener(final PropertyChangeListener listener) {
        myEventSupport.addPropertyChangeListener(listener);

    }

    /**
     * Notification that an attempt to connect to the server via the all of the
     * {@link #getAddresses() addresses provided} failed.
     */
    public void connectFailed() {
        final State oldValue = myState;

        myWorkingAddress = null;
        myState = State.UNAVAILABLE;

        myEventSupport.firePropertyChange(STATE_PROP, oldValue, myState);
    }

    /**
     * Notification that a connection has closed normally. This will leave the
     * connection in the last known state even if it is the last open
     * connection.
     */
    public void connectionClosed() {
        // Nothing for now....
    }

    /**
     * Notification that a connection was successfully opened to the server. The
     * {@link InetSocketAddress} provided becomes the preferred address to use
     * when connecting to the server.
     *
     * @param addressUsed
     *            The address that was used to connect to the server.
     */
    public void connectionOpened(final InetSocketAddress addressUsed) {
        myWorkingAddress = addressUsed;
    }

    /**
     * Notification that a connection has closed abruptly. This will normally
     * transition the connection to an unknown state.
     */
    public void connectionTerminated() {
        final State oldValue = myState;

        myWorkingAddress = null;
        myState = State.UNAVAILABLE;

        myEventSupport.firePropertyChange(STATE_PROP, oldValue, myState);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return a stable equality check. This is based only on the
     * server object's identity. The {@link Cluster} class will de-duplicate
     * once the canonical host names are determined.
     * </p>
     */
    @Override
    public boolean equals(final Object object) {
        return (this == object);
    }

    /**
     * Returns the address of the server being tracked.
     *
     * @return The address of the server being tracked.
     */
    public Collection<InetSocketAddress> getAddresses() {
        if (myWorkingAddress == null) {
            myWorkingAddress = InetSocketAddress.createUnresolved(
                    myCanonicalHostName, myCanonicalAddress.getPort());
        }

        if (myCanonicalAddress == myWorkingAddress) {
            return Collections.singleton(myCanonicalAddress);
        }
        return Arrays.asList(myWorkingAddress, myCanonicalAddress);
    }

    /**
     * Returns the current average latency (in milliseconds) seen in issuing
     * requests to the server. If the latency returns {@link Double#MAX_VALUE}
     * then we have no basis for determining the latency.
     * <p>
     * This average is over the recent replies not over all replies received.
     * </p>
     *
     * @return The current average latency (in milliseconds) seen in issuing
     *         requests to the server.
     */
    public double getAverageLatency() {
        return myAverageLatency;
    }

    /**
     * Returns the name of the server as reported by the server itself.
     *
     * @return The name of the server as reported by the server itself.
     */
    public String getCanonicalName() {
        return myCanonicalName;
    }

    /**
     * Returns the maximum number of write operations allowed in a single write
     * command. Defaults to {@value #MAX_BATCHED_WRITE_OPERATIONS_DEFAULT}.
     *
     * @return The maximum number of write operations allowed in a single write
     *         command.
     */
    public int getMaxBatchedWriteOperations() {
        return myMaxBatchedWriteOperations;
    }

    /**
     * Returns the maximum BSON object size the server will accept. Defaults to
     * {@link Client#MAX_DOCUMENT_SIZE}.
     *
     * @return The maximum BSON object size the server will accept.
     */
    public int getMaxBsonObjectSize() {
        return myMaxBsonObjectSize;
    }

    /**
     * Sets the last reported seconds behind the primary.
     *
     * @return The seconds behind the primary server.
     */
    public double getSecondsBehind() {
        return mySecondsBehind;
    }

    /**
     * Returns the state value.
     *
     * @return The state value.
     */
    public State getState() {
        return myState;
    }

    /**
     * Returns the tags for the server.
     *
     * @return The tags for the server.
     */
    public Document getTags() {
        return myTags;
    }

    /**
     * Returns the version of the server.
     *
     * @return The version of the server.
     */
    public Version getVersion() {
        return myVersion;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return a stable hash for the server. This is based only on
     * the server object's {@link System#identityHashCode(Object) identity hash
     * code}. The {@link Cluster} class will de-duplicate once the canonical
     * host names are determined.
     * </p>
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Returns true if the server can be written to, false otherwise.
     * <p>
     * If writable it might be a standalone server, the primary in a replica
     * set, or a mongos in a sharded configuration. If not writable it is a
     * secondary server in a replica set.
     * </p>
     *
     * @return True if the server can be written to, false otherwise.
     */
    public boolean isWritable() {
        return (myState == State.WRITABLE);
    }

    /**
     * Returns true if there has not been a recent update to the server's
     * version or maximum document size.
     *
     * @return True if there has not been a recent update to the server's
     *         version or maximum document size.
     */
    public boolean needBuildInfo() {
        final long now = System.currentTimeMillis();
        final long tenMinutesAgo = now - TimeUnit.MINUTES.toMillis(10);

        return Version.UNKNOWN.equals(myVersion)
                || (myLastVersionUpdate < tenMinutesAgo);
    }

    /**
     * Remove a PropertyChangeListener to stop receiving future property changes
     * for the {@link Server}.
     *
     * @param listener
     *            The PropertyChangeListener to be removed
     *
     * @see PropertyChangeSupport#removePropertyChangeListener(PropertyChangeListener)
     */
    public void removeListener(final PropertyChangeListener listener) {
        myEventSupport.removePropertyChangeListener(listener);
    }

    /**
     * Notification that a status request message on the connection failed.
     * <p>
     * In the case of an exception the seconds behind is set to
     * {@link Integer#MAX_VALUE}. The value is configurable as a long so in
     * theory a user can ignore this case using a large
     * {@link com.allanbank.mongodb.MongoClientConfiguration#setMaxSecondaryLag(long)}
     * .
     * </p>
     */
    public void requestFailed() {
        mySecondsBehind = Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to to return a human readable version of the server state.
     * </p>
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append(getCanonicalName());
        builder.append("(");
        builder.append(myState);
        builder.append(",");
        if (myTags != null) {
            builder.append("T,");
        }
        builder.append(getAverageLatency());
        builder.append(")");

        return builder.toString();
    }

    /**
     * Updates the state of the server based on the document provided. The
     * document should be the reply to either a {@code ismaster} or
     * {@code replSetGetStatus} command.
     *
     * @param document
     *            The document with the state of the server.
     */
    public void update(final Document document) {
        updateState(document);
        updateSecondsBehind(document);
        updateTags(document);
        updateName(document);
        updateVersion(document);
        updateMaxBsonObjectSize(document);
        updateMaxWriteOperations(document);
    }

    /**
     * Updates the average latency (in nano-seconds) for the server.
     *
     * @param latencyNanoSeconds
     *            The latency seen sending a request and receiving a reply from
     *            the server.
     */
    public void updateAverageLatency(final long latencyNanoSeconds) {

        final double latency = latencyNanoSeconds / NANOS_PER_MILLI;
        final double oldAverage = myAverageLatency;
        if (Double.MAX_VALUE == oldAverage) {
            myAverageLatency = latency;
            if (mySecondsBehind == Double.MAX_VALUE) {
                mySecondsBehind = 0.0;
            }
        }
        else {
            myAverageLatency = (DECAY_ALPHA * latency)
                    + ((1.0D - DECAY_ALPHA) * oldAverage);
        }
    }

    /**
     * Extract any {@code maxBsonObjectSize} from the reply.
     *
     * @param isMasterReply
     *            The reply to the {@code ismaster} command.
     */
    private void updateMaxBsonObjectSize(final Document isMasterReply) {
        final int oldValue = myMaxBsonObjectSize;

        final NumericElement maxSize = isMasterReply.findFirst(NUMERIC_TYPE,
                MAX_BSON_OBJECT_SIZE_PROP);
        if (maxSize != null) {
            myMaxBsonObjectSize = maxSize.getIntValue();
        }

        myEventSupport.firePropertyChange(MAX_BSON_OBJECT_SIZE_PROP, oldValue,
                myMaxBsonObjectSize);
    }

    /**
     * Extract any {@code maxWriteBatchSize} from the reply.
     *
     * @param isMasterReply
     *            The reply to the {@code ismaster} command.
     */
    private void updateMaxWriteOperations(final Document isMasterReply) {
        final int oldValue = myMaxBatchedWriteOperations;

        final NumericElement maxSize = isMasterReply.findFirst(NUMERIC_TYPE,
                MAX_BATCHED_WRITE_OPERATIONS_PROP);
        if (maxSize != null) {
            myMaxBatchedWriteOperations = maxSize.getIntValue();
        }

        myEventSupport.firePropertyChange(MAX_BATCHED_WRITE_OPERATIONS_PROP,
                oldValue, myMaxBatchedWriteOperations);
    }

    /**
     * Updates the canonical name for the server based on the response to the
     * {@code ismaster} command.
     *
     * @param isMasterReply
     *            The reply to the {@code ismaster} command.
     */
    private void updateName(final Document isMasterReply) {
        final String oldValue = myCanonicalName;

        final Element element = isMasterReply.findFirst("me");
        if (element != null) {
            final String name = element.getValueAsString();
            if ((name != null) && !myCanonicalName.equals(name)) {
                myCanonicalName = name;
            }
        }

        myEventSupport.firePropertyChange(CANONICAL_NAME_PROP, oldValue,
                myCanonicalName);
    }

    /**
     * Extract the number of seconds this Server is behind the primary by
     * comparing its latest optime with that of the absolute latest optime.
     * <p>
     * To account for idle servers we use the optime for each server and assign
     * a value of zero to the "latest" optime and then subtract the remaining
     * servers from that optime.
     * </p>
     * <p>
     * Lastly, the state of the server is also checked and the seconds behind is
     * set to {@link Double#MAX_VALUE} if not in the primary (
     * {@value #PRIMARY_STATE}) or secondary ({@value #SECONDARY_STATE}).
     * </p>
     *
     * @param replicaStateDoc
     *            The document to extract the seconds behind from.
     */
    private void updateSecondsBehind(final Document replicaStateDoc) {
        final State oldValue = myState;

        final NumericElement state = replicaStateDoc.get(NUMERIC_TYPE,
                "myState");
        if (state != null) {
            final int value = state.getIntValue();
            if (value == PRIMARY_STATE) {
                myState = State.WRITABLE;
                mySecondsBehind = 0;
            }
            else if (value == SECONDARY_STATE) {
                myState = State.READ_ONLY;

                TimestampElement serverTimestamp = null;
                final StringElement expectedName = new StringElement("name",
                        myCanonicalName);
                for (final DocumentElement member : replicaStateDoc.find(
                        DOCUMENT_TYPE, "members", ".*")) {
                    if (expectedName.equals(member.get("name"))
                            && (member.get(TIMESTAMP_TYPE, "optimeDate") != null)) {

                        serverTimestamp = member.get(TIMESTAMP_TYPE,
                                "optimeDate");
                    }
                }

                if (serverTimestamp != null) {
                    TimestampElement latestTimestamp = serverTimestamp;
                    for (final TimestampElement time : replicaStateDoc.find(
                            TIMESTAMP_TYPE, "members", ".*", "optimeDate")) {
                        if (latestTimestamp.getTime() < time.getTime()) {
                            latestTimestamp = time;
                        }
                    }

                    final double msBehind = latestTimestamp.getTime()
                            - serverTimestamp.getTime();
                    mySecondsBehind = (msBehind / TimeUnit.SECONDS.toMillis(1));
                }
            }
            else {
                // "myState" != 1 and "myState" != 2
                mySecondsBehind = Double.MAX_VALUE;
                myState = State.UNAVAILABLE;
            }
        }

        myEventSupport.firePropertyChange(STATE_PROP, oldValue, myState);
    }

    /**
     * Extract the if the result implies that the server is writable.
     *
     * @param isMasterReply
     *            The document to extract the seconds behind from.
     */
    private void updateState(final Document isMasterReply) {
        final State oldValue = myState;

        BooleanElement element = isMasterReply.findFirst(BooleanElement.class,
                "ismaster");
        if (element != null) {
            if (element.getValue()) {
                myState = State.WRITABLE;
                mySecondsBehind = 0.0;
            }
            else {
                element = isMasterReply.findFirst(BooleanElement.class,
                        "secondary");
                if ((element != null) && element.getValue()) {
                    myState = State.READ_ONLY;
                    // Check the seconds behind for default values.
                    // This protects from not being able to get the replica set
                    // status due to permissions.
                    if ((mySecondsBehind == Double.MAX_VALUE)
                            || (mySecondsBehind == Integer.MAX_VALUE)) {
                        mySecondsBehind = 0.0;
                    }
                }
                else {
                    myState = State.UNAVAILABLE;
                }
            }
        }

        myEventSupport.firePropertyChange(STATE_PROP, oldValue, myState);
    }

    /**
     * Extract any tags from the reply.
     *
     * @param isMasterReply
     *            The reply to the {@code ismaster} command.
     */
    private void updateTags(final Document isMasterReply) {
        final Document oldValue = myTags;

        Document tags = isMasterReply.findFirst(DOCUMENT_TYPE, TAGS_PROP);
        if (tags != null) {
            // Strip to a pure Document from a DocumentElement.
            tags = BuilderFactory.start(tags.asDocument()).build();
            if (tags.getElements().isEmpty()) {
                myTags = null;
            }
            else if (!tags.equals(myTags)) {
                myTags = tags;
            }
        }

        myEventSupport.firePropertyChange(TAGS_PROP, oldValue, myTags);
    }

    /**
     * Extract any {@code versionArray} from the reply.
     *
     * @param buildInfoReply
     *            The reply to the {@code buildinfo} command.
     */
    private void updateVersion(final Document buildInfoReply) {
        final Version oldValue = myVersion;

        final List<NumericElement> versionElements = buildInfoReply.find(
                NUMERIC_TYPE, "versionArray", ".*");
        if (!versionElements.isEmpty()) {
            myVersion = Version.parse(versionElements);
            myLastVersionUpdate = System.currentTimeMillis();
        }
        else {
            // Use the String version if present.
            final StringElement stringVersion = buildInfoReply.findFirst(
                    STRING_TYPE, "version");
            if (stringVersion != null) {
                myVersion = Version.parse(stringVersion.getValue());
                myLastVersionUpdate = System.currentTimeMillis();
            }
            else {
                // Use the wire version if present.
                final NumericElement wireVersion = buildInfoReply.findFirst(
                        NUMERIC_TYPE, "maxWireVersion");
                if (wireVersion != null) {
                    final Version version = Version.forWireVersion(wireVersion
                            .getIntValue());

                    // Don't want to update the version if we are getting the
                    // value
                    // some other way since the wire protocol version requires
                    // interpretation and really just provides a "floor"
                    // version.
                    // Check for an unknown or lower version.
                    if (oldValue.equals(Version.UNKNOWN)
                            || (oldValue.compareTo(version) < 0)) {
                        myVersion = version;
                        // Don't update the myLastVersionUpdate time so we still
                        // try and get the precise version.
                    }
                }
            }
        }

        myEventSupport.firePropertyChange(VERSION_PROP, oldValue, myVersion);
    }

    /**
     * State provides the possible sttes for a server within the MongoDB
     * cluster.
     *
     * @api.no This class is <b>NOT</b> part of the drivers API. This class may
     *         be mutated in incompatible ways between any two releases of the
     *         driver.
     * @copyright 2013, Allanbank Consulting, Inc., All Rights Reserved
     */
    public enum State {
        /**
         * We can send reads to the server. It is running, we can connect to it
         * and is a secondary in the replica set.
         */
        READ_ONLY,

        /** We cannot connect to the server. */
        UNAVAILABLE,

        /**
         * A transient state for the server. We have either never connected to
         * the server or have lost all of the connections to the server.
         */
        UNKNOWN,

        /**
         * We can send writes to the server. It is running, we can connect to it
         * and is either a stand-alone instance, the primary in the replica set
         * or a mongos.
         */
        WRITABLE;
    }
}
