/*
 * #%L
 * Credential.java - mongodb-async-driver - Allanbank Consulting, Inc.
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
package com.allanbank.mongodb;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

import com.allanbank.mongodb.client.connection.auth.Authenticator;
import com.allanbank.mongodb.client.connection.auth.MongoDbAuthenticator;
import com.allanbank.mongodb.client.connection.auth.ScramSha1Authenticator;
import com.allanbank.mongodb.client.connection.auth.X509Authenticator;
import com.allanbank.mongodb.error.MongoDbAuthenticationException;

/**
 * Credential provides an immutable set of credential for accessing MongoDB.
 * <p>
 * A given client can support a different set of credential for each database
 * the client is accessing. The client can also authenticate against the
 * {@value #ADMIN_DB} database which will apply across all databases.
 * </p>
 * <p>
 * <em>Note:</em> While we use the term user name/password within this class the
 * values may not actually be a user name or a password. In addition not all
 * authentication mechanisms may use all of the fields in this class. See the
 * documentation for the authenticator being used for details on what values are
 * expected for each of the fields in this class.
 * </p>
 *
 * @see <a
 *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication.html">Authentication
 *      Usage Guide</a>
 * @api.yes This class is part of the driver's API. Public and protected members
 *          will be deprecated for at least 1 non-bugfix release (version
 *          numbers are &lt;major&gt;.&lt;minor&gt;.&lt;bugfix&gt;) before being
 *          removed or modified.
 * @copyright 2013, Allanbank Consulting, Inc., All Rights Reserved
 */
@Immutable
@ThreadSafe
public final class Credential
        implements Serializable {

    /**
     * The name of the administration database used to authenticate a
     * administrator to MongoDB.
     */
    public static final String ADMIN_DB = "admin";

    /**
     * Constant for Kerberos authentication.
     * <p>
     * <em>Note:</em> Use of the Kerberos for authentication requires the
     * driver's extensions. See the <a href=
     * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/kerberos.html"
     * >Kerberos Usage Guide</a> for details.
     * </p>
     *
     * @see <a
     *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/kerberos.html">Kerberos
     *      Usage Guide</a>
     */
    public static final String KERBEROS;

    /** Constant for traditional MongoDB Challenge/Response. */
    public static final String MONGODB_CR;

    /** An empty password array. */
    public static final char[] NO_PASSWORD = new char[0];

    /**
     * Constant for authentication using plain SASL (LDAP/PAM) authentication.
     * <p>
     * <em>Note:</em> Use of Plain SASL for authentication requires the driver's
     * extensions. See the <a href=
     * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html"
     * >Plain SASL Usage Guide</a> for details.
     * </p>
     *
     * @see <a
     *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html">Plain
     *      SASL Usage Guide</a>
     */
    public static final String PLAIN_SASL;

    /**
     * Constant for authentication using SCRAM-SHA-1 SASL authentication based
     * on RFC 5802.
     * <p>
     * The extensions implementation contains a complete implementation of the
     * {@code saslPrep} algorithm for the username and password as specified by
     * RFC 4013 which is itself a profile for {@code stringprep} from RFC 3454.
     * This enables the use of non-ascii character sets within the username and
     * password.
     * </p>
     *
     * @see <a
     *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/scram-sha-1.html">SCRAM-SHA-1
     *      SASL Usage Guide</a>
     * @see <a href="http://tools.ietf.org/html/rfc5802">RFC 5802</a>
     * @see <a href="http://tools.ietf.org/html/rfc4013">RFC 4013</a>
     * @see <a href="http://tools.ietf.org/html/rfc3454">RFC 3454</a>
     */
    public static final String SCRAM_SHA_1;

    /**
     * Constant for authentication using x.509 client certificates passed at
     * connection establishment.
     *
     * @see <a
     *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/tls.html">TLS/SSL
     *      Usage Guide</a>
     * @see <a
     *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/x509.html">x.509
     *      Authentication Usage Guide</a>
     */
    public static final String X509;

    /** Serialization version of the class. */
    private static final long serialVersionUID = -6251469373336569336L;

    static {
        KERBEROS = "com.allanbank.mongodb.extensions.authentication.KerberosAuthenticator";
        MONGODB_CR = MongoDbAuthenticator.class.getName();
        PLAIN_SASL = "com.allanbank.mongodb.extensions.authentication.PlainSaslAuthenticator";
        SCRAM_SHA_1 = ScramSha1Authenticator.class.getName();
        X509 = X509Authenticator.class.getName();
    }

    /**
     * Creates a {@link Builder} for creating a {@link Credential}.
     *
     * @return The {@link Builder} for creating a {@link Credential}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The authentication type or mode that the credential should be used with.
     */
    private final String myAuthenticationType;

    /** The template authenticator for the credential. */
    private transient Authenticator myAuthenticator;

    /** The database the credential are valid for. */
    private final String myDatabase;

    /** The file containing the full credentials. */
    private final File myFile;

    /** Other options for the credentials. */
    private final Map<String, String> myOptions;

    /** The password for the credential set. */
    private final char[] myPassword;

    /** The user name for the credential set. */
    private final String myUserName;

    /**
     * Creates a new Credential.
     *
     * @param builder
     *            The builder for the credentials.
     */
    public Credential(final Builder builder) {
        myUserName = builder.myUserName;
        myDatabase = builder.myDatabase;
        myFile = builder.myFile;
        myAuthenticationType = builder.myAuthenticationType;
        myAuthenticator = builder.myAuthenticator;
        myPassword = builder.myPassword.clone();
        myOptions = Collections.unmodifiableMap(new HashMap<String, String>(
                builder.myOptions));
    }

    /**
     * Returns an authenticator for the credential.
     *
     * @return The authenticator for the credential.
     * @throws MongoDbAuthenticationException
     *             On a failure to load the authenticator for the credential.
     */
    public Authenticator authenticator() throws MongoDbAuthenticationException {
        if (myAuthenticator == null) {
            try {
                loadAuthenticator();
            }
            catch (final ClassNotFoundException e) {
                throw new MongoDbAuthenticationException(e);
            }
            catch (final InstantiationException e) {
                throw new MongoDbAuthenticationException(e);
            }
            catch (final IllegalAccessException e) {
                throw new MongoDbAuthenticationException(e);
            }
        }

        return myAuthenticator.clone();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to return true if the passed value equals these credential.
     * </p>
     */
    @Override
    public boolean equals(final Object object) {
        boolean result = false;
        if (this == object) {
            result = true;
        }
        else if ((object != null) && (getClass() == object.getClass())) {
            final Credential other = (Credential) object;

            result = nullSafeEquals(myAuthenticationType,
                    other.myAuthenticationType)
                    && nullSafeEquals(myDatabase, other.myDatabase)
                    && nullSafeEquals(myUserName, other.myUserName)
                    && nullSafeEquals(myFile, other.myFile)
                    && nullSafeEquals(myOptions, other.myOptions)
                    && Arrays.equals(myPassword, other.myPassword);
        }
        return result;
    }

    /**
     * Returns the authentication type or mode that the credential should be
     * used with.
     *
     * @return The authentication type or mode that the credential should be
     *         used with.
     */
    public String getAuthenticationType() {
        return myAuthenticationType;
    }

    /**
     * Returns the authenticator value.
     *
     * @return The authenticator value.
     */
    public Authenticator getAuthenticator() {
        return myAuthenticator;
    }

    /**
     * Returns the database the credential are valid for. Use {@link #ADMIN_DB}
     * to authenticate as an administrator.
     *
     * @return The database the credential are valid for.
     */
    public String getDatabase() {
        return myDatabase;
    }

    /**
     * Returns the file containing the full credentials.
     *
     * @return The file containing the full credentials. May be
     *         <code>null</code>.
     */
    public File getFile() {
        return myFile;
    }

    /**
     * Returns the option value.
     *
     * @param optionName
     *            The name of the option to set.
     * @param defaultValue
     *            The value of the option if it is not set or cannot be parsed
     *            via {@link Boolean#parseBoolean(String)}.
     * @return The option value.
     */
    public boolean getOption(final String optionName, final boolean defaultValue) {
        final String value = myOptions.get(optionName);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    /**
     * Returns the option value.
     *
     * @param optionName
     *            The name of the option to set.
     * @param defaultValue
     *            The value of the option if it is not set or cannot be parsed
     *            via {@link Integer#parseInt(String)}.
     * @return The option value.
     */
    public int getOption(final String optionName, final int defaultValue) {
        final String value = myOptions.get(optionName);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (final NumberFormatException nfe) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Returns the option value.
     *
     * @param optionName
     *            The name of the option to set.
     * @param defaultValue
     *            The value of the option if it is not set.
     * @return The option value.
     */
    public String getOption(final String optionName, final String defaultValue) {
        String value = myOptions.get(optionName);
        if (value == null) {
            value = defaultValue;
        }

        return value;
    }

    /**
     * Returns the password for the credential set. A clone of the internal
     * array is returns that should be cleared when it is done being used via
     * something like {@link java.util.Arrays#fill(char[], char)
     * Arrays.fill(password, 0)}
     *
     * @return The password for the credential set.
     */
    public char[] getPassword() {
        return myPassword.clone();
    }

    /**
     * Returns the user name for the credential set.
     *
     * @return The user name for the credential set.
     */
    public String getUserName() {
        return myUserName;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to hash the credential.
     * </p>
     */
    @Override
    public int hashCode() {
        int result = 1;

        result = (31 * result)
                + ((myAuthenticationType == null) ? 0 : myAuthenticationType
                        .hashCode());
        result = (31 * result)
                + ((myDatabase == null) ? 0 : myDatabase.hashCode());
        result = (31 * result) + myOptions.hashCode();
        result = (31 * result) + Arrays.hashCode(myPassword);
        result = (31 * result)
                + ((myUserName == null) ? 0 : myUserName.hashCode());
        result = (31 * result) + ((myFile == null) ? 0 : myFile.hashCode());

        return result;
    }

    /**
     * Returns true if the password has atleast a single character.
     *
     * @return True if the password has atleast a single character, false
     *         otherwise.
     */
    public boolean hasPassword() {
        return (myPassword.length > 0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to returns the credential in a human readable form.
     * </p>
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{ username : '");
        builder.append(myUserName);
        builder.append("', database : '");
        builder.append(myDatabase);
        if (myFile != null) {
            builder.append("', file : '");
            builder.append(myFile.getName());
        }
        builder.append("', password : '<redacted>', type: '");
        if (KERBEROS.equals(myAuthenticationType)) {
            builder.append("KERBEROS");
        }
        else if (PLAIN_SASL.equals(myAuthenticationType)) {
            builder.append("PLAIN SASL");
        }
        else if (SCRAM_SHA_1.equals(myAuthenticationType)) {
            builder.append("SCRAM-SHA-1");
        }
        else if (X509.equals(myAuthenticationType)) {
            builder.append("x.509");
        }
        else if (MONGODB_CR.equals(myAuthenticationType)) {
            builder.append("MONGODB-CR");
        }
        else if (myAuthenticationType != null) {
            builder.append(myAuthenticationType);
        }

        for (final Map.Entry<String, String> option : myOptions.entrySet()) {
            builder.append("', '");
            builder.append(option.getKey());
            builder.append("': '");
            builder.append(option.getValue());
        }

        builder.append("' }");

        return builder.toString();
    }

    /**
     * Loads the authenticator for the credential.
     *
     * @throws ClassNotFoundException
     *             If the authenticators Class cannot be found.
     * @throws InstantiationException
     *             If the authenticator cannot be instantiated.
     * @throws IllegalAccessException
     *             If the authenticator cannot be accessed.
     */
    /* package */void loadAuthenticator() throws ClassNotFoundException,
            InstantiationException, IllegalAccessException {
        if (myAuthenticator == null) {
            myAuthenticator = (Authenticator) Class.forName(
                    getAuthenticationType()).newInstance();
        }
    }

    /**
     * Does a null safe equals comparison.
     *
     * @param rhs
     *            The right-hand-side of the comparison.
     * @param lhs
     *            The left-hand-side of the comparison.
     * @return True if the rhs equals the lhs. Note: nullSafeEquals(null, null)
     *         returns true.
     */
    private boolean nullSafeEquals(final Object rhs, final Object lhs) {
        return (rhs == lhs) || ((rhs != null) && rhs.equals(lhs));
    }

    /**
     * Sets the transient state of this {@link Credential}.
     *
     * @param in
     *            The input stream.
     * @throws ClassNotFoundException
     *             On a failure loading a class in this classed reachable tree.
     * @throws IOException
     *             On a failure reading from the stream.
     */
    private void readObject(final ObjectInputStream in)
            throws ClassNotFoundException, IOException {
        in.defaultReadObject();
        myAuthenticator = null;
    }

    /**
     * Builder provides a helper for creating a {@link Credential}.
     *
     * @copyright 2013, Allanbank Consulting, Inc., All Rights Reserved
     */
    @NotThreadSafe
    public static class Builder {
        /**
         * The authentication type or mode that the credential should be used
         * with.
         */
        protected String myAuthenticationType;

        /** The template authenticator for the credential. */
        protected Authenticator myAuthenticator;

        /** The database the credential are valid for. */
        protected String myDatabase;

        /** The file containing the full credentials. */
        protected File myFile;

        /** Other options for the credentials. */
        protected final Map<String, String> myOptions;

        /** The password for the credential set. */
        protected char[] myPassword;

        /** The user name for the credential set. */
        protected String myUserName;

        /**
         * Creates a new Builder.
         */
        public Builder() {
            myOptions = new HashMap<String, String>();
            reset();
        }

        /**
         * Adds an option to the built credentials.
         *
         * @param optionName
         *            The name of the option to set.
         * @param optionValue
         *            The value of the option to set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder addOption(final String optionName,
                final boolean optionValue) {
            myOptions.put(optionName, String.valueOf(optionValue));

            return this;
        }

        /**
         * Adds an option to the built credentials.
         *
         * @param optionName
         *            The name of the option to set.
         * @param optionValue
         *            The value of the option to set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder addOption(final String optionName, final int optionValue) {
            myOptions.put(optionName, String.valueOf(optionValue));

            return this;
        }

        /**
         * Adds an option to the built credentials.
         *
         * @param optionName
         *            The name of the option to set.
         * @param optionValue
         *            The value of the option to set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder addOption(final String optionName,
                final String optionValue) {
            myOptions.put(optionName, optionValue);

            return this;
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)}.
         * </p>
         *
         * @param authenticationType
         *            The new value for the authentication type or mode that the
         *            credential should be used with.
         * @return This {@link Builder} for method chaining.
         */
        public Builder authenticationType(final String authenticationType) {
            return setAuthenticationType(authenticationType);
        }

        /**
         * Sets the value of the template authenticator for the credential.
         * <p>
         * This method delegates to {@link #setAuthenticator(Authenticator)}.
         * </p>
         *
         * @param authenticator
         *            The new value for the template authenticator for the
         *            credential.
         * @return This {@link Builder} for method chaining.
         */
        public Builder authenticator(final Authenticator authenticator) {
            return setAuthenticator(authenticator);
        }

        /**
         * Creates the credential from this builder.
         *
         * @return The {@link Credential} populated with the state of this
         *         builder.
         */
        public Credential build() {
            return new Credential(this);
        }

        /**
         * Sets the value of the database the credential are valid for.
         * <p>
         * This method delegates to {@link #setDatabase(String)}.
         * </p>
         *
         * @param database
         *            The new value for the database the credential are valid
         *            for.
         * @return This {@link Builder} for method chaining.
         */
        public Builder database(final String database) {
            return setDatabase(database);
        }

        /**
         * Sets the value of the file containing the full credentials.
         * <p>
         * This method delegates to {@link #setFile(File)}.
         * </p>
         *
         * @param file
         *            The new value for the file containing the full
         *            credentials.
         * @return This {@link Builder} for method chaining.
         */
        public Builder file(final File file) {
            return setFile(file);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with to Kerberos.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(KERBEROS)}.
         * </p>
         * <p>
         * <em>Note:</em> Use of Kerberos for authentication requires the
         * driver's extensions. See the <a href=
         * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/kerberos.html"
         * >Kerberos Usage Guide</a> for details.
         * </p>
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/kerberos.html">Kerberos
         *      Usage Guide</a>
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder kerberos() {
            return setAuthenticationType(KERBEROS);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with LDAP via PLAIN SASL.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(PLAIN_SASL)}.
         * </p>
         * <p>
         * <em>Note:</em> Use of Plain SASL for authentication requires the
         * driver's extensions. See the <a href=
         * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html"
         * >Plain SASL Usage Guide</a> for details.
         * </p>
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html">Plain
         *      SASL Usage Guide</a>
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder ldap() {
            return setAuthenticationType(PLAIN_SASL);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with to MongoDB Challenge/Response.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(MONGODB_CR)}.
         * </p>
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder mongodbCR() {
            return setAuthenticationType(MONGODB_CR);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with PAM via PLAIN SASL.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(PLAIN_SASL)}.
         * </p>
         * <p>
         * <em>Note:</em> Use of Plain SASL for authentication requires the
         * driver's extensions. See the <a href=
         * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html"
         * >Plain SASL Usage Guide</a> for details.
         * </p>
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html">Plain
         *      SASL Usage Guide</a>
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder pam() {
            return setAuthenticationType(PLAIN_SASL);
        }

        /**
         * Sets the value of the password for the credential set.
         * <p>
         * This method delegates to {@link #setPassword(char[])}.
         * </p>
         *
         * @param password
         *            The new value for the password for the credential set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder password(final char[] password) {
            return setPassword(password);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with to PLAIN SASL.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(PLAIN_SASL)}.
         * </p>
         * <p>
         * <em>Note:</em> Use of Plain SASL for authentication requires the
         * driver's extensions. See the <a href=
         * "http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html"
         * >Plain SASL Usage Guide</a> for details.
         * </p>
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/plain_sasl.html">Plain
         *      SASL Usage Guide</a>
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder plainSasl() {
            return setAuthenticationType(PLAIN_SASL);
        }

        /**
         * Resets the builder to a known state.
         *
         * @return This {@link Builder} for method chaining.
         */
        public Builder reset() {
            if (myPassword != null) {
                Arrays.fill(myPassword, '\u0000');
            }

            myAuthenticationType = MONGODB_CR;
            myAuthenticator = null;
            myDatabase = ADMIN_DB;
            myFile = null;
            myPassword = NO_PASSWORD;
            myUserName = null;
            myOptions.clear();

            return this;
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with to SCARM-SHA-1 SASL.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(SCRAM_SHA_1)}.
         * </p>
         * <p>
         * The extensions implementation contains a complete implementation of
         * the {@code saslPrep} algorithm for the username and password as
         * specified by RFC 4013 which is itself a profile for
         * {@code stringprep} from RFC 3454. This enables the use of non-ascii
         * character sets within the username and password.
         * </p>
         *
         * @return This {@link Builder} for method chaining.
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/scram-sha-1.html">SCRAM-SHA-1
         *      SASL Usage Guide</a>
         * @see <a href="http://tools.ietf.org/html/rfc5802">RFC 5802</a>
         * @see <a href="http://tools.ietf.org/html/rfc4013">RFC 4013</a>
         * @see <a href="http://tools.ietf.org/html/rfc3454">RFC 3454</a>
         */
        public Builder scramSha1() {
            return setAuthenticationType(SCRAM_SHA_1);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with.
         *
         * @param authenticationType
         *            The new value for the authentication type or mode that the
         *            credential should be used with.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setAuthenticationType(final String authenticationType) {
            myAuthenticationType = authenticationType;
            return this;
        }

        /**
         * Sets the value of the template authenticator for the credential.
         *
         * @param authenticator
         *            The new value for the template authenticator for the
         *            credential.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setAuthenticator(final Authenticator authenticator) {
            myAuthenticator = authenticator;
            return this;
        }

        /**
         * Sets the value of the database the credential are valid for.
         *
         * @param database
         *            The new value for the database the credential are valid
         *            for.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setDatabase(final String database) {
            if (database == null) {
                myDatabase = ADMIN_DB;
            }
            else {
                myDatabase = database;
            }
            return this;
        }

        /**
         * Sets the value of the file containing the full credentials.
         *
         * @param file
         *            The new value for the file containing the full
         *            credentials.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setFile(final File file) {
            myFile = file;
            return this;
        }

        /**
         * Sets the value of the password for the credential set.
         *
         * @param password
         *            The new value for the password for the credential set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setPassword(final char[] password) {
            Arrays.fill(myPassword, '\u0000');

            if (password == null) {
                myPassword = NO_PASSWORD;
            }
            else {
                myPassword = password.clone();
            }
            return this;
        }

        /**
         * Sets the value of the user name for the credential set.
         *
         * @param userName
         *            The new value for the user name for the credential set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder setUserName(final String userName) {
            myUserName = userName;
            return this;
        }

        /**
         * Sets the value of the user name for the credential set.
         * <p>
         * This method delegates to {@link #setUserName(String)}.
         * </p>
         *
         * @param userName
         *            The new value for the user name for the credential set.
         * @return This {@link Builder} for method chaining.
         */
        public Builder userName(final String userName) {
            return setUserName(userName);
        }

        /**
         * Sets the value of the authentication type or mode that the credential
         * should be used with to x.509 client certificates exchanged via the
         * TLS connection.
         * <p>
         * This method delegates to {@link #setAuthenticationType(String)
         * setAuthenticationType(X509)}.
         * </p>
         *
         * @return This {@link Builder} for method chaining.
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/tls.html">TLS/SSL
         *      Usage Guide</a>
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/x509.html">x.509
         *      Authentication Usage Guide</a>
         */
        public Builder x509() {
            return setAuthenticationType(X509);
        }

        /**
         * Sets the {@link #userName(String) user name} to that of the x.509
         * certificate subject name and then sets the value of the
         * authentication type or mode that the credential should be used with
         * to x.509 client certificates exchanged via the TLS connection.
         * <p>
         * This method delegates to {@link #userName(String)
         * userName(cert.getSubjectX500Principal().toString())}.{@link #x509()
         * x509()}.
         * </p>
         *
         * @param cert
         *            The client's certificate containing the client subject
         *            name.
         * @return This {@link Builder} for method chaining.
         *
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/tls.html">TLS/SSL
         *      Usage Guide</a>
         * @see <a
         *      href="http://www.allanbank.com/mongodb-async-driver/userguide/authentication/x509.html">x.509
         *      Authentication Usage Guide</a>
         */
        public Builder x509(final X509Certificate cert) {
            return userName(cert.getSubjectX500Principal().toString()).x509();
        }
    }
}
