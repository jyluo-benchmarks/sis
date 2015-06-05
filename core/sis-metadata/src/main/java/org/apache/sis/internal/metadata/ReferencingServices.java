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
package org.apache.sis.internal.metadata;

import java.util.Map;
import javax.measure.unit.Unit;
import javax.measure.quantity.Length;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.SingleCRS;
import org.opengis.referencing.crs.DerivedCRS;
import org.opengis.referencing.cs.CartesianCS;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransformFactory;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.referencing.operation.OperationMethod;
import org.opengis.referencing.operation.SingleOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.opengis.util.NoSuchIdentifierException;
import org.apache.sis.metadata.iso.extent.DefaultExtent;
import org.apache.sis.metadata.iso.extent.DefaultVerticalExtent;
import org.apache.sis.metadata.iso.extent.DefaultTemporalExtent;
import org.apache.sis.metadata.iso.extent.DefaultGeographicBoundingBox;
import org.apache.sis.metadata.iso.extent.DefaultSpatialTemporalExtent;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.internal.system.OptionalDependency;
import org.apache.sis.internal.system.Modules;
import org.apache.sis.io.wkt.FormattableObject;
import org.apache.sis.util.iso.DefaultNameSpace;
import org.apache.sis.util.Deprecable;
import org.apache.sis.util.resources.Errors;


/**
 * Provides access to services defined in the {@code "sis-referencing"} module.
 * This class searches for the {@link org.apache.sis.internal.referencing.ServicesForMetadata}
 * implementation using Java reflection.
 *
 * <p>This class also opportunistically defines some methods and constants related to
 * <cite>"referencing by coordinates"</cite> but needed by metadata.</p>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3
 * @version 0.6
 * @module
 */
public class ReferencingServices extends OptionalDependency {
    /**
     * The length of one nautical mile, which is {@value} metres.
     */
    public static final double NAUTICAL_MILE = 1852;

    /**
     * The GRS80 {@linkplain org.apache.sis.referencing.datum.DefaultEllipsoid#getAuthalicRadius() authalic radius},
     * which is {@value} metres.
     */
    public static final double AUTHALIC_RADIUS = 6371007;

    /**
     * The {@link org.apache.sis.referencing.datum.DefaultGeodeticDatum#BURSA_WOLF_KEY} value.
     */
    public static final String BURSA_WOLF_KEY = "bursaWolf";

    /**
     * The key for specifying explicitely the value to be returned by
     * {@link org.apache.sis.referencing.operation.DefaultConversion#getParameterValues()}.
     * It is usually not necessary to specify those parameters because they are inferred either from
     * the {@link MathTransform}, or specified explicitely in a {@code DefiningConversion}. However
     * there is a few cases, for example the Molodenski transform, where none of the above can apply,
     * because SIS implements those operations as a concatenation of math transforms, and such
     * concatenations do not have {@link org.opengis.parameter.ParameterValueGroup}.
     */
    public static final String PARAMETERS_KEY = "parameters";

    /**
     * The key for specifying a {@linkplain org.opengis.referencing.operation.MathTransformFactory}
     * instance to use for the construction of a geodetic object. This is usually not needed for CRS
     * construction, except in the special case of a derived CRS created from a defining conversion.
     */
    public static final String MT_FACTORY = "mtFactory";

    /**
     * The separator character between an identifier and its namespace in the argument given to
     * {@link #getOperationMethod(String)}. For example this is the separator in {@code "EPSG:9807"}.
     *
     * This is defined as a constant for now, but we may make it configurable in a future version.
     */
    private static final char IDENTIFIER_SEPARATOR = DefaultNameSpace.DEFAULT_SEPARATOR;

    /**
     * The services, fetched when first needed.
     */
    private static volatile ReferencingServices instance;

    /**
     * For subclass only. This constructor registers this instance as a {@link SystemListener}
     * in order to force a new {@code ReferencingServices} lookup if the classpath changes.
     */
    protected ReferencingServices() {
        super(Modules.METADATA, "sis-referencing");
    }

    /**
     * Invoked when the classpath changed. Resets the {@link #instance} to {@code null}
     * in order to force the next call to {@link #getInstance()} to fetch a new one,
     * which may be different.
     */
    @Override
    protected final void classpathChanged() {
        synchronized (ReferencingServices.class) {
            super.classpathChanged();
            instance = null;
        }
    }

    /**
     * Returns the singleton instance.
     *
     * @return The singleton instance.
     */
    public static ReferencingServices getInstance() {
        ReferencingServices c = instance;
        if (c == null) {
            synchronized (ReferencingServices.class) {
                c = instance;
                if (c == null) {
                    /*
                     * Double-checked locking: okay since Java 5 provided that the 'instance' field is volatile.
                     * In the particular case of this class, the intend is to ensure that SystemListener.add(…)
                     * is invoked only once.
                     */
                    c = getInstance(ReferencingServices.class, Modules.METADATA, "sis-referencing",
                            "org.apache.sis.internal.referencing.ServicesForMetadata");
                    if (c == null) {
                        c = new ReferencingServices();
                    }
                    instance = c;
                }
            }
        }
        return c;
    }




    ///////////////////////////////////////////////////////////////////////////////////////
    ////                                                                               ////
    ////                        SERVICES FOR ISO 19115 METADATA                        ////
    ////                                                                               ////
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets a geographic bounding box from the specified envelope.
     * If the envelope contains a CRS which is not geographic, then the bounding box will be transformed
     * to a geographic CRS (without datum shift if possible). Otherwise, the envelope is assumed already
     * in a geographic CRS using (<var>longitude</var>, <var>latitude</var>) axis order.
     *
     * @param  envelope The source envelope.
     * @param  target The target bounding box.
     * @throws TransformException if the given envelope can't be transformed.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     */
    public void setBounds(Envelope envelope, DefaultGeographicBoundingBox target) throws TransformException {
        throw moduleNotFound();
    }

    /**
     * Sets a vertical extent with the value inferred from the given envelope.
     * Only the vertical ordinates are extracted; all other ordinates are ignored.
     *
     * @param  envelope The source envelope.
     * @param  target The target vertical extent.
     * @throws TransformException if no vertical component can be extracted from the given envelope.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     */
    public void setBounds(Envelope envelope, DefaultVerticalExtent target) throws TransformException {
        throw moduleNotFound();
    }

    /**
     * Sets a temporal extent with the value inferred from the given envelope.
     * Only the temporal ordinates are extracted; all other ordinates are ignored.
     *
     * @param  envelope The source envelope.
     * @param  target The target temporal extent.
     * @throws TransformException if no temporal component can be extracted from the given envelope.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     */
    public void setBounds(Envelope envelope, DefaultTemporalExtent target) throws TransformException {
        throw moduleNotFound();
    }

    /**
     * Sets the geographic, vertical and temporal extents with the values inferred from the given envelope.
     * If the given {@code target} has more geographic or vertical extents than needed (0 or 1), then the
     * extraneous extents are removed.
     *
     * <p>Behavior regarding missing dimensions:</p>
     * <ul>
     *   <li>If the given envelope has no horizontal component, then all geographic extents are removed
     *       from the given {@code target}. Non-geographic extents (e.g. descriptions and polygons) are
     *       left unchanged.</li>
     *   <li>If the given envelope has no vertical component, then the vertical extent is set to {@code null}.</li>
     *   <li>If the given envelope has no temporal component, then the temporal extent is set to {@code null}.</li>
     * </ul>
     *
     * @param  envelope The source envelope.
     * @param  target The target spatio-temporal extent.
     * @throws TransformException if no temporal component can be extracted from the given envelope.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     */
    public void setBounds(Envelope envelope, DefaultSpatialTemporalExtent target) throws TransformException {
        throw moduleNotFound();
    }

    /**
     * Initializes a horizontal, vertical and temporal extent with the values inferred from the given envelope.
     *
     * @param  envelope The source envelope.
     * @param  target The target extent.
     * @throws TransformException if a coordinate transformation was required and failed.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     */
    public void addElements(Envelope envelope, DefaultExtent target) throws TransformException {
        throw moduleNotFound();
    }




    ///////////////////////////////////////////////////////////////////////////////////////
    ////                                                                               ////
    ////                          SERVICES FOR WKT FORMATTING                          ////
    ////                                                                               ////
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a fully implemented parameter descriptor.
     *
     * @param  parameter A partially implemented parameter descriptor, or {@code null}.
     * @return A fully implemented parameter descriptor, or {@code null} if the given argument was null.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     *
     * @since 0.5
     */
    public ParameterDescriptor<?> toImplementation(ParameterDescriptor<?> parameter) {
        throw moduleNotFound();
    }

    /**
     * Converts the given object in a {@code FormattableObject} instance.
     *
     * @param  object The object to wrap.
     * @return The given object converted to a {@code FormattableObject} instance.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     *
     * @see org.apache.sis.referencing.AbstractIdentifiedObject#castOrCopy(IdentifiedObject)
     *
     * @since 0.4
     */
    public FormattableObject toFormattableObject(IdentifiedObject object) {
        throw moduleNotFound();
    }

    /**
     * Converts the given object in a {@code FormattableObject} instance. Callers should verify that the given
     * object is not already an instance of {@code FormattableObject} before to invoke this method. This method
     * returns {@code null} if it can not convert the object.
     *
     * @param  object The object to wrap.
     * @param  internal {@code true} if the formatting convention is {@code Convention.INTERNAL}.
     * @return The given object converted to a {@code FormattableObject} instance, or {@code null}.
     * @throws UnsupportedOperationException if the {@code "sis-referencing"} module has not been found on the classpath.
     *
     * @since 0.6
     */
    public FormattableObject toFormattableObject(MathTransform object, boolean internal) {
        throw moduleNotFound();
    }




    ///////////////////////////////////////////////////////////////////////////////////////
    ////                                                                               ////
    ////                           SERVICES FOR WKT PARSING                            ////
    ////                                                                               ////
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the Greenwich prime meridian.
     *
     * @return The Greenwich prime meridian.
     *
     * @since 0.6
     */
    public PrimeMeridian getGreenwich() {
        throw moduleNotFound();
    }

    /**
     * Returns the coordinate system of a geocentric CRS using axes in the given unit of measurement.
     *
     * @param  unit The unit of measurement for the geocentric CRS axes.
     * @return The coordinate system for a geocentric CRS with axes using the given unit of measurement.
     *
     * @since 0.6
     */
    public CartesianCS getGeocentricCS(final Unit<Length> unit) {
        throw moduleNotFound();
    }

    /**
     * Converts a geocentric coordinate system from the legacy WKT 1 to the current ISO 19111 standard.
     * This method replaces the (Other, East, North) directions by (Geocentric X, Geocentric Y, Geocentric Z).
     *
     * @param  cs The geocentric coordinate system to upgrade.
     * @return The upgraded coordinate system, or {@code cs} if this method can not upgrade the given CS.
     *
     * @since 0.6
     */
    public CartesianCS upgradeGeocentricCS(final CartesianCS cs) {
        return cs;
    }

    /**
     * Creates a coordinate system of unknown type. This method is used during parsing of WKT version 1,
     * since that legacy format did not specified any information about the coordinate system in use.
     * This method should not need to be invoked for parsing WKT version 2.
     *
     * @param  axes The axes of the unknown coordinate system.
     * @return An "abstract" coordinate system using the given axes.
     *
     * @since 0.6
     */
    public CoordinateSystem createAbstractCS(final CoordinateSystemAxis[] axes) {
        throw moduleNotFound();
    }

    /**
     * Creates a derived CRS from the information found in a WKT 1 {@code FITTED_CS} element.
     * This coordinate system can not be easily constructed from the information provided by the WKT 1 format.
     * Note that this method is needed only for WKT 1 parsing, since WKT provides enough information for using
     * the standard factories.
     *
     * @param  properties    The properties to be given to the {@code DerivedCRS} and {@code Conversion} objects.
     * @param  baseCRS       Coordinate reference system to base the derived CRS on.
     * @param  method        The coordinate operation method (mandatory in all cases).
     * @param  baseToDerived Transform from positions in the base CRS to positions in this target CRS.
     * @param  derivedCS     The coordinate system for the derived CRS.
     * @return The newly created derived CRS, potentially implementing an additional CRS interface.
     *
     * @since 0.6
     */
    public DerivedCRS createDerivedCRS(final Map<String,?>    properties,
                                       final SingleCRS        baseCRS,
                                       final OperationMethod  method,
                                       final MathTransform    baseToDerived,
                                       final CoordinateSystem derivedCS)
    {
        throw moduleNotFound();
    }

    /**
     * Creates the {@code TOWGS84} element during parsing of a WKT version 1. This is an optional operation:
     * this method is allowed to return {@code null} if the "sis-referencing" module is not in the classpath.
     *
     * @param  values The 7 Bursa-Wolf parameter values.
     * @return The {@link org.apache.sis.referencing.datum.BursaWolfParameters}, or {@code null}.
     *
     * @since 0.6
     */
    public Object createToWGS84(final double[] values) {
        return null;
    }

    /**
     * Returns the coordinate operation factory to use for the given properties and math transform factory.
     * If the given properties are empty and the {@code mtFactory} is the system default, then this method
     * returns the system default {@code CoordinateOperationFactory} instead of creating a new one.
     *
     * @param  properties The default properties.
     * @param  mtFactory  The math transform factory to use.
     * @return The coordinate operation factory to use.
     *
     * @since 0.6
     */
    public CoordinateOperationFactory getCoordinateOperationFactory(Map<String,?> properties, MathTransformFactory mtFactory) {
        /*
         * The check for 'properties' and 'mtFactory' is performed by the ServicesForMetadata subclass. If this code is
         * executed, this means that the "sis-referencing" module is not on the classpath, in which case we do not know
         * how to pass the 'properties' and 'mtFactory' arguments to the foreigner CoordinateOperationFactory anyway.
         */
        final CoordinateOperationFactory factory = DefaultFactories.forClass(CoordinateOperationFactory.class);
        if (factory != null) {
            return factory;
        } else {
            throw moduleNotFound();
        }
    }

    /**
     * Returns {@code true} if the {@linkplain org.apache.sis.referencing.AbstractIdentifiedObject#getName()
     * primary name} or an aliases of the given object matches the given name. The comparison ignores case,
     * some Latin diacritical signs and any characters that are not letters or digits.
     *
     * @param  object The object for which to check the name or alias.
     * @param  name The name to compare with the object name or aliases.
     * @return {@code true} if the primary name of at least one alias matches the specified {@code name}.
     *
     * @since 0.6
     */
    public boolean isHeuristicMatchForName(final IdentifiedObject object, final String name) {
        return NameToIdentifier.isHeuristicMatchForName(object.getName(), object.getAlias(), name);
    }

    /**
     * Returns {@code true} if the name or an identifier of the given method matches the given {@code identifier}.
     *
     * @param  method     The method to test for a match.
     * @param  identifier The name or identifier of the operation method to search.
     * @return {@code true} if the given method is a match for the given identifier.
     *
     * @since 0.6
     */
    private boolean matches(final OperationMethod method, final String identifier) {
        if (isHeuristicMatchForName(method, identifier)) {
            return true;
        }
        for (int s = identifier.indexOf(IDENTIFIER_SEPARATOR); s >= 0;
                 s = identifier.indexOf(IDENTIFIER_SEPARATOR, s))
        {
            final String codespace = identifier.substring(0, s).trim();
            final String code = identifier.substring(++s).trim();
            for (final ReferenceIdentifier id : method.getIdentifiers()) {
                if (codespace.equalsIgnoreCase(id.getCodeSpace()) && code.equalsIgnoreCase(id.getCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the operation method for the specified name or identifier. The given argument shall be either a
     * method name (e.g. <cite>"Transverse Mercator"</cite>) or one of its identifiers (e.g. {@code "EPSG:9807"}).
     *
     * @param  methods The method candidates.
     * @param  identifier The name or identifier of the operation method to search.
     * @return The coordinate operation method for the given name or identifier, or {@code null} if none.
     *
     * @see org.apache.sis.referencing.operation.DefaultCoordinateOperationFactory#getOperationMethod(String)
     * @see org.apache.sis.referencing.operation.transform.DefaultMathTransformFactory#getOperationMethod(String)
     *
     * @since 0.6
     */
    public final OperationMethod getOperationMethod(final Iterable<? extends OperationMethod> methods, final String identifier) {
        OperationMethod fallback = null;
        for (final OperationMethod method : methods) {
            if (matches(method, identifier)) {
                /*
                 * Stop the iteration at the first non-deprecated method.
                 * If we find only deprecated methods, take the first one.
                 */
                if (!(method instanceof Deprecable) || !((Deprecable) method).isDeprecated()) {
                    return method;
                }
                if (fallback == null) {
                    fallback = method;
                }
            }
        }
        return fallback;
    }

    /**
     * Returns the coordinate operation method for the given classification.
     * This method checks if the given {@code opFactory} is a SIS implementation
     * before to fallback on a slower fallback.
     *
     * @param  opFactory  The coordinate operation factory to use if it is a SIS implementation.
     * @param  mtFactory  The math transform factory to use as a fallback.
     * @param  identifier The name or identifier of the operation method to search.
     * @return The coordinate operation method for the given name or identifier.
     * @throws FactoryException if an error occurred which searching for the given method.
     *
     * @since 0.6
     */
    public OperationMethod getOperationMethod(final CoordinateOperationFactory opFactory,
            final MathTransformFactory mtFactory, final String identifier) throws FactoryException
    {
        final OperationMethod method = getOperationMethod(mtFactory.getAvailableMethods(SingleOperation.class), identifier);
        if (method != null) {
            return method;
        }
        throw new NoSuchIdentifierException(Errors.format(Errors.Keys.NoSuchOperationMethod_1, identifier), identifier);
    }
}
