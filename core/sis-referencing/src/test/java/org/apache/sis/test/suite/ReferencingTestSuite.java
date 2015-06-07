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

import org.apache.sis.test.TestSuite;
import org.junit.runners.Suite;
import org.junit.BeforeClass;


/**
 * All tests from the {@code sis-referencing} module, in approximative dependency order.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3
 * @version 0.6
 * @module
 */
@Suite.SuiteClasses({
    org.apache.sis.internal.referencing.FormulasTest.class,
    org.apache.sis.internal.referencing.j2d.ShapeUtilitiesTest.class,
    org.apache.sis.internal.referencing.AxisDirectionsTest.class,
    org.apache.sis.internal.referencing.PositionalAccuracyConstantTest.class,
    org.apache.sis.internal.referencing.ReferencingUtilitiesTest.class,
    org.apache.sis.internal.jaxb.referencing.CodeTest.class,
    org.apache.sis.internal.jaxb.referencing.SecondDefiningParameterTest.class,

    // Identification of objects, needed by large parts of sis-referencing.
    org.apache.sis.referencing.NamedIdentifierTest.class,
    org.apache.sis.referencing.IdentifiedObjectsTest.class,
    org.apache.sis.referencing.AbstractIdentifiedObjectTest.class,
    org.apache.sis.referencing.AbstractReferenceSystemTest.class,
    org.apache.sis.referencing.BuilderTest.class,

    // Test matrices early because they may be used in about every SIS corners.
    org.apache.sis.referencing.operation.matrix.GeneralMatrixTest.class,
    org.apache.sis.referencing.operation.matrix.SolverTest.class,
    org.apache.sis.referencing.operation.matrix.Matrix1Test.class,
    org.apache.sis.referencing.operation.matrix.Matrix2Test.class,
    org.apache.sis.referencing.operation.matrix.Matrix3Test.class,
    org.apache.sis.referencing.operation.matrix.Matrix4Test.class,
    org.apache.sis.referencing.operation.matrix.NonSquareMatrixTest.class, // Expected to be last MatrixTestCase - see javadoc.
    org.apache.sis.referencing.operation.matrix.MatricesTest.class,
    org.apache.sis.referencing.operation.matrix.AffineTransforms2DTest.class,

    // Parameter are needed for math transforms and map projections.
    org.apache.sis.parameter.DefaultParameterDescriptorTest.class,
    org.apache.sis.parameter.DefaultParameterDescriptorGroupTest.class,
    org.apache.sis.parameter.DefaultParameterValueTest.class,
    org.apache.sis.parameter.DefaultParameterValueGroupTest.class,
    org.apache.sis.parameter.UnmodifiableParameterValueTest.class,
    org.apache.sis.parameter.ParametersTest.class,
    org.apache.sis.parameter.ParameterBuilderTest.class,
    org.apache.sis.parameter.ParameterFormatTest.class,
    org.apache.sis.parameter.TensorParametersTest.class,
    org.apache.sis.parameter.MatrixParametersTest.class,
    org.apache.sis.parameter.MatrixParametersAlphaNumTest.class,
    org.apache.sis.parameter.TensorValuesTest.class,
    org.apache.sis.parameter.MapProjectionParametersTest.class,

    // Test transforms other than map projections.
    org.apache.sis.referencing.operation.transform.CoordinateDomainTest.class,
    org.apache.sis.referencing.operation.transform.IterationStrategyTest.class,
    org.apache.sis.referencing.operation.transform.AbstractMathTransformTest.class,
    org.apache.sis.referencing.operation.transform.ProjectiveTransformTest.class,
    org.apache.sis.referencing.operation.transform.LinearTransformTest.class,
    org.apache.sis.referencing.operation.transform.ExponentialTransform1DTest.class,
    org.apache.sis.referencing.operation.transform.LogarithmicTransform1DTest.class,
    org.apache.sis.referencing.operation.transform.CopyTransformTest.class,
    org.apache.sis.referencing.operation.transform.PassThroughTransformTest.class,
    org.apache.sis.referencing.operation.transform.ConcatenatedTransformTest.class,
    org.apache.sis.referencing.operation.transform.TransferFunctionTest.class,
    org.apache.sis.referencing.operation.transform.MathTransformsTest.class,
    org.apache.sis.referencing.operation.transform.ContextualParametersTest.class,

    // Registration of map projections and other math transforms.
    org.apache.sis.referencing.operation.DefaultFormulaTest.class,
    org.apache.sis.referencing.operation.DefaultOperationMethodTest.class,
    org.apache.sis.referencing.operation.AbstractSingleOperationTest.class,
    // Other test classes from the 'operation' package after the CRS tests below.
    org.apache.sis.referencing.operation.transform.OperationMethodSetTest.class,
    org.apache.sis.internal.referencing.provider.AffineTest.class,
    org.apache.sis.internal.referencing.provider.LongitudeRotationTest.class,
    org.apache.sis.internal.referencing.provider.MapProjectionTest.class,
    org.apache.sis.internal.referencing.provider.AllProvidersTest.class,
    org.apache.sis.referencing.operation.transform.DefaultMathTransformFactoryTest.class,

    // Test map projections. Those tests need the providers tested above.
    org.apache.sis.referencing.operation.projection.NormalizedProjectionTest.class,
    org.apache.sis.referencing.operation.projection.EquirectangularTest.class,
    org.apache.sis.referencing.operation.projection.MercatorTest.class,
    org.apache.sis.referencing.operation.projection.LambertConformalTest.class,

    // Coordinate Reference System components.
    org.apache.sis.referencing.datum.BursaWolfParametersTest.class,
    org.apache.sis.referencing.datum.TimeDependentBWPTest.class,
    org.apache.sis.referencing.datum.DefaultEllipsoidTest.class,
    org.apache.sis.referencing.datum.DefaultPrimeMeridianTest.class,
    org.apache.sis.referencing.datum.DefaultVerticalDatumTest.class,
    org.apache.sis.referencing.datum.DefaultTemporalDatumTest.class,
    org.apache.sis.referencing.datum.DefaultGeodeticDatumTest.class,
    org.apache.sis.referencing.cs.DirectionAlongMeridianTest.class,
    org.apache.sis.referencing.cs.DefaultCoordinateSystemAxisTest.class,
    org.apache.sis.referencing.cs.NormalizerTest.class,
    org.apache.sis.referencing.cs.AbstractCSTest.class,
    org.apache.sis.referencing.cs.DefaultCartesianCSTest.class,
    org.apache.sis.referencing.cs.DefaultEllipsoidalCSTest.class,
    org.apache.sis.referencing.cs.DefaultSphericalCSTest.class,
    org.apache.sis.referencing.cs.DefaultCompoundCSTest.class,
    org.apache.sis.referencing.cs.CoordinateSystemsTest.class,
    org.apache.sis.referencing.cs.HardCodedCSTest.class,
    org.apache.sis.referencing.crs.AbstractCRSTest.class,
    org.apache.sis.referencing.crs.DefaultGeodeticCRSTest.class,
    org.apache.sis.referencing.crs.DefaultGeocentricCRSTest.class,
    org.apache.sis.referencing.crs.DefaultGeographicCRSTest.class,
    org.apache.sis.referencing.crs.DefaultVerticalCRSTest.class,
    org.apache.sis.referencing.crs.DefaultTemporalCRSTest.class,
    org.apache.sis.referencing.operation.DefaultTransformationTest.class,
    org.apache.sis.referencing.operation.DefaultConversionTest.class,
    org.apache.sis.referencing.crs.DefaultProjectedCRSTest.class,
    org.apache.sis.referencing.crs.DefaultDerivedCRSTest.class,
    org.apache.sis.referencing.crs.SubTypesTest.class,
    org.apache.sis.referencing.crs.DefaultCompoundCRSTest.class,
    org.apache.sis.referencing.crs.HardCodedCRSTest.class,
    org.apache.sis.referencing.factory.GeodeticObjectFactoryTest.class,
    org.apache.sis.referencing.StandardDefinitionsTest.class,
    org.apache.sis.referencing.CommonCRSTest.class,
    org.apache.sis.referencing.CRSTest.class,

    org.apache.sis.io.wkt.MathTransformParserTest.class,
    org.apache.sis.io.wkt.GeodeticObjectParserTest.class,
    org.apache.sis.io.wkt.WKTFormatTest.class,

    org.apache.sis.geometry.AbstractDirectPositionTest.class,
    org.apache.sis.geometry.GeneralDirectPositionTest.class,
    org.apache.sis.geometry.DirectPosition1DTest.class,
    org.apache.sis.geometry.DirectPosition2DTest.class,
    org.apache.sis.geometry.AbstractEnvelopeTest.class,
    org.apache.sis.geometry.GeneralEnvelopeTest.class,
    org.apache.sis.geometry.SubEnvelopeTest.class,
    org.apache.sis.geometry.ImmutableEnvelopeTest.class,
    org.apache.sis.geometry.Envelope2DTest.class,
    org.apache.sis.geometry.CurveExtremumTest.class,
    org.apache.sis.geometry.EnvelopesTest.class,

    org.apache.sis.distance.LatLonPointRadiusTest.class, // Pending refactoring in a geometry package.

    org.apache.sis.referencing.operation.builder.LinearTransformBuilderTest.class,
    org.apache.sis.internal.referencing.ServicesForMetadataTest.class,
    org.apache.sis.test.integration.ReferencingInMetadataTest.class,
    org.apache.sis.test.integration.DefaultMetadataTest.class
})
public final strictfp class ReferencingTestSuite extends TestSuite {
    /**
     * Verifies the list of tests before to run the suite.
     * See {@link #verifyTestList(Class, Class[])} for more information.
     */
    @BeforeClass
    public static void verifyTestList() {
        assertNoMissingTest(ReferencingTestSuite.class);
        verifyTestList(ReferencingTestSuite.class);
    }
}
