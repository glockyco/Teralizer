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
package org.apache.commons.geometry.euclidean.twod;

import java.util.regex.Pattern;

import org.apache.commons.numbers.angle.Angle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PolarCoordinatesTest {

    private static final double EPS = 1e-10;

    private static final double THREE_PI_OVER_TWO = 3 * Math.PI / 2;

    @Test
    void testNormalizeAzimuth() {
        // act/assert
        Assertions.assertEquals(0.0, PolarCoordinates.normalizeAzimuth(0), EPS);

        Assertions.assertEquals(Angle.PI_OVER_TWO, PolarCoordinates.normalizeAzimuth(Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, PolarCoordinates.normalizeAzimuth(Math.PI), EPS);
        Assertions.assertEquals(THREE_PI_OVER_TWO, PolarCoordinates.normalizeAzimuth(THREE_PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, PolarCoordinates.normalizeAzimuth(Angle.TWO_PI), EPS);

        Assertions.assertEquals(THREE_PI_OVER_TWO, PolarCoordinates.normalizeAzimuth(-Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(Math.PI, PolarCoordinates.normalizeAzimuth(-Math.PI), EPS);
        Assertions.assertEquals(Angle.PI_OVER_TWO, PolarCoordinates.normalizeAzimuth(-Math.PI - Angle.PI_OVER_TWO), EPS);
        Assertions.assertEquals(0.0, PolarCoordinates.normalizeAzimuth(-Angle.TWO_PI), EPS);
    }

    @Test
    void testNormalizeAzimuth_NaNAndInfinite() {
        // act/assert
        Assertions.assertEquals(Double.NaN, PolarCoordinates.normalizeAzimuth(Double.NaN), EPS);
        Assertions.assertEquals(Double.NEGATIVE_INFINITY, PolarCoordinates.normalizeAzimuth(Double.NEGATIVE_INFINITY), EPS);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, PolarCoordinates.normalizeAzimuth(Double.POSITIVE_INFINITY), EPS);
    }
}
