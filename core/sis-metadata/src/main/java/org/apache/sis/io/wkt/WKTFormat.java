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

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.io.IOException;
import java.text.Format;
import java.text.NumberFormat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.text.ParseException;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;
import org.opengis.util.Factory;
import org.opengis.util.InternationalString;
import org.opengis.metadata.citation.Citation;
import org.opengis.referencing.IdentifiedObject;
import org.apache.sis.io.CompoundFormat;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.resources.Errors;


/**
 * Parser and formatter for <cite>Well Known Text</cite> (WKT) objects.
 * This format handles a pair of {@link Parser} and {@link Formatter},
 * to be used by {@code parse} and {@code format} methods respectively.
 * {@code WKTFormat} objects allow the following configuration:
 *
 * <ul>
 *   <li>The preferred authority of {@linkplain IdentifiedObject#getName() object name} to
 *       format (see {@link Formatter#getNameAuthority()} for more information).</li>
 *   <li>The {@linkplain Symbols symbols} to use (curly braces or brackets, <i>etc</i>).</li>
 *   <li>The {@linkplain CharEncoding character encoding} (i.e. replacements to use for Unicode characters).</li>
 *   <li>Whether ANSI X3.64 colors are allowed or not (default is not).</li>
 *   <li>The indentation.</li>
 * </ul>
 *
 * <div class="section">String expansion</div>
 * Because the strings to be parsed by this class are long and tend to contain repetitive substrings,
 * {@code WKTFormat} provides a mechanism for performing string substitutions before the parsing take place.
 * Long strings can be assigned short names by calls to the
 * <code>{@linkplain #definitions()}.put(<var>key</var>,<var>value</var>)</code> method.
 * After definitions have been added, any call to a parsing method will replace all occurrences
 * of a short name by the associated long string.
 *
 * <p>The short names must comply with the rules of Java identifiers. It is recommended, but not
 * required, to prefix the names by some symbol like {@code "$"} in order to avoid ambiguity.
 * Note however that this class doesn't replace occurrences between quoted text, so string
 * expansion still relatively safe even when used with non-prefixed identifiers.</p>
 *
 * <div class="note"><b>Example:</b>
 * In the example below, the {@code $WGS84} substring which appear in the argument given to the
 * {@code parseObject(…)} method will be expanded into the full {@code GEOGCS["WGS84", …]} string
 * before the parsing proceed.
 *
 * <blockquote><code>{@linkplain #definitions()}.put("$WGS84", "GEOGCS[\"WGS84\", DATUM[</code> <i>…etc…</i> <code>]]);<br>
 * Object crs = {@linkplain #parseObject(String) parseObject}("PROJCS[\"Mercator_1SP\", <strong>$WGS84</strong>,
 * PROJECTION[</code> <i>…etc…</i> <code>]]");</code></blockquote>
 * </div>
 *
 * <div class="section">Limitations</div>
 * <ul>
 *   <li>Instances of this class are not synchronized for multi-threading.
 *       It is recommended to create separated format instances for each thread.
 *       If multiple threads access a {@code WKTFormat} concurrently, it must be synchronized externally.</li>
 *   <li>Serialized objects of this class are not guaranteed to be compatible with future Apache SIS releases.
 *       Serialization support is appropriate for short term storage or RMI between applications running the
 *       same version of Apache SIS.</li>
 * </ul>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @author  Rémi Eve (IRD)
 * @since   0.4
 * @version 0.6
 * @module
 *
 * @see <a href="http://docs.opengeospatial.org/is/12-063r5/12-063r5.html">WKT 2 specification</a>
 * @see <a href="http://www.geoapi.org/3.0/javadoc/org/opengis/referencing/doc-files/WKT.html">Legacy WKT 1</a>
 */
public class WKTFormat extends CompoundFormat<Object> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -2909110214650709560L;

    /**
     * The indentation value to give to the {@link #setIndentation(int)}
     * method for formatting the complete object on a single line.
     */
    public static final int SINGLE_LINE = -1;

    /**
     * The default indentation value.
     */
    static final byte DEFAULT_INDENTATION = 2;

    /**
     * The pattern of dates.
     *
     * @see #createFormat(Class)
     */
    static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SX";

    /**
     * Short version of {@link #DATE_PATTERN}, to be used when formatting temporal extents
     * if the duration is at least {@link Formatter#TEMPORAL_THRESHOLD}. This pattern must
     * be a prefix of {@link #DATE_PATTERN}, since we will use that condition for deciding
     * if this pattern is really shorter (the user could have created his own date format
     * with a different pattern).
     */
    static final String SHORT_DATE_PATTERN = "yyyy-MM-dd";

    /**
     * The symbols to use for this formatter.
     * The same object is also referenced in the {@linkplain #parser} and {@linkplain #formatter}.
     * It appears here for serialization purpose.
     */
    private Symbols symbols;

    /**
     * The colors to use for this formatter, or {@code null} for no syntax coloring.
     * The same object is also referenced in the {@linkplain #formatter}.
     * It appears here for serialization purpose.
     */
    private Colors colors;

    /**
     * The convention to use. The same object is also referenced in the {@linkplain #formatter}.
     * It appears here for serialization purpose.
     */
    private Convention convention;

    /**
     * The preferred authority for objects or parameter names. A {@code null} value
     * means that the authority shall be inferred from the {@linkplain #convention}.
     */
    private Citation authority;

    /**
     * Whether WKT keywords shall be formatted in upper case.
     */
    private KeywordCase keywordCase;

    /**
     * {@link CharEncoding#UNICODE} for preserving non-ASCII characters. The default value is
     * {@link CharEncoding#DEFAULT}, which causes replacements like "é" → "e" in all elements
     * except {@code REMARKS["…"]}. May also be a user-supplied encoding.
     *
     * <p>A {@code null} value means to infer this property from the {@linkplain #convention}.</p>
     */
    private CharEncoding encoding;

    /**
     * The amount of spaces to use in indentation, or {@value #SINGLE_LINE} if indentation is disabled.
     * The same value is also stored in the {@linkplain #formatter}.
     * It appears here for serialization purpose.
     */
    private byte indentation;

    /**
     * A formatter using the same symbols than the {@linkplain #parser}.
     * Will be created by the {@link #format(Object, Appendable)} method when first needed.
     */
    private transient Formatter formatter;

    /**
     * The parser. Will be created when first needed.
     */
    private transient AbstractParser parser;

    /**
     * The factories needed by the parser. Will be created when first needed.
     */
    private transient Map<Class<?>,Factory> factories;

    /**
     * The warning produced by the last parsing or formatting operation, or {@code null} if none.
     *
     * @see #getWarnings()
     */
    private transient Warnings warnings;

    /**
     * Creates a format for the given locale and timezone. The given locale will be used for
     * {@link InternationalString} localization; this is <strong>not</strong> the locale for number format.
     *
     * @param locale   The locale for the new {@code Format}, or {@code null} for {@code Locale.ROOT}.
     * @param timezone The timezone, or {@code null} for UTC.
     */
    public WKTFormat(final Locale locale, final TimeZone timezone) {
        super(locale, timezone);
        convention  = Convention.DEFAULT;
        symbols     = Symbols.getDefault();
        keywordCase = KeywordCase.DEFAULT;
        indentation = DEFAULT_INDENTATION;
    }

    /**
     * Returns the locale for the given category. This method implements the following mapping:
     *
     * <ul>
     *   <li>{@link java.util.Locale.Category#FORMAT}: the value of {@link Symbols#getLocale()},
     *       normally fixed to {@link Locale#ROOT}, used for number formatting.</li>
     *   <li>{@link java.util.Locale.Category#DISPLAY}: the {@code locale} given at construction time,
     *       used for {@link InternationalString} localization.</li>
     * </ul>
     *
     * @param  category The category for which a locale is desired.
     * @return The locale for the given category (never {@code null}).
     */
    @Override
    public Locale getLocale(final Locale.Category category) {
        if (category == Locale.Category.FORMAT) {
            return symbols.getLocale();
        }
        return super.getLocale(category);
    }

    /**
     * Returns the symbols used for parsing and formatting WKT.
     *
     * @return The current set of symbols used for parsing and formatting WKT.
     */
    public Symbols getSymbols() {
        return symbols;
    }

    /**
     * Sets the symbols used for parsing and formatting WKT.
     *
     * @param symbols The new set of symbols to use for parsing and formatting WKT.
     */
    public void setSymbols(final Symbols symbols) {
        ArgumentChecks.ensureNonNull("symbols", symbols);
        if (!symbols.equals(this.symbols)) {
            this.symbols = symbols.immutable();
            formatter = null;
            parser = null;
        }
    }

    /**
     * Returns a mapper between Java character sequences and the characters to write in WKT.
     * The intend is to specify how to write characters that are not allowed in WKT strings
     * according ISO 19162 specification. Return values can be:
     *
     * <ul>
     *   <li>{@link CharEncoding#DEFAULT} for performing replacements like "é" → "e"
     *       in all WKT elements except {@code REMARKS["…"]}.</li>
     *   <li>{@link CharEncoding#UNICODE} for preserving non-ASCII characters.</li>
     *   <li>Any other user-supplied mapping.</li>
     * </ul>
     *
     * @return The mapper between Java character sequences and the characters to write in WKT.
     *
     * @since 0.6
     */
    public CharEncoding getCharEncoding() {
        CharEncoding result = encoding;
        if (result == null) {
            result = (convention == Convention.INTERNAL) ? CharEncoding.UNICODE : CharEncoding.DEFAULT;
        }
        return result;
    }

    /**
     * Sets the mapper between Java character sequences and the characters to write in WKT.
     *
     * <p>If this method is never invoked, or if this method is invoked with a {@code null} value,
     * then the default mapper is {@link CharEncoding#DEFAULT} except for WKT formatted according
     * the {@linkplain Convention#INTERNAL internal convention}.</p>
     *
     * @param encoding The new mapper to use, or {@code null} for restoring the default value.
     *
     * @since 0.6
     */
    public void setCharEncoding(final CharEncoding encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns whether non-ASCII characters are preserved. The default value is {@code false},
     * which causes replacements like "é" → "e" in all elements except {@link ElementKind#REMARKS}.
     *
     * <p>This value is always {@code true} when the WKT {@linkplain #getConvention() convention}
     * is set to {@link Convention#INTERNAL}.</p>
     *
     * @return Whether non-ASCII characters are preserved.
     *
     * @since 0.5
     *
     * @deprecated Replaced by {@link #getCharEncoding()}.
     */
    @Deprecated
    public boolean isNonAsciiAllowed() {
        return getCharEncoding() == CharEncoding.UNICODE;
    }

    /**
     * Sets whether non-ASCII characters shall be preserved. The default value is {@code false},
     * which causes replacements like "é" → "e" in all elements except {@link ElementKind#REMARKS}.
     * Setting this property to {@code true} will disable such replacements.
     *
     * @param allowed Whether non-ASCII characters shall be preserved.
     *
     * @since 0.5
     *
     * @deprecated Replaced by {@link #setCharEncoding(CharEncoding)}.
     */
    @Deprecated
    public void setNonAsciiAllowed(final boolean allowed) {
        setCharEncoding(allowed ? CharEncoding.UNICODE : CharEncoding.DEFAULT);
    }

    /**
     * Returns whether WKT keywords should be written with upper cases or camel cases.
     *
     * @return The case to use for formatting keywords.
     */
    public KeywordCase getKeywordCase() {
        return keywordCase;
    }

    /**
     * Sets whether WKT keywords should be written with upper cases or camel cases.
     *
     * @param keywordCase The case to use for formatting keywords.
     */
    public void setKeywordCase(final KeywordCase keywordCase) {
        ArgumentChecks.ensureNonNull("keywordCase", keywordCase);
        this.keywordCase = keywordCase;
        updateFormatter(formatter);
    }

    /**
     * Returns the colors to use for syntax coloring, or {@code null} if none.
     * By default there is no syntax coloring.
     *
     * @return The colors for syntax coloring, or {@code null} if none.
     */
    public Colors getColors() {
        return colors;
    }

    /**
     * Sets the colors to use for syntax coloring.
     * This property applies only when formatting text.
     *
     * <p>Newly created {@code WKTFormat}s have no syntax coloring. If a non-null argument like
     * {@link Colors#DEFAULT} is given to this method, then the {@link #format(Object, Appendable) format(…)}
     * method tries to highlight most of the elements that are relevant to
     * {@link org.apache.sis.util.Utilities#equalsIgnoreMetadata(Object, Object)}.</p>
     *
     * @param colors The colors for syntax coloring, or {@code null} if none.
     */
    public void setColors(Colors colors) {
        if (colors != null) {
            colors = colors.immutable();
        }
        this.colors = colors;
        updateFormatter(formatter);
    }

    /**
     * Returns the convention for parsing and formatting WKT elements.
     * The default value is {@link Convention#WKT2}.
     *
     * @return The convention to use for formatting WKT elements (never {@code null}).
     */
    public Convention getConvention() {
        return convention;
    }

    /**
     * Sets the convention for parsing and formatting WKT elements.
     *
     * @param convention The new convention to use for parsing and formatting WKT elements.
     */
    public void setConvention(final Convention convention) {
        ArgumentChecks.ensureNonNull("convention", convention);
        this.convention = convention;
        updateFormatter(formatter);
        parser = null;
    }

    /**
     * Returns the preferred authority to look for when fetching identified object names and identifiers.
     * The difference between various authorities are most easily seen in projection and parameter names.
     *
     * <div class="note"><b>Example:</b>
     * The following table shows the names given by various organizations or projects for the same projection:
     *
     * <table class="sis">
     *   <caption>Projection name examples</caption>
     *   <tr><th>Authority</th> <th>Projection name</th></tr>
     *   <tr><td>EPSG</td>      <td>Mercator (variant A)</td></tr>
     *   <tr><td>OGC</td>       <td>Mercator_1SP</td></tr>
     *   <tr><td>GEOTIFF</td>   <td>CT_Mercator</td></tr>
     * </table></div>
     *
     * If no authority has been {@link #setNameAuthority(Citation) explicitly set}, then this
     * method returns the default authority for the current {@linkplain #getConvention() convention}.
     *
     * @return The organization, standard or project to look for when fetching projection and parameter names.
     *
     * @see Formatter#getNameAuthority()
     */
    public Citation getNameAuthority() {
        Citation result = authority;
        if (result == null) {
            result = convention.getNameAuthority();
        }
        return result;
    }

    /**
     * Sets the preferred authority for choosing the projection and parameter names.
     * If non-null, the given priority will have precedence over the authority usually
     * associated to the {@linkplain #getConvention() convention}. A {@code null} value
     * restore the default behavior.
     *
     * @param authority The new authority, or {@code null} for inferring it from the convention.
     *
     * @see Formatter#getNameAuthority()
     */
    public void setNameAuthority(final Citation authority) {
        this.authority = authority;
        updateFormatter(formatter);
        // No need to update the parser.
    }

    /**
     * Updates the formatter convention, authority, colors and indentation according the current state of this
     * {@code WKTFormat}. The authority may be null, in which case it will be inferred from the convention when
     * first needed.
     */
    private void updateFormatter(final Formatter formatter) {
        if (formatter != null) {
            final boolean toUpperCase;
            switch (keywordCase) {
                case UPPER_CASE: toUpperCase = true;  break;
                case CAMEL_CASE: toUpperCase = false; break;
                default: toUpperCase = (convention.majorVersion() == 1); break;
            }
            formatter.configure(convention, authority, colors, toUpperCase, indentation);
            if (encoding != null) {
                formatter.encoding = encoding;
            }
        }
    }

    /**
     * Returns the current indentation to be used for formatting objects.
     * The {@value #SINGLE_LINE} value means that the whole WKT is to be formatted on a single line.
     *
     * @return The current indentation.
     */
    public int getIndentation() {
        return indentation;
    }

    /**
     * Sets a new indentation to be used for formatting objects.
     * The {@value #SINGLE_LINE} value means that the whole WKT is to be formatted on a single line.
     *
     * @param indentation The new indentation to use.
     */
    public void setIndentation(final int indentation) {
        ArgumentChecks.ensureBetween("indentation", SINGLE_LINE, Byte.MAX_VALUE, indentation);
        this.indentation = (byte) indentation;
        updateFormatter(formatter);
    }

    /**
     * Returns the type of objects formatted by this class. This method has to return {@code Object.class}
     * since it is the only common parent to all object types accepted by this formatter.
     *
     * @return {@code Object.class}
     */
    @Override
    public final Class<Object> getValueType() {
        return Object.class;
    }

    /**
     * Creates an object from the given character sequence.
     * The parsing begins at the index given by the {@code pos} argument.
     *
     * @param  text The character sequence for the object to parse.
     * @param  pos  The position where to start the parsing.
     * @return The parsed object.
     * @throws ParseException If an error occurred while parsing the object.
     */
    @Override
    public Object parse(final CharSequence text, final ParsePosition pos) throws ParseException {
        warnings = null;
        ArgumentChecks.ensureNonNull("text", text);
        ArgumentChecks.ensureNonNull("pos",  pos);
        AbstractParser parser = this.parser;
        if (parser == null) {
            if (factories == null) {
                factories = new HashMap<>();
            }
            this.parser = parser = new GeodeticObjectParser(symbols,
                    (NumberFormat) getFormat(Number.class),
                    (DateFormat)   getFormat(Date.class),
                    (UnitFormat)   getFormat(Unit.class),
                    convention, getLocale(), factories);
        }
        Object object = null;
        try {
            return object = parser.parseObject(text.toString(), pos);
        } finally {
            warnings = parser.getAndClearWarnings(object);
        }
    }

    /**
     * Formats the specified object as a Well Know Text. The formatter accepts at least the following types:
     * {@link FormattableObject}, {@link IdentifiedObject},
     * {@link org.opengis.referencing.operation.MathTransform},
     * {@link org.opengis.metadata.extent.GeographicBoundingBox},
     * {@link org.opengis.metadata.extent.VerticalExtent},
     * {@link org.opengis.metadata.extent.TemporalExtent}
     * and {@link Unit}.
     *
     * @param  object     The object to format.
     * @param  toAppendTo Where the text is to be appended.
     * @throws IOException If an error occurred while writing to {@code toAppendTo}.
     *
     * @see FormattableObject#toWKT()
     */
    @Override
    public void format(final Object object, final Appendable toAppendTo) throws IOException {
        warnings = null;
        ArgumentChecks.ensureNonNull("object",     object);
        ArgumentChecks.ensureNonNull("toAppendTo", toAppendTo);
        /*
         * If the given Appendable is not a StringBuffer, creates a temporary StringBuffer.
         * We can not write directly in an arbitrary Appendable because Formatter needs the
         * ability to go backward ("append only" is not sufficient), and because it passes
         * the buffer to other java.text.Format instances which work only with StringBuffer.
         */
        final StringBuffer buffer;
        if (toAppendTo instanceof StringBuffer) {
            buffer = (StringBuffer) toAppendTo;
        } else {
            buffer = new StringBuffer(500);
        }
        /*
         * Creates the Formatter when first needed.
         */
        Formatter formatter = this.formatter;
        if (formatter == null) {
            formatter = new Formatter(getLocale(), symbols,
                    (NumberFormat) getFormat(Number.class),
                    (DateFormat)   getFormat(Date.class),
                    (UnitFormat)   getFormat(Unit.class));
            updateFormatter(formatter);
            this.formatter = formatter;
        }
        final boolean valid;
        final InternationalString warning;
        try {
            formatter.setBuffer(buffer);
            valid = formatter.appendElement(object) || formatter.appendValue(object);
        } finally {
            warning = formatter.getErrorMessage();  // Must be saved before formatter.clear() is invoked.
            formatter.setBuffer(null);
            formatter.clear();
        }
        if (warning != null) {
            warnings = new Warnings(getLocale(), (byte) 0, Collections.<String, List<String>>emptyMap());
            warnings.add(warning, formatter.getErrorCause(), null);
            warnings.setRoot(object);
        }
        if (!valid) {
            throw new ClassCastException(Errors.format(
                    Errors.Keys.IllegalArgumentClass_2, "object", object.getClass()));
        }
        if (buffer != toAppendTo) {
            toAppendTo.append(buffer);
        }
    }

    /**
     * Creates a new format to use for parsing and formatting values of the given type.
     * This method is invoked the first time that a format is needed for the given type.
     * The {@code valueType} can be any types declared in the
     * {@linkplain CompoundFormat#createFormat(Class) parent class}.
     *
     * @param  valueType The base type of values to parse or format.
     * @return The format to use for parsing of formatting values of the given type, or {@code null} if none.
     */
    @Override
    protected Format createFormat(final Class<?> valueType) {
        if (valueType == Number.class) {
            return symbols.createNumberFormat();
        }
        if (valueType == Date.class) {
            final DateFormat format = new SimpleDateFormat(DATE_PATTERN, symbols.getLocale());
            format.setTimeZone(getTimeZone());
            return format;
        }
        return super.createFormat(valueType);
    }

    /**
     * If warnings occurred during the last WKT {@linkplain #parse(CharSequence, ParsePosition) parsing} or
     * {@linkplain #format(Object, Appendable) formatting}, returns the warnings. Otherwise returns {@code null}.
     * The warnings are cleared every time a new object is parsed or formatted.
     *
     * @return The warnings of the last parsing of formatting operation, or {@code null} if none.
     *
     * @since 0.6
     */
    public Warnings getWarnings() {
        final Warnings w = warnings;
        if (w != null) {
            w.publish();
        }
        return w;
    }

    /**
     * If a warning occurred during the last WKT {@linkplain #parse(CharSequence, ParsePosition) parsing} or
     * {@linkplain #format(Object, Appendable) formatting}, returns the warning. Otherwise returns {@code null}.
     * The warning is cleared every time a new object is parsed or formatted.
     *
     * @return The last warning, or {@code null} if none.
     *
     * @deprecated Replaced by {@link #getWarnings()}.
     */
    @Deprecated
    public String getWarning() {
        return (warnings != null) ? warnings.toString() : null;
    }

    /**
     * Returns a clone of this format.
     *
     * @return A clone of this format.
     */
    @Override
    public WKTFormat clone() {
        final WKTFormat clone = (WKTFormat) super.clone();
        clone.formatter = null; // Do not share the formatter.
        clone.parser    = null;
        clone.warnings  = null;
        return clone;
    }
}
