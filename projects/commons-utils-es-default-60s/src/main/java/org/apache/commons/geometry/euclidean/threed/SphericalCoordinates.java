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

import org.apache.commons.geometry.euclidean.twod.PolarCoordinates;
import org.apache.commons.numbers.angle.Angle;

/** Class representing <a href="https://en.wikipedia.org/wiki/Spherical_coordinate_system">spherical coordinates</a>
 * in 3 dimensional Euclidean space.
 *
 * <p>Spherical coordinates for a point are defined by three values:
 * <ol>
 *  <li><em>Radius</em> - The distance from the point to a fixed referenced point.</li>
 *  <li><em>Azimuth angle</em> - The angle measured from a fixed reference direction in a plane to
 * the orthogonal projection of the point on that plane.</li>
 *  <li><em>Polar angle</em> - The angle measured from a fixed zenith direction to the point. The zenith
 *direction must be orthogonal to the reference plane.</li>
 * </ol>
 * This class follows the convention of using the origin as the reference point; the positive x-axis as the
 * reference direction for the azimuth angle, measured in the x-y plane with positive angles moving counter-clockwise
 * toward the positive y-axis; and the positive z-axis as the zenith direction. Spherical coordinates are
 * related to Cartesian coordinates as follows:
 * <pre>
 * x = r cos(&theta;) sin(&Phi;)
 * y = r sin(&theta;) sin(&Phi;)
 * z = r cos(&Phi;)
 *
 * r = &radic;(x^2 + y^2 + z^2)
 * &theta; = atan2(y, x)
 * &Phi; = acos(z/r)
 * </pre>
 * where <em>r</em> is the radius, <em>&theta;</em> is the azimuth angle, and <em>&Phi;</em> is the polar angle
 * of the spherical coordinates.
 *
 * <p>There are numerous, competing conventions for the symbols used to represent spherical coordinate values. For
 * example, the mathematical convention is to use <em>(r, &theta;, &Phi;)</em> to represent radius, azimuth angle, and
 * polar angle, whereas the physics convention flips the angle values and uses <em>(r, &Phi;, &theta;)</em>. As such,
 * this class avoids the use of these symbols altogether in favor of the less ambiguous formal names of the values,
 * e.g. {@code radius}, {@code azimuth}, and {@code polar}.</p>
 *
 * <p>In order to ensure the uniqueness of coordinate sets, coordinate values
 * are normalized so that {@code radius} is in the range {@code [0, +Infinity)},
 * {@code azimuth} is in the range {@code [0, 2pi)}, and {@code polar} is in the
 * range {@code [0, pi]}.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Spherical_coordinate_system">Spherical Coordinate System</a>
 */
public final class SphericalCoordinates {

    public static double normalizeAzimuth(final double azimuth) {
        return PolarCoordinates.normalizeAzimuth(azimuth);
    }

    public static double normalizePolar(final double polar) {
        // normalize the polar angle; this is the angle between the polar vector and the point ray
        // so it is unsigned (unlike the azimuth) and should be in the range [0, pi]
        if (Double.isFinite(polar)) {
            return Math.abs(Angle.Rad.WITHIN_MINUS_PI_AND_PI.applyAsDouble(polar));
        }

        return polar;
    }
}
