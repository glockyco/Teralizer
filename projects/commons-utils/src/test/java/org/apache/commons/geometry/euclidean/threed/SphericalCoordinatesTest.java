/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.geometry.euclidean.threed;

import java.util.regex.Pattern;

import org.apache.commons.numbers.angle.Angle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SphericalCoordinatesTest {

    private static final double EPS = 1e-10;

    private static final double THREE_PI_OVER_TWO = 3 * Math.PI / 2;

    private static final double QUARTER_PI = 0.25 * Math.PI;
    private static final double MINUS_QUARTER_PI = -0.25 * Math.PI;
    private static final double THREE_QUARTER_PI = 0.75 * Math.PI;
    private static final double MINUS_THREE_QUARTER_PI = -0.75 * Math.PI;

    @Test
    void testNormalizeAzimuth() {
        // act/assert
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizeAzimuth(0), EPS);

        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizeAzimuth(Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, SphericalCoordinates.normalizeAzimuth(Math.PI), EPS);
        Assertions.assertEquals(THREE_PI_OVER_TWO, SphericalCoordinates.normalizeAzimuth(THREE_PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizeAzimuth(Angle.TWO_PI), EPS);

        Assertions.assertEquals(THREE_PI_OVER_TWO, SphericalCoordinates.normalizeAzimuth(-Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, SphericalCoordinates.normalizeAzimuth(-Math.PI), EPS);
        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizeAzimuth(-Math.PI - Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizeAzimuth(-Angle.TWO_PI), EPS);
    }

    @Test
    void testNormalizeAzimuth_NaNAndInfinite() {
        // act/assert
        Assertions.assertEquals(Double.NaN, SphericalCoordinates.normalizeAzimuth(Double.NaN), EPS);
        Assertions.assertEquals(Double.NEGATIVE_INFINITY, SphericalCoordinates.normalizeAzimuth(Double.NEGATIVE_INFINITY), EPS);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, SphericalCoordinates.normalizeAzimuth(Double.POSITIVE_INFINITY), EPS);
    }

    @Test
    void testNormalizePolar() {
        // act/assert
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizePolar(0), EPS);

        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizePolar(Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, SphericalCoordinates.normalizePolar(Math.PI), EPS);
        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizePolar(Math.PI + Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizePolar(Angle.TWO_PI), EPS);

        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizePolar(-Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, SphericalCoordinates.normalizePolar(-Math.PI), EPS);
        Assertions.assertEquals(Angle.PI_OVER_TWO, SphericalCoordinates.normalizePolar(-Math.PI - Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, SphericalCoordinates.normalizePolar(-Angle.TWO_PI), EPS);
    }

    @Test
    void testNormalizePolar_NaNAndInfinite() {
        // act/assert
        Assertions.assertEquals(Double.NaN, SphericalCoordinates.normalizePolar(Double.NaN), EPS);
        Assertions.assertEquals(Double.NEGATIVE_INFINITY, SphericalCoordinates.normalizePolar(Double.NEGATIVE_INFINITY), EPS);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, SphericalCoordinates.normalizePolar(Double.POSITIVE_INFINITY), EPS);
    }

}
