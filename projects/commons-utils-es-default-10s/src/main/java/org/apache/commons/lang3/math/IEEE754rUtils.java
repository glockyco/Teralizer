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
package org.apache.commons.lang3.math;


/**
 * Provides IEEE-754r variants of NumberUtils methods.
 *
 * <p>See: <a href="https://en.wikipedia.org/wiki/IEEE_754r">https://en.wikipedia.org/wiki/IEEE_754r</a></p>
 *
 * @since 2.4
 */
public class IEEE754rUtils {

    /**
     * Gets the maximum of two {@code double} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @return  the largest of the values
     */
    public static double max(final double a, final double b) {
        if (Double.isNaN(a)) {
            return b;
        }
        if (Double.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }

    /**
     * Gets the maximum of three {@code double} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @param c  value 3
     * @return  the largest of the values
     */
    public static double max(final double a, final double b, final double c) {
        return max(max(a, b), c);
    }

    /**
     * Gets the maximum of two {@code float} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @return  the largest of the values
     */
    public static float max(final float a, final float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }

    /**
     * Gets the maximum of three {@code float} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @param c  value 3
     * @return  the largest of the values
     */
    public static float max(final float a, final float b, final float c) {
        return max(max(a, b), c);
    }

    /**
     * Gets the minimum of two {@code double} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @return  the smallest of the values
     */
    public static double min(final double a, final double b) {
        if (Double.isNaN(a)) {
            return b;
        }
        if (Double.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * Gets the minimum of three {@code double} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @param c  value 3
     * @return  the smallest of the values
     */
    public static double min(final double a, final double b, final double c) {
        return min(min(a, b), c);
    }

    /**
     * Gets the minimum of two {@code float} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @return  the smallest of the values
     */
    public static float min(final float a, final float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * Gets the minimum of three {@code float} values.
     *
     * <p>NaN is only returned if all numbers are NaN as per IEEE-754r.</p>
     *
     * @param a  value 1
     * @param b  value 2
     * @param c  value 3
     * @return  the smallest of the values
     */
    public static float min(final float a, final float b, final float c) {
        return min(min(a, b), c);
    }

    /**
     * Make private in 4.0.
     *
     * @deprecated TODO Make private in 4.0.
     */
    @Deprecated
    public IEEE754rUtils() {
        // empty
    }
}
