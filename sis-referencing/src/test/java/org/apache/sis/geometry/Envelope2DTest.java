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
package org.apache.sis.geometry;

import org.apache.sis.test.DependsOn;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.opengis.test.Validators.*;
import static org.apache.sis.referencing.Assert.*;
import static org.apache.sis.geometry.AbstractEnvelopeTest.WGS84;


/**
 * Tests the {@link Envelope2D} class.
 * Most of tests are actually performed by {@link AbstractEnvelopeTest}, which compare
 * {@link GeneralEnvelope} results with {@code Envelope2D} results for ensuring consistency.
 * This class adds only some tests that are specific to {@code Envelope2D} instances.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3
 * @version 0.3
 * @module
 */
@DependsOn(AbstractEnvelopeTest.class)
public final strictfp class Envelope2DTest extends TestCase {
    /**
     * Tests {@code Envelope2D} serialization.
     */
    @Test
    public void testSerialization() {
        final Envelope2D e1 = new Envelope2D(WGS84, -20, -10, 40, 20);
        final Envelope2D e2 = assertSerializedEquals(e1);
        assertNotSame(e1, e2);
        validate(e2);
    }
}