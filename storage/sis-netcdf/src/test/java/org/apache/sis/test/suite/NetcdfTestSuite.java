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
package org.apache.sis.test.suite;

import org.apache.sis.internal.netcdf.IOTestCase;
import org.apache.sis.test.TestSuite;
import org.junit.runners.Suite;
import org.junit.BeforeClass;


/**
 * All tests from the {@code sis-referencing} module, in approximative dependency order.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3
 * @version 0.3
 * @module
 */
@Suite.SuiteClasses({
    org.apache.sis.internal.netcdf.DecoderTest.class,
    org.apache.sis.internal.netcdf.VariableTest.class,
    org.apache.sis.internal.netcdf.impl.ChannelDecoderTest.class,
    org.apache.sis.internal.netcdf.impl.VariableInfoTest.class
})
public final strictfp class NetcdfTestSuite extends TestSuite {
    /**
     * Verifies the list of tests before to run the suite.
     * See {@link #verifyTestList(Class)} for more information.
     */
    @BeforeClass
    public static void verifyTestList() {
        verifyTestList(NetcdfTestSuite.class, new Class<?>[] {
            IOTestCase.class
        });
    }
}