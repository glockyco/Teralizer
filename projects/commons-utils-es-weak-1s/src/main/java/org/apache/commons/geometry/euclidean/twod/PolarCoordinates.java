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

import org.apache.commons.numbers.angle.Angle;

/** Class representing <a href="https://en.wikipedia.org/wiki/Polar_coordinate_system">polar coordinates</a>
 * in 2 dimensional Euclidean space.
 *
 * <p>Polar coordinates are defined by a distance from a reference point
 * and an angle from a reference direction. The distance value is called
 * the radial coordinate, or <em>radius</em>, and the angle is called the angular coordinate,
 * or <em>azimuth</em>. This class follows the standard
 * mathematical convention of using the positive x-axis as the reference
 * direction and measuring positive angles counter-clockwise, toward the
 * positive y-axis. The origin is used as the reference point. Polar coordinate
 * are related to Cartesian coordinates as follows:
 * <pre>
 * x = r * cos(&theta;)
 * y = r * sin(&theta;)
 *
 * r = &radic;(x^2 + y^2)
 * &theta; = atan2(y, x)
 * </pre>
 * where <em>r</em> is the radius and <em>&theta;</em> is the azimuth of the polar coordinates.
 *
 * <p>In order to ensure the uniqueness of coordinate sets, coordinate values
 * are normalized so that {@code radius} is in the range {@code [0, +Infinity)}
 * and {@code azimuth} is in the range {@code [0, 2pi)}.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Polar_coordinate_system">Polar Coordinate System</a>
 */
public final class PolarCoordinates {
    public static double normalizeAzimuth(final double azimuth) {
        if (Double.isFinite(azimuth)) {
            return Angle.Rad.WITHIN_0_AND_2PI.applyAsDouble(azimuth);
        }

        return azimuth;
    }
}
