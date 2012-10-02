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
package org.apache.sis.internal.test;

import org.junit.Test;

import static org.apache.sis.test.Assert.*;


/**
 * Tests the {@link org.apache.sis.test.Assert} class.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.00)
 * @version 0.3
 * @module
 */
public final strictfp class AssertTest {
    /**
     * Tests the {@link Assert#assertMultilinesEquals(String, String)} method.
     */
    @Test
    public void testAssertEqualsMultilines() {
        assertMultilinesEquals("Line 1\nLine 2\r\nLine 3\n\rLine 5",
                               "Line 1\rLine 2\nLine 3\n\nLine 5");
    }

    /**
     * Tests the {@link Assert#assertSerializedEquals(Object)} method.
     */
    @Test
    public void testSerialize() {
        final String local = "Le silence éternel de ces espaces infinis m'effraie";
        assertNotSame(local, assertSerializedEquals(local));
    }
}
