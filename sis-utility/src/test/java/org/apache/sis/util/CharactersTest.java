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
package org.apache.sis.util;

import org.apache.sis.test.DependsOnMethod;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.apache.sis.util.Characters.*;


/**
 * Tests the {@link Characters} utility methods.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.00)
 * @version 0.3
 * @module
 */
public final strictfp class CharactersTest extends TestCase {
    /**
     * Tests the value of {@link Characters#LINE_SEPARATOR}
     * and {@link Characters#PARAGRAPH_SEPARATOR} constant value.
     */
    @Test
    public void testUnicodeSeparators() {
        assertEquals(Character.LINE_SEPARATOR,      Character.getType(LINE_SEPARATOR));
        assertEquals(Character.PARAGRAPH_SEPARATOR, Character.getType(PARAGRAPH_SEPARATOR));
    }

    /**
     * Tests the {@link Characters#isLineOrParagraphSeparator(int)} method.
     */
    @Test
    @DependsOnMethod("testUnicodeSeparators")
    public void testLineOrParagraphSeparator() {
        assertFalse(isLineOrParagraphSeparator('z'));
        assertFalse(isLineOrParagraphSeparator('\t'));
        assertTrue (isLineOrParagraphSeparator('\r'));
        assertTrue (isLineOrParagraphSeparator('\n'));
        assertTrue (isLineOrParagraphSeparator(LINE_SEPARATOR));
        assertTrue (isLineOrParagraphSeparator(PARAGRAPH_SEPARATOR));
    }

    /**
     * Tests {@link Characters#toSuperScript(char)}.
     */
    @Test
    public void testSuperScript() {
        for (char c='0'; c<='9'; c++) {
            final char s = toSuperScript(c);
            assertFalse(s == c);
            assertFalse(isSuperScript(c));
            assertTrue (isSuperScript(s));
            assertEquals(c, toNormalScript(s));
        }
        final char c = 'A';
        assertEquals(c, toSuperScript(c));
        assertEquals(c, toNormalScript(c));
        assertFalse(isSuperScript(c));
    }

    /**
     * Tests {@link Characters#toSubScript(char)}.
     */
    @Test
    public void testSubScript() {
        for (char c='0'; c<='9'; c++) {
            final char s = toSubScript(c);
            assertFalse(s == c);
            assertFalse(isSubScript(c));
            assertTrue (isSubScript(s));
            assertEquals(c, toNormalScript(s));
        }
        final char c = 'a';
        assertEquals(c, toSubScript(c));
        assertEquals(c, toNormalScript(c));
        assertFalse(isSubScript(c));
    }
}
