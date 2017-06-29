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
package org.apache.sis.internal.storage.csv;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import javax.measure.Unit;
import javax.measure.quantity.Time;
import org.opengis.metadata.Metadata;
import org.opengis.util.FactoryException;
import org.opengis.metadata.maintenance.ScopeCode;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.TemporalCRS;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.feature.DefaultAttributeType;
import org.apache.sis.feature.DefaultFeatureType;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.internal.referencing.GeodeticObjectBuilder;
import org.apache.sis.internal.util.UnmodifiableArrayList;
import org.apache.sis.internal.storage.MetadataBuilder;
import org.apache.sis.internal.storage.io.IOUtilities;
import org.apache.sis.internal.storage.FeatureStore;
import org.apache.sis.internal.feature.Geometries;
import org.apache.sis.internal.feature.MovingFeature;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.metadata.sql.MetadataStoreException;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.DataStoreContentException;
import org.apache.sis.storage.DataStoreReferencingException;
import org.apache.sis.storage.IllegalNameException;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.setup.OptionKey;
import org.apache.sis.util.ArraysExt;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.ObjectConverter;
import org.apache.sis.util.ObjectConverters;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.collection.BackingStoreException;
import org.apache.sis.measure.Units;

// Branch-dependent imports
import java.time.Instant;
import java.time.DateTimeException;
import java.util.function.Consumer;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.opengis.feature.Attribute;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.feature.PropertyType;
import org.opengis.feature.AttributeType;


/**
 * A data store which creates feature instances from a CSV file using the OGC Moving Features specification.
 * See package javadoc for more information on the syntax.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 0.8
 * @since   0.7
 * @module
 */
public final class Store extends FeatureStore {
    /**
     * The character at the beginning of lines to ignore in the header.
     * Note that this is not part of OGC Moving Feature Specification.
     */
    private static final char COMMENT = '#';

    /**
     * The character at the beginning of metadata lines.
     */
    static final char METADATA = '@';

    /**
     * The quote character. Quotes inside quoted texts must be doubled.
     */
    private static final char QUOTE = '"';

    /**
     * The column separator.
     */
    static final char SEPARATOR = ',';

    /**
     * The separator between ordinate values in a coordinate.
     */
    static final char ORDINATE_SEPARATOR = ' ';

    /**
     * The prefix for elements in the {@code @columns} line that specify the data type.
     * Examples: {@code xsd:boolean}, {@code xsd:decimal}, {@code xsd:integer}, <i>etc</i>.
     */
    private static final String TYPE_PREFIX = "xsd:";

    /**
     * Index of the column containing trajectory coordinates.
     * Columns before the trajectory are Moving Feature identifier {@code mfIdRef}, start time and end time.
     */
    private static final int TRAJECTORY_COLUMN = 3;

    /**
     * The reader, set by the constructor and cleared when no longer needed.
     */
    private BufferedReader source;

    /**
     * The character encoding, or {@code null} if unspecified (in which case the platform default is assumed).
     * Note that the default value is different than the moving feature specification, which requires UTF-8.
     * See "Departures from Moving Features specification" in package javadoc.
     */
    private final Charset encoding;

    /**
     * The metadata object, or {@code null} if not yet created.
     */
    private transient DefaultMetadata metadata;

    /**
     * The three- or four-dimensional envelope together with the CRS.
     * This envelope contains a vertical component if the feature trajectories are 3D,
     * and a temporal component if the CSV file contains a start time and end time.
     *
     * @see #parseEnvelope(List)
     */
    private final GeneralEnvelope envelope;

    /**
     * Description of the columns found in the CSV file.
     *
     * @see #parseFeatureType(List)
     */
    final FeatureType featureType;

    /**
     * {@code true} if {@link #featureType} contains a trajectory column.
     */
    private boolean hasTrajectories;

    /**
     * The number of dimensions other than time in the coordinate reference system.
     * Shall be 2 or 3 according Moving Features CSV encoding specification, but Apache SIS
     * may be tolerant to other values (depending on the backing geometry library).
     */
    private short spatialDimensionCount;

    /**
     * The factory to use for creating geometries.
     */
    private final Geometries<?> geometries;

    /**
     * Appearing order of trajectories (time or sequential), or {@code null} if unspecified.
     *
     * @see #parseFoliation(List)
     */
    final Foliation foliation;

    /**
     * Specifies how time is encoded in the CSV file, or {@code null} if there is no time.
     */
    private TimeEncoding timeEncoding;

    /**
     * {@code true} if this reader should create a separated {@code Feature} instance for each line in the CSV file.
     * By default, this is {@code true} if the CSV files does not seem to contain moving features.
     * But the user can also force this value to {@code true}, for example for testing purposes.
     */
    private boolean dissociate;

    /**
     * All parsed moving features, or {@code null} if none or if not yet parsed. If {@link #dissociate}
     * is {@code false}, then this list will be created by {@link #features()} when first needed.
     */
    private transient List<Feature> movingFeatures;

    /**
     * Creates a new CSV store from the given file, URL or stream.
     *
     * <p>If the CSV file is known to be a Moving Feature file, then the given connector should
     * have an {@link org.apache.sis.setup.OptionKey#ENCODING} associated to the UTF-8 value.</p>
     *
     * @param  provider    the factory that created this {@code DataStore} instance, or {@code null} if unspecified.
     * @param  connector   information about the storage (URL, stream, <i>etc</i>).
     * @param  dissociate  {@code true} for forcing the creation of a different {@code Feature} instance for each line.
     * @throws DataStoreException if an error occurred while opening the stream.
     */
    public Store(final StoreProvider provider, final StorageConnector connector, boolean dissociate) throws DataStoreException {
        super(provider, connector);
        final Reader r = connector.getStorageAs(Reader.class);
        connector.closeAllExcept(r);
        if (r == null) {
            throw new DataStoreException(Errors.format(Errors.Keys.CanNotOpen_1, super.getDisplayName()));
        }
        source = (r instanceof BufferedReader) ? (BufferedReader) r : new LineNumberReader(r);
        geometries = Geometries.implementation(connector.getOption(OptionKey.GEOMETRY_LIBRARY));
        this.dissociate = dissociate;
        GeneralEnvelope envelope    = null;
        FeatureType     featureType = null;
        Foliation       foliation   = null;
        try {
            final List<String> elements = new ArrayList<>();
            source.mark(1024);
            String line;
            while ((line = source.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                final char c = line.charAt(0);
                if (c == COMMENT) continue;
                if (c != METADATA) break;
                split(line, elements);
                final String keyword = elements.get(0);
                switch (keyword.toLowerCase(Locale.US)) {
                    case "@stboundedby": {
                        if (envelope != null) {
                            throw new DataStoreContentException(duplicated("@stboundedby"));
                        }
                        envelope = parseEnvelope(elements);
                        dissociate |= (timeEncoding == null);   // Need to be updated before parseFeatureType(…) execution.
                        break;
                    }
                    case "@columns": {
                        if (featureType != null) {
                            throw new DataStoreContentException(duplicated("@columns"));
                        }
                        featureType = parseFeatureType(elements);
                        break;
                    }
                    case "@foliation": {
                        if (foliation != null) {
                            throw new DataStoreContentException(duplicated("@foliation"));
                        }
                        foliation = parseFoliation(elements);
                        break;
                    }
                    default: {
                        final LogRecord record = errors().getLogRecord(Level.WARNING, Errors.Keys.UnknownKeyword_1, keyword);
                        record.setSourceClassName(Store.class.getName());
                        record.setSourceMethodName("parseHeader");
                        listeners.warning(record);
                        break;
                    }
                }
                elements.clear();
                source.mark(1024);
            }
            source.reset();
        } catch (IOException e) {
            throw new DataStoreException(getLocale(), StoreProvider.NAME, super.getDisplayName(), source).initCause(e);
        } catch (FactoryException e) {
            throw new DataStoreReferencingException(getLocale(), StoreProvider.NAME, super.getDisplayName(), source).initCause(e);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new DataStoreContentException(getLocale(), StoreProvider.NAME, super.getDisplayName(), source).initCause(e);
        }
        this.encoding    = connector.getOption(OptionKey.ENCODING);
        this.envelope    = envelope;
        this.featureType = featureType;
        this.foliation   = foliation;
        this.dissociate |= (timeEncoding == null);
    }

    /**
     * Parses the envelope described by the header line starting with {@code @stboundedby}.
     * The envelope returned by this method will be stored in the {@link #envelope} field.
     *
     * <p>Example:</p>
     * {@preformat text
     *   &#64;stboundedby, urn:ogc:def:crs:CRS:1.3:84, 2D, 50.23 9.23, 50.31 9.27, 2012-01-17T12:33:41Z, 2012-01-17T12:37:00Z, sec
     * }
     *
     * @param  elements  the line elements. The first elements should be {@code "@stboundedby"}.
     * @return the envelope, or {@code null} if the given list does not contain enough elements.
     */
    @SuppressWarnings("fallthrough")
    private GeneralEnvelope parseEnvelope(final List<String> elements) throws DataStoreException, FactoryException {
        CoordinateReferenceSystem crs = null;
        int spatialDimensionCount = 2;
        boolean    isDimExplicit  = false;
        double[]   lowerCorner    = ArraysExt.EMPTY_DOUBLE;
        double[]   upperCorner    = ArraysExt.EMPTY_DOUBLE;
        Instant    startTime      = null;
        Instant    endTime        = null;
        Unit<Time> timeUnit       = Units.SECOND;
        boolean    isTimeAbsolute = false;
        int ordinal = -1;
        for (final String element : elements) {
            ordinal++;
            if (!element.isEmpty()) {
                switch (ordinal) {
                    case 0: continue;                                       // The "@stboundedby" header.
                    case 1: crs = CRS.forCode(element); continue;
                    case 2: if (element.length() == 2 && Character.toUpperCase(element.charAt(1)) == 'D') {
                                isDimExplicit = true;
                                spatialDimensionCount = element.charAt(0) - '0';
                                if (spatialDimensionCount < 1 || spatialDimensionCount > 3) {
                                    throw new DataStoreReferencingException(errors().getString(
                                        Errors.Keys.IllegalCoordinateSystem_1, element));
                                }
                                continue;
                            }
                            /*
                             * According the Moving Feature specification, the [dim] element is optional.
                             * If we did not recognized the dimension, assume that we have the next element
                             * (i.e. the lower corner). Fall-through so we can process it.
                             */
                            ordinal++;  // Fall through
                    case 3: lowerCorner = CharSequences.parseDoubles(element, ORDINATE_SEPARATOR); continue;
                    case 4: upperCorner = CharSequences.parseDoubles(element, ORDINATE_SEPARATOR); continue;
                    case 5: startTime   = Instant.parse(element); continue;
                    case 6: endTime     = Instant.parse(element); continue;
                    case 7: switch (element.toLowerCase(Locale.US)) {
                                case "sec":
                                case "second":   /* Already SECOND. */    continue;
                                case "minute":   timeUnit = Units.MINUTE; continue;
                                case "hour":     timeUnit = Units.HOUR;   continue;
                                case "day":      timeUnit = Units.DAY;    continue;
                                case "absolute": isTimeAbsolute = true;   continue;
                                default: throw new DataStoreReferencingException(errors().getString(Errors.Keys.UnknownUnit_1, element));
                            }
                }
                // If we reach this point, there is some remaining unknown elements. Ignore them.
                break;
            }
        }
        /*
         * Complete the CRS by adding a vertical component if needed, then a temporal component.
         * Only after the CRS has been completed we can create the envelope.
         *
         * Vertical component:
         *   Ideally, should be part of the CRS created from the authority code. But if the authority
         *   code is only for a two-dimensional CRS, we default to an arbitrary height component.
         *
         * Temporal component:
         *   Assumed never part of the authority code. We need to build the temporal component ourselves
         *   in order to set the origin to the start time.
         */
        final GeneralEnvelope envelope;
        if (crs != null) {
            int count = 0;
            final CoordinateReferenceSystem[] components = new CoordinateReferenceSystem[3];
            components[count++] = crs;
            /*
             * If the coordinates are three-dimensional but the CRS is 2D, add a vertical axis.
             * The vertical axis shall be the third one, however we do not enforce that rule
             * since Apache SIS should work correctly even if the vertical axis is elsewhere.
             */
            int dimension = crs.getCoordinateSystem().getDimension();
            if (isDimExplicit) {
                if (spatialDimensionCount > dimension) {
                    components[count++] = CommonCRS.Vertical.MEAN_SEA_LEVEL.crs();
                    dimension++;
                }
                if (dimension != spatialDimensionCount) {
                    throw new DataStoreReferencingException(errors().getString(
                            Errors.Keys.MismatchedDimension_3, "@stboundedby(CRS)", spatialDimensionCount, dimension));
                }
            }
            if (dimension > Short.MAX_VALUE) {
                throw new DataStoreReferencingException(errors().getString(
                        Errors.Keys.ExcessiveNumberOfDimensions_1, dimension));
            }
            spatialDimensionCount = dimension;
            /*
             * Add a temporal axis if we have a start time (no need for end time).
             * This block presumes that the CRS does not already have a time axis.
             * If a time axis was already present, an exception will be thrown at
             * builder.createCompoundCRS(…) invocation time.
             */
            final GeodeticObjectBuilder builder = new GeodeticObjectBuilder();
            String name = crs.getName().getCode();
            if (startTime != null) {
                final TemporalCRS temporal;
                if (isTimeAbsolute) {
                    temporal = TimeEncoding.DEFAULT.crs();
                    timeEncoding = TimeEncoding.ABSOLUTE;
                } else {
                    temporal = builder.createTemporalCRS(Date.from(startTime), timeUnit);
                    timeEncoding = new TimeEncoding(temporal.getDatum(), timeUnit);
                }
                components[count++] = temporal;
                name = name + " + " + temporal.getName().getCode();
            }
            crs = builder.addName(name).createCompoundCRS(ArraysExt.resize(components, count));
            envelope = new GeneralEnvelope(crs);
        } else {
            /*
             * While illegal in principle, Apache SIS accepts missing CRS.
             * In such case, use only the number of dimensions.
             */
            int dim = spatialDimensionCount;
            if (startTime != null) dim++;           // Same criterion than in above block.
            envelope = new GeneralEnvelope(dim);
        }
        /*
         * At this point we got the three- or four-dimensional spatio-temporal CRS.
         * We can now set the envelope coordinate values, including temporal values.
         */
        int dim;
        if ((dim = lowerCorner.length) != spatialDimensionCount ||
            (dim = upperCorner.length) != spatialDimensionCount)
        {
            throw new DataStoreReferencingException(errors().getString(
                    Errors.Keys.MismatchedDimension_3, "@stboundedby(BBOX)", spatialDimensionCount, dim));
        }
        for (int i=0; i<spatialDimensionCount; i++) {
            envelope.setRange(i, lowerCorner[i], upperCorner[i]);
        }
        if (startTime != null) {
            envelope.setRange(spatialDimensionCount, timeEncoding.toCRS(startTime.toEpochMilli()),
                    (endTime == null) ? Double.NaN : timeEncoding.toCRS(endTime.toEpochMilli()));
        }
        this.spatialDimensionCount = (short) spatialDimensionCount;
        return envelope;
    }

    /**
     * Parses the columns metadata described by the header line starting with {@code @columns}.
     * The feature type returned by this method will be stored in the {@link #featureType} field.
     *
     * <p>Example:</p>
     * {@preformat text
     *   &#64;columns, mfidref, trajectory, state,xsd:token, "type code",xsd:integer
     * }
     *
     * @param  elements  the line elements. The first element should be {@code "@columns"}.
     * @return the column metadata, or {@code null} if the given list does not contain enough elements.
     */
    @SuppressWarnings("rawtypes")               // "rawtypes" because of generic array creation.
    private FeatureType parseFeatureType(final List<String> elements) throws DataStoreException {
        AttributeType[] characteristics = null;
        final int size = elements.size();
        final List<PropertyType> properties = new ArrayList<>();
        for (int i=1; i<size; i++) {
            final String name = elements.get(i);
            Class<?> type = null;
            if (++i < size) {
                String tn = elements.get(i);
                if (!tn.isEmpty() && tn.regionMatches(true, 0, TYPE_PREFIX, 0, TYPE_PREFIX.length())) {
                    String st = tn.substring(TYPE_PREFIX.length()).toLowerCase(Locale.US);
                    switch (st) {
                        case "boolean":  type = Boolean.class; break;
                        case "decimal":  type = Double .class; break;
                        case "integer":  type = Integer.class; break;
                        case "string":   type = String .class; break;
                        case "datetime": type = Instant.class; break;
                        case "anyuri":   type = URI    .class; break;
                        default: throw new DataStoreContentException(errors().getString(Errors.Keys.UnknownType_1, tn));
                    }
                }
            }
            int minOccurrence = 0;
            int maxOccurrence = dissociate ? 1 : Integer.MAX_VALUE;
            if (type == null) {
                /*
                 * If the column name was not followed by a type, default to a String type except in the special
                 * case of trajectory. Note that according the Moving Feature specification, only the two first
                 * columns are not followed by a type. Those columns are:
                 *
                 *   1) mfidref     - used in order to identify the moving feature.
                 *   2) trajectory  - defines the spatio-temporal geometry of moving features.
                 *                    Contains implicit "start time" and "end time" columns.
                 *
                 * Those two columns are mandatory in Moving Feature specification. All other ones are optional.
                 */
                type = String.class;
                switch (--i) {
                    case 0:                                             // "@column" (should not happen actually)
                    case 1: {
                        minOccurrence = 1;                              // "mfidref"
                        maxOccurrence = 1;
                        break;
                    }
                    case 2: {                                           // "trajectory" or property.
                        if (name.equalsIgnoreCase("trajectory")) {
                            hasTrajectories = true;
                            if (timeEncoding != null) {
                                properties.add(createProperty("startTime", Instant.class, 1, 1, null));
                                properties.add(createProperty(  "endTime", Instant.class, 1, 1, null));
                            }
                            if (dissociate) {
                                type = double[].class;
                            } else {
                                type = geometries.polylineClass;
                                characteristics = new AttributeType[] {MovingFeature.TIME};
                            }
                            minOccurrence = 1;
                            maxOccurrence = 1;
                        }
                        break;
                    }
                }
            }
            properties.add(createProperty(name, type, minOccurrence, maxOccurrence, characteristics));
        }
        String name = super.getDisplayName();
        final int s = name.lastIndexOf('.');
        if (s > 0) {                            // Exclude 0 because shall not be the first character.
            name = name.substring(0, s);
        }
        return new DefaultFeatureType(Collections.singletonMap(DefaultFeatureType.NAME_KEY, name),
                false, null, properties.toArray(new PropertyType[properties.size()]));
    }

    /**
     * Creates a property type for the given name and type.
     */
    private static PropertyType createProperty(final String name, final Class<?> type,
            final int minOccurrence, final int maxOccurrence, final AttributeType<?>[] characteristics)
    {
        return new DefaultAttributeType<>(Collections.singletonMap(DefaultAttributeType.NAME_KEY, name),
                type, minOccurrence, maxOccurrence, null, characteristics);
    }

    /**
     * Parses the metadata described by the header line starting with {@code @foliation}.
     * The value returned by this method will be stored in the {@link #foliation} field.
     *
     * <p>Example:</p>
     * {@preformat text
     *   &#64;foliation,Sequential
     * }
     *
     * @param  elements  the line elements. The first elements should be {@code "@foliation"}.
     * @return the foliation metadata.
     */
    private Foliation parseFoliation(final List<String> elements) {
        if (elements.size() >= 2) {
            return Foliation.valueOf(elements.get(1).toUpperCase(Locale.US));
        }
        return Foliation.TIME;      // Default value.
    }

    /**
     * Returns the metadata associated to the CSV file, or {@code null} if none.
     *
     * @return the metadata associated to the CSV file, or {@code null} if none.
     * @throws DataStoreException if an error occurred during the parsing process.
     */
    @Override
    public synchronized Metadata getMetadata() throws DataStoreException {
        if (metadata == null) {
            final MetadataBuilder builder = new MetadataBuilder();
            try {
                builder.setFormat(timeEncoding != null && hasTrajectories ? StoreProvider.MOVING : StoreProvider.NAME);
            } catch (MetadataStoreException e) {
                listeners.warning(null, e);
            }
            builder.addEncoding(encoding, MetadataBuilder.Scope.ALL);
            builder.addResourceScope(ScopeCode.DATASET, null);
            try {
                builder.addExtent(envelope);
            } catch (TransformException e) {
                throw new DataStoreReferencingException(getLocale(), StoreProvider.NAME, getDisplayName(), source).initCause(e);
            } catch (UnsupportedOperationException e) {
                /*
                 * Failed to set the temporal components if the sis-temporal module was
                 * not on the classpath, but the other dimensions still have been set.
                 */
                listeners.warning(null, e);
            }
            builder.addFeatureType(featureType, null);
            metadata = builder.build(true);
        }
        return metadata;
    }

    /**
     * Returns the feature type for the given name. The {@code name} argument should be the
     * value specified at the following path (only one such value exists for a CSV data store):
     *
     * <blockquote>
     * {@link #getMetadata()} /
     * {@link org.apache.sis.metadata.iso.DefaultMetadata#getContentInfo() contentInfo} /
     * {@link org.apache.sis.metadata.iso.content.DefaultFeatureCatalogueDescription#getFeatureTypeInfo() featureTypes} /
     * {@link org.apache.sis.metadata.iso.content.DefaultFeatureTypeInfo#getFeatureTypeName() featureTypeName}
     * </blockquote>
     *
     * @param  name  the name of the feature type to get.
     * @return the feature type of the given name (never {@code null}).
     * @throws IllegalNameException if the given name was not found.
     *
     * @since 0.8
     */
    @Override
    public FeatureType getFeatureType(String name) throws IllegalNameException {
        if (featureType.getName().toString().equals(name)) {
            return featureType;
        }
        throw new IllegalNameException(getLocale(), getDisplayName(), name);
    }

    /**
     * Returns the stream of features.
     *
     * @return a stream over all features in the CSV file.
     * @throws DataStoreException if an error occurred while creating the feature stream.
     *
     * @todo Needs to reset the position when doing another pass on the features.
     * @todo If sequential order, publish Feature as soon as identifier changed.
     */
    @Override
    public synchronized Stream<Feature> features() throws DataStoreException {
        if (dissociate) {
            return StreamSupport.stream(new Iter(), false);
        }
        if (movingFeatures == null) try {
            final Iter iter = new Iter();
            iter.readMoving(null, true);
            movingFeatures = UnmodifiableArrayList.wrap(iter.createMovingFeatures());
        } catch (IOException | IllegalArgumentException | DateTimeException e) {
            throw new DataStoreException(canNotParseFile(), e);
        }
        return movingFeatures.stream();
    }

    /**
     * Implementation of the iterator returned by {@link #features()}.
     */
    private final class Iter implements Spliterator<Feature>, Consumer<LogRecord> {
        /**
         * Converters from string representations to the values to store in the {@link #values} array.
         */
        private final ObjectConverter<String,?>[] converters;

        /**
         * All values found in a row. We need to remember those values between different executions
         * of the {@link #tryAdvance(Consumer)} method because the Moving Feature Specification said:
         * "If the value equals the previous value, the text for the value can be omitted."
         */
        private final Object[] values;

        /**
         * Name of the property where to store a value.
         */
        private final String[] propertyNames;

        /**
         * Identifier of the feature in process of being parsed.
         */
        private String identifier;

        /**
         * Where to store the property values and the trajectory of the feature in process of being parsed.
         */
        private MovingFeature builder;

        /**
         * All builders by feature name (not only the one being parsed).
         */
        private final Map<String,MovingFeature> builders;

        /**
         * Creates a new iterator.
         */
        @SuppressWarnings({"unchecked", "rawtypes", "fallthrough"})
        Iter() {
            final Collection<? extends PropertyType> properties = featureType.getProperties(true);
            converters    = new ObjectConverter[properties.size()];
            values        = new Object[converters.length];
            propertyNames = new String[converters.length];
            int i = -1;
            for (final PropertyType p : properties) {
                propertyNames[++i] = p.getName().tip().toString();
                /*
                 * According Moving Features specification:
                 *   Column 0 is the feature identifier (mfidref). There is nothing special to do here.
                 *   Column 1 is the start time.
                 *   Column 2 is the end time.
                 *   Column 3 is the trajectory.
                 *   Columns 4+ are custom attributes.
                 */
                final ObjectConverter<String,?> c;
                switch (i) {
                    case 1: // Fall through
                    case 2: {
                        if (timeEncoding != null) {
                            c = timeEncoding;
                            break;
                        }
                        /*
                         * If there is no time columns, then this column may be the trajectory (note that allowing
                         * CSV files without time is obviously a departure from Moving Features specification.
                         * The intend is to have a CSV format applicable to other features than moving ones).
                         * Fall through in order to process trajectory.
                         */
                    }
                    case TRAJECTORY_COLUMN: {
                        if (hasTrajectories) {
                            c = GeometryParser.INSTANCE;
                            break;
                        }
                        /*
                         * If there is no trajectory columns, than this column is a custum attribute.
                         * CSV files without trajectories are not compliant with Moving Feature spec.,
                         * but we try to keep this reader a little bit more generic.
                         */
                    }
                    default: {
                        c = ObjectConverters.find(String.class, ((AttributeType) p).getValueClass());
                        break;
                    }
                }
                converters[i] = c;
            }
            builders = new LinkedHashMap<>();
        }

        /**
         * Creates all moving features.
         * This method can only be invoked after {@link #readMoving(Consumer, boolean)} completion.
         * This method is ignored if the CSV file contains only static features.
         */
        Feature[] createMovingFeatures() {
            int n = 0;
            final int np = values.length - TRAJECTORY_COLUMN;
            final Feature[] features = new Feature[builders.size()];
            for (final Map.Entry<String,MovingFeature> entry : builders.entrySet()) {
                features[n++] = createMovingFeature(entry.getKey(), entry.getValue(), np);
            }
            return features;
        }

        /**
         * Creates the moving feature of the given name.
         * This method can only be invoked after {@link #readMoving(Consumer, boolean)}.
         *
         * @param  featureName  name of the feature to create.
         * @param  np           number of properties, ignoring the ones before the trajectory column.
         */
        @SuppressWarnings("unchecked")
        private Feature createMovingFeature(final String featureName, final MovingFeature mf, final int np) {
            final Feature feature = featureType.newInstance();
            feature.setPropertyValue(propertyNames[0], featureName);
            mf.storeTimeRange(propertyNames[1], propertyNames[2], feature);
            int column = 0;
            if (hasTrajectories) {
                mf.storeGeometry(featureName, column, spatialDimensionCount, geometries,
                        (Attribute) feature.getProperty(propertyNames[TRAJECTORY_COLUMN]), this);
                column++;
            }
            while (column < np) {
                mf.storeAttribute(column, (Attribute<?>) feature.getProperty(propertyNames[TRAJECTORY_COLUMN + column]));
                column++;
            }
            return feature;
        }

        /**
         * Executes the given action for the next moving feature or for all remaining moving features.
         * This method assumes that the 4 first columns are as documented in the constructor.
         *
         * @param  action  the action to execute as soon as the {@code mfidref} change, or {@code null} if none.
         * @param  all     {@code true} for executing the given action on all remaining features.
         * @return {@code false} if there is no remaining feature after this method call.
         * @throws IOException if an I/O error occurred while reading a feature.
         * @throws IllegalArgumentException if parsing of a number failed, or other error.
         * @throws DateTimeException if parsing of a date failed.
         */
        boolean readMoving(final Consumer<? super Feature> action, final boolean all) throws IOException {
            final FixedSizeList elements = new FixedSizeList(values);
            final int np = values.length - TRAJECTORY_COLUMN;
            String line;
            while ((line = source.readLine()) != null) {
                split(line, elements);
                int n = elements.size();
                for (int i=0; i<n; i++) {
                    values[i] = converters[i].apply((String) values[i]);
                }
                final String  mfIdRef   =  (String)  values[0];
                final long    startTime = ((Instant) values[1]).toEpochMilli();
                final long    endTime   = ((Instant) values[2]).toEpochMilli();
                String        publish   = null;
                if (!mfIdRef.equals(identifier)) {
                    publish    = identifier;
                    identifier = mfIdRef;
                    builder    = builders.computeIfAbsent(mfIdRef, (k) -> new MovingFeature(np));
                }
                builder.addTimeRange(startTime, endTime);
                for (int i=0; i<np; i++) {
                    builder.addValue(i, startTime, endTime, values[i + TRAJECTORY_COLUMN]);
                }
                /*
                 * If we started a new feature and the features are stored in sequential order,
                 * we can publish the previous one right away.
                 */
                if (publish != null && action != null) {
                    action.accept(createMovingFeature(publish, builders.remove(publish), np));
                    if (!all) return true;
                }
                elements.clear();
            }
            return false;
        }

        /**
         * Executes the given action for the next feature or for all remaining features.
         * The features are assumed static, with one feature per line.
         *
         * <p><b>Multi-threading:</b>
         * There is no need for {@code synchronize(Store.this)} statement since this method uses only final and
         * either immutable or thread-safe objects from {@link Store}. The only object that need synchronization
         * is {@link Store#source}, which is already synchronized.</p>
         *
         * @param  action  the action to execute.
         * @param  all     {@code true} for executing the given action on all remaining features.
         * @return {@code false} if there is no remaining feature after this method call.
         * @throws IOException if an I/O error occurred while reading a feature.
         * @throws IllegalArgumentException if parsing of a number failed, or other error.
         * @throws DateTimeException if parsing of a date failed.
         */
        private boolean read(final Consumer<? super Feature> action, final boolean all) throws IOException {
            final FixedSizeList elements = new FixedSizeList(values);
            String line;
            while ((line = source.readLine()) != null) {
                split(line, elements);
                final Feature feature = featureType.newInstance();
                int i, n = elements.size();
                for (i=0; i<n; i++) {
                    values[i] = converters[i].apply((String) values[i]);
                    feature.setPropertyValue(propertyNames[i], values[i]);
                }
                n = values.length;
                for (; i<n; i++) {
                    // For omitted elements, reuse previous value.
                    feature.setPropertyValue(propertyNames[i], values[i]);
                }
                action.accept(feature);
                if (!all) return true;
                elements.clear();
            }
            return false;
        }

        /**
         * Executes the given action only on the next feature, if any.
         */
        @Override
        public boolean tryAdvance(final Consumer<? super Feature> action) {
            try {
                return read(action, false);
            } catch (IOException | IllegalArgumentException | DateTimeException e) {
                throw new BackingStoreException(canNotParseFile(), e);
            }
        }

        /**
         * Executes the given action on all remaining features.
         */
        @Override
        public void forEachRemaining(final Consumer<? super Feature> action) {
            try {
                read(action, true);
            } catch (IOException | IllegalArgumentException | DateTimeException e) {
                throw new BackingStoreException(canNotParseFile(), e);
            }
        }

        /**
         * Current implementation can not split this iterator.
         */
        @Override
        public Spliterator<Feature> trySplit() {
            return null;
        }

        /**
         * We do not know the number of features.
         */
        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        /**
         * Returns the characteristics of the iteration over feature instances.
         * The iteration is assumed {@link #ORDERED} in the declaration order in the CSV file.
         * The iteration is {@link #NONNULL} (i.e. {@link #tryAdvance(Consumer)} is not allowed
         * to return null value) and {@link #IMMUTABLE} (i.e. we do not support modification of
         * the CSV file while an iteration is in progress).
         *
         * @return characteristics of iteration over the features in the CSV file.
         */
        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }

        /**
         * Invoked when a warning occurred while computing the geometry.
         */
        @Override
        public void accept(final LogRecord warning) {
            warning.setSourceClassName(Store.class.getName());
            warning.setSourceMethodName("stream");
            listeners.warning(warning);
        }
    }

    /**
     * Splits the content of the given line around the column separator.
     * Quotes are taken in account. The elements are added in the given list.
     *
     * @param line      the line to parse.
     * @param elements  an initially empty list where to add elements.
     */
    private static void split(final String line, final List<? super String> elements) {
        int startAt = 0;
        boolean isQuoting = false;        // If a quote has been opened and not yet closed.
        boolean hasQuotes = false;        // If the value contains at least one quote (not used for quoting the value).
        final int length = line.length();
        for (int i=0; i<length; i++) {
            switch (line.charAt(i)) {
                case QUOTE: {
                    hasQuotes = true;
                    if (isQuoting && i+1 < length && line.charAt(i+1) == QUOTE) {
                        i++;
                    } else {
                        isQuoting = !isQuoting;
                    }
                    break;
                }
                case SEPARATOR: {
                    if (!isQuoting) {
                        if (!elements.add(decode(line, startAt, i, hasQuotes))) {
                            return;     // Reached the maximal capacity of the list.
                        }
                        startAt = i+1;
                        hasQuotes = false;
                    }
                    break;
                }
            }
        }
        elements.add(decode(line, startAt, length, hasQuotes));
    }

    /**
     * Extracts a substring from the given line and replaces double quotes by single quotes.
     *
     * <div class="section">Departure from Moving Features specification</div>
     * The Moving Features specification said:
     *
     *   <blockquote>Some characters may need to be escaped here. {@literal <} (less than), {@literal >}
     *   (greater than), " (double quotation), ‘ (single quotation), and {@literal &} (ampersand) must be
     *   replaced with the entity references defined in XML. Space, tab, and comma are written in escape
     *   sequences \\s, \\t, and \\b, respectively.</blockquote>
     *
     * This part of the specification is currently ignored (its purpose is still unclear).
     */
    private static String decode(CharSequence text, final int lower, final int upper, final boolean hasQuotes) {
        if (hasQuotes) {
            final StringBuilder buffer = new StringBuilder(upper - lower).append(text, lower, upper);
            for (int i=0; i<buffer.length(); i++) {
                if (buffer.charAt(i) == QUOTE) {
                    buffer.deleteCharAt(i);
                    // If the deleted char was followed by another quote, that second quote will be preserved.
                }
            }
            text = CharSequences.trimWhitespaces(buffer);
        } else {
            text = CharSequences.trimWhitespaces(text, lower, upper);
        }
        return text.toString();
    }

    /**
     * Returns an error message for a duplicated element.
     */
    private String duplicated(final String name) {
        return errors().getString(Errors.Keys.DuplicatedElement_1, name);
    }

    /**
     * Returns the error message for a file that can not be parsed.
     * The error message will contain the line number if available.
     */
    final String canNotParseFile() {
        return IOUtilities.canNotReadFile(getLocale(), StoreProvider.NAME, getDisplayName(), source);
    }

    /**
     * Returns the resources to use for producing error messages.
     */
    private Errors errors() {
        return Errors.getResources(getLocale());
    }

    /**
     * Closes this data store and releases any underlying resources.
     *
     * @throws DataStoreException if an error occurred while closing this data store.
     */
    @Override
    public synchronized void close() throws DataStoreException {
        final BufferedReader s = source;
        source = null;                  // Cleared first in case of failure.
        if (s != null) try {
            s.close();
        } catch (IOException e) {
            throw new DataStoreException(e);
        }
    }
}
