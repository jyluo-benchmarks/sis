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

/**
 * Tools for SIS tests. This package defines a base class, {@link org.apache.sis.test.TestCase},
 * which is extended directly or indirectly by many (but not all) SIS tests.
 * This package defines also an {@link org.apache.sis.test.Assert} class which extend the GeoAPI
 * {@link org.opengis.test.Assert} (which itself extends the JUnit {@link org.junit.Assert} class)
 * with the addition of assertion methods commonly used in SIS tests.
 * <p>
 * By default, successful tests do not produce any output. However it is possible to ask for
 * verbose output, which is sometime useful for debugging purpose. This behavior is controlled
 * by {@linkplain java.lang.System#getProperties() system properties} like below:
 * <p>
 * <ul>
 *   <li><code>-D{@value org.apache.sis.test.TestBase#VERBOSE_KEY}=true</code>
 *       for verbose console output.</li>
 *   <li><code>-D{@value org.apache.sis.test.TestBase#ENCODING_KEY}=UTF-8</code>
 *       (or any other valid encoding name) for the encoding of the above-cited
 *       verbose output. If omitted, the platform encoding will be used.</li>
 * </ul>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.00)
 * @version 0.3
 * @module
 */
package org.apache.sis.test;