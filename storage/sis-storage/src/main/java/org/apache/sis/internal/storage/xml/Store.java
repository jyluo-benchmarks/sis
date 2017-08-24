/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.storage.xml;

import java.util.Map;
import java.util.Collections;
import java.util.logging.LogRecord;
import java.io.Closeable;
import java.io.Reader;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import org.opengis.metadata.Metadata;
import org.opengis.util.FactoryException;
import org.opengis.referencing.ReferenceSystem;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.apache.sis.xml.XML;
import org.apache.sis.storage.Resource;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.util.logging.WarningListener;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.internal.system.Loggers;
import org.apache.sis.internal.storage.MetadataBuilder;
import org.apache.sis.internal.referencing.DefinitionVerifier;
import org.apache.sis.parameter.Parameters;
import org.opengis.parameter.ParameterValueGroup;


/**
 * A data store which creates data objects from a XML file.
 * This {@code DataStore} implementation is basically a facade for the {@link XML#unmarshal(Source, Map)} method.
 * The current implementation recognizes the following objects:
 *
 * <ul>
 *   <li>{@link Metadata}, typically built from the {@code <gmd:MD_Metadata>} XML element.</li>
 *   <li>{@link ReferenceSystem}, accessible by {@link Metadata#getReferenceSystemInfo()}.</li>
 * </ul>
 *
 * The above list may be extended in any future SIS version.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 0.8
 * @since   0.4
 * @module
 */
final class Store extends DataStore {
    /**
     * The input stream or reader, set by the constructor and cleared when no longer needed.
     */
    private StreamSource source;
    private URI sourceUri;

    /**
     * The unmarshalled object, initialized only when first needed.
     * May still be {@code null} if the unmarshalling failed.
     */
    private Object object;

    /**
     * The metadata object, determined when first needed.
     */
    private Metadata metadata;

    /**
     * Creates a new XML store from the given file, URL or stream.
     *
     * @param  provider   the factory that created this {@code DataStore} instance, or {@code null} if unspecified.
     * @param  connector  information about the storage (URL, stream, <i>etc</i>).
     * @throws DataStoreException if an error occurred while opening the stream.
     */
    public Store(final StoreProvider provider, final StorageConnector connector) throws DataStoreException {
        super(provider, connector);
        final InputStream in = connector.getStorageAs(InputStream.class);
        try {
            sourceUri = connector.getStorageAs(URI.class);
        } catch (IllegalArgumentException ex) {
            //open parameters will be null.
        }
        if (in != null) {
            source = new StreamSource(in);
        } else {
            final Reader reader = connector.getStorageAs(Reader.class);
            if (reader != null) {
                source = new StreamSource(reader);
            }
        }
        final Closeable c = input(source);
        connector.closeAllExcept(c);
        if (c == null) {
            throw new DataStoreException(Errors.format(Errors.Keys.CanNotOpen_1, super.getDisplayName()));
        }
    }

    /**
     * Returns the input stream or reader set in the given source, or {@code null} if none.
     */
    private static Closeable input(final StreamSource source) {
        Closeable in = null;
        if (source != null) {
            in = source.getInputStream();
            if (in == null) {
                in = source.getReader();
            }
        }
        return in;
    }

    /**
     * Returns the properties to give to the (un)marshaller.
     */
    private Map<String,?> properties() {
        if (listeners.hasListeners()) {
            return Collections.singletonMap(XML.WARNING_LISTENER, new WarningListener<Object>() {
                /** Returns the type of objects that emit warnings of interest for this listener. */
                @Override public Class<Object> getSourceClass() {
                    return Object.class;
                }

                /** Reports the occurrence of a non-fatal error during XML unmarshalling. */
                @Override public void warningOccured(final Object source, final LogRecord warning) {
                    warning.setLoggerName(Loggers.XML);
                    listeners.warning(warning);
                }
            });
        }
        return null;
    }

    /**
     * Unmarshal the object, if not already done. Note that {@link #object} may still be null
     * if an exception has been thrown at this invocation time or in previous invocation.
     *
     * @throws DataStoreException if an error occurred during the unmarshalling process.
     */
    private void unmarshal() throws DataStoreException {
        final StreamSource s = source;
        final Closeable in = input(s);
        source = null;                          // Cleared first in case of error.
        if (in != null) try {
            try {
                object = XML.unmarshal(s, properties());
            } finally {
                in.close();
            }
        } catch (JAXBException | IOException e) {
            throw new DataStoreException(Errors.format(Errors.Keys.CanNotRead_1, getDisplayName()), e);
        }
        if (object instanceof CoordinateReferenceSystem) try {
            final DefinitionVerifier v = DefinitionVerifier.withAuthority((CoordinateReferenceSystem) object, null, false);
            if (v != null) {
                log(v.warning(false));
            }
        } catch (FactoryException e) {
            listeners.warning(null, e);
        }
    }

    /**
     * Reports a warning, if non-null.
     */
    private void log(final LogRecord record) {
        if (record != null) {
            record.setSourceClassName(Store.class.getName());
            record.setSourceMethodName("getMetadata");          // Public facade for the parse() method.
            record.setLoggerName(Loggers.XML);
            listeners.warning(record);
        }
    }

    /**
     * Returns the metadata associated to the unmarshalled object, or {@code null} if none.
     * The current implementation performs the following choice:
     *
     * <ul>
     *   <li>If the unmarshalled object implements the {@link Metadata} interface, then it is returned directly.</li>
     *   <li>Otherwise if the unmarshalled object implements {@link ReferenceSystem}, then it is wrapped in the
     *       <cite>"reference system info"</cite> property of a new {@link DefaultMetadata} instance.</li>
     * </ul>
     *
     * Other cases may be added in any future SIS version.
     *
     * @return the metadata associated to the unmarshalled object, or {@code null} if none.
     * @throws DataStoreException if an error occurred during the unmarshalling process.
     */
    @Override
    public synchronized Metadata getMetadata() throws DataStoreException {
        if (metadata == null) {
            unmarshal();
            if (object instanceof Metadata) {
                metadata = (Metadata) object;
            } else if (object instanceof ReferenceSystem) {
                final MetadataBuilder builder = new MetadataBuilder();
                builder.addReferenceSystem((ReferenceSystem) object);
                builder.addTitle(getDisplayName());
                metadata = builder.build(true);
            }
        }
        return metadata;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ParameterValueGroup getOpenParameters() {
        if (sourceUri==null) return null;
        final Parameters parameters = Parameters.castOrWrap(StoreProvider.OPEN_DESCRIPTOR.createValue());
        parameters.getOrCreate(StoreProvider.PARAM_LOCATION).setValue(sourceUri);
        return parameters;
    }

    /**
     * Current implementation does not provide any resource since it is only about metadata.
     * Futures versions may return resources if Apache SIS provides a wider GML support.
     */
    @Override
    public Resource getRootResource() throws DataStoreException {
        return null;
    }

    /**
     * Closes this data store and releases any underlying resources.
     *
     * @throws DataStoreException if an error occurred while closing this data store.
     */
    @Override
    public synchronized void close() throws DataStoreException {
        object = null;
        final Closeable in = input(source);
        source = null;                          // Cleared first in case of failure.
        if (in != null) try {
            in.close();
        } catch (IOException e) {
            throw new DataStoreException(e);
        }
    }
}
