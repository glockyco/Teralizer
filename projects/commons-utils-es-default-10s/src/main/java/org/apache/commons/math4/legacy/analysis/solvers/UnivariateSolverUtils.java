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
package org.apache.commons.math4.legacy.analysis.solvers;

public final class UnivariateSolverUtils {
    /**
     * Class contains only static methods.
     */
    private UnivariateSolverUtils() {}

    /**
     * Compute the midpoint of two values.
     *
     * @param a first value.
     * @param b second value.
     * @return the midpoint.
     */
    public static double midpoint(double a, double b) {
        return (a + b) * 0.5;
    }

    /**
     * Check whether the arguments form a (strictly) increasing sequence.
     *
     * @param start First number.
     * @param mid Second number.
     * @param end Third number.
     * @return {@code true} if the arguments form an increasing sequence.
     */
    public static boolean isSequence(final double start,
                                     final double mid,
                                     final double end) {
        return start < mid && mid < end;
    }

    public static void verifyInterval(final double lower,
                                      final double upper)
        throws RuntimeException {
        if (lower >= upper) {
            throw new RuntimeException();
        }
    }

    public static void verifySequence(final double lower,
                                      final double initial,
                                      final double upper)
        throws RuntimeException {
        verifyInterval(lower, initial);
        verifyInterval(initial, upper);
    }
}
