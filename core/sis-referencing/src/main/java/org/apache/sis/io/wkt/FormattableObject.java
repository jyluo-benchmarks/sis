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
package org.apache.sis.io.wkt;

import java.io.Console;
import java.io.PrintWriter;
import org.opengis.parameter.GeneralParameterValue;
import org.apache.sis.util.Debug;
import org.apache.sis.util.Classes;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.internal.util.X364;


/**
 * Base class for objects that can be formatted as <cite>Well Known Text</cite> (WKT).
 * Almost every Apache SIS implementations of referencing objects extend this class.
 *
 * <p>This class provides two methods for getting a default <cite>Well Known Text</cite>
 * representation of this object:</p>
 *
 * <ul>
 *   <li>{@link #toWKT()} returns a strictly compliant WKT or throw an exception.</li>
 *   <li>{@link #toString()} is like {@code toWKT()} with some rules relaxed in order
 *       to never throw exception.</li>
 * </ul>
 *
 * The {@code toWKT()} method may throw {@link UnformattableObjectException} if an object
 * contains elements not defined by the official standard, while {@code toString()} can
 * fallback on a non-standard representation.
 *
 * {@section Syntax coloring}
 * A convenience {@link #print()} method is provided, which is roughly equivalent to
 * {@code System.out.println(this)} except that syntax coloring is automatically applied
 * if the terminal seems to support the ANSI escape codes.
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @since   0.4 (derived from geotk-2.0)
 * @version 0.4
 * @module
 */
public class FormattableObject implements Formattable {
    /**
     * The formatter for the {@link #toWKT()} and {@link #toString()} methods.
     * Formatters are not thread-safe, consequently we need a different instance for each thread.
     * We do not use synchronization because the formatter will call back user's code, which
     * introduce a risk of thread lock if the user performs his own synchronization.
     */
    private static final ThreadLocal<Formatter> FORMATTER = new ThreadLocal<Formatter>();

    /**
     * Default constructor.
     */
    protected FormattableObject() {
    }

    /**
     * Returns a strictly compliant <cite>Well Known Text</cite> (WKT) using the default convention,
     * symbols and indentation. If this object can not be represented in a standard way, then this
     * method throws an {@link UnformattableObjectException}.
     *
     * @return The default Well Know Text representation of this object.
     * @throws UnformattableObjectException If this object can not be formatted as a standard WKT.
     *
     * @see org.opengis.referencing.IdentifiedObject#toWKT()
     */
    public String toWKT() throws UnformattableObjectException {
        return formatWKT(Convention.OGC, false, true);
    }

    /**
     * Returns a <cite>Well Known Text</cite> (WKT) using the default convention, symbols and indentation.
     * If this object can not be represented in a standard way, then this method fallbacks on a non-standard
     * representation.
     *
     * @return The Well Known Text (WKT) or a pseudo-WKT representation of this object.
     */
    @Override
    public String toString() {
        return formatWKT(Convention.OGC, false, false);
    }

    /**
     * Returns a <cite>Well Known Text</cite> (WKT) for this object using the specified convention.
     * The convention is usually {@link Convention#OGC OGC}, but other common conventions are
     * {@link Convention#GEOTIFF GEOTIFF} and {@link Convention#EPSG EPSG}.
     * The {@link Convention#INTERNAL INTERNAL} convention is a special value for debugging map projections.
     *
     * @param  convention The WKT convention to use.
     * @return The Well Known Text (WKT) or a pseudo-WKT representation of this object.
     */
    public String toString(final Convention convention) {
        ArgumentChecks.ensureNonNull("convention", convention);
        return formatWKT(convention, false, false);
    }

    /**
     * Prints a WKT representation of this object to the {@linkplain System#out standard output stream}.
     * If a {@linkplain Console console} is attached to the running JVM (i.e. if the application is run
     * from the command-line and the output is not redirected to a file) and if Apache SIS thinks that
     * the console supports the ANSI escape codes (a.k.a. X3.64), then a syntax coloring will be applied.
     *
     * <p>This is a convenience method for debugging purpose and for console applications.</p>
     */
    @Debug
    public void print() {
        final Console console = System.console();
        final PrintWriter out = (console != null) ? console.writer() : null;
        final String wkt = formatWKT(Convention.OGC, (out != null) && X364.isAnsiSupported(), false);
        if (out != null) {
            out.println(wkt);
        } else {
            System.out.println(wkt);
        }
    }

    /**
     * Returns a WKT for this object using the specified convention.
     * If {@code strict} is true, then an exception is thrown if the WKT is not standard-compliant.
     *
     * @param  convention The convention for choosing WKT entities names.
     * @param  colorize   {@code true} for applying syntax coloring, or {@code false} otherwise.
     * @param  strict     {@code true} if an exception shall be thrown for unformattable objects,
     *                    or {@code false} for providing a non-standard formatting instead.
     * @return The Well Known Text (WKT) or a pseudo-WKT representation of this object.
     * @throws UnformattableObjectException If {@code strict} is {@code true} and this object can not be formatted.
     */
    private String formatWKT(final Convention convention, final boolean colorize, final boolean strict)
             throws UnformattableObjectException
    {
        Formatter formatter = FORMATTER.get();
        if (formatter == null) {
            formatter = new Formatter();
            FORMATTER.set(formatter);
        }
        formatter.colors = colorize ? Colors.DEFAULT : null;
        formatter.setConvention(convention, null);
        try {
            /*
             * Special processing for parameter values, which is formatted
             * directly in 'Formatter'. Note that in GeoAPI, that interface
             * doesn't share the same parent interface than other interfaces.
             */
            if (this instanceof GeneralParameterValue) {
                formatter.append((GeneralParameterValue) this);
            } else {
                formatter.append(this);
            }
            if (strict) {
                formatter.ensureValidWKT();
            }
            return formatter.toString();
        } finally {
            formatter.clear();
        }
    }

    /**
     * {@inheritDoc}
     *
     * The default implementation declares that this object produces an invalid WKT.
     * Subclasses shall override this method for proper WKT formatting and shall <strong>not</strong>
     * invoke {@code super.formatTo(formatter)} if they can use a standard WKT syntax.
     *
     * @see #toWKT()
     * @see #toString()
     */
    @Override
    public String formatTo(final Formatter formatter) {
        Class<?> type = getClass();
        for (final Class<?> candidate : type.getInterfaces()) {
            final String name = candidate.getName();
            if (name.startsWith("org.opengis.") && !name.startsWith("org.opengis.util.")) {
                type = candidate;
                break;
            }
        }
        final String name = Classes.getShortName(type);
        formatter.setInvalidWKT(name);
        return name;
    }
}