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
package org.apache.commons.numbers.gamma;

/**
 * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
 * Regularized Beta function</a>.
 *
 * <p>\[ I_x(a,b) = \frac{1}{B(a, b)} \int_0^x t^{a-1}\,(1-t)^{b-1}\,dt \]
 *
 * <p>where \( B(a, b) \) is the beta function.
 *
 * <p>This code has been adapted from the <a href="https://www.boost.org/">Boost</a>
 * {@code c++} implementation {@code <boost/math/special_functions/beta.hpp>}.
 *
 * @see
 * <a href="https://www.boost.org/doc/libs/1_77_0/libs/math/doc/html/math_toolkit/sf_beta/ibeta_function.html">
 * Boost C++ Incomplete Beta functions</a>
 */
public final class RegularizedBeta {

    /** Private constructor. */
    private RegularizedBeta() {
        // intentionally empty.
    }

    /**
     * Computes the value of the
     * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
     * regularized beta function</a> I(x, a, b).
     *
     * <p>\[ I_x(a,b) = \frac{1}{B(a, b)} \int_0^x t^{a-1}\,(1-t)^{b-1}\,dt \]
     *
     * <p>where \( B(a, b) \) is the beta function.
     *
     * @param x Value.
     * @param a Parameter {@code a}.
     * @param b Parameter {@code b}.
     * @return the regularized beta function \( I_x(a, b) \).
     * @throws ArithmeticException if the series evaluation fails to converge.
     */
    public static double value(double x,
                               double a,
                               double b) {
        return BoostBeta.ibeta(a, b, x);
    }

    /**
     * Computes the value of the
     * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
     * regularized beta function</a> I(x, a, b).
     *
     * <p>\[ I_x(a,b) = \frac{1}{B(a, b)} \int_0^x t^{a-1}\,(1-t)^{b-1}\,dt \]
     *
     * <p>where \( B(a, b) \) is the beta function.
     *
     * @param x the value.
     * @param a Parameter {@code a}.
     * @param b Parameter {@code b}.
     * @param epsilon Tolerance in series evaluation.
     * @param maxIterations Maximum number of iterations in series evaluation.
     * @return the regularized beta function \( I_x(a, b) \).
     * @throws ArithmeticException if the series evaluation fails to converge.
     */
    public static double value(double x,
                               final double a,
                               final double b,
                               double epsilon,
                               int maxIterations) {
        return BoostBeta.ibeta(a, b, x, new Policy(epsilon, maxIterations));
    }

    /**
     * Computes the complement of the
     * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
     * regularized beta function</a> I(x, a, b).
     *
     * <p>\[ 1 - I_x(a,b) = I_{1-x}(b, a) \]
     *
     * @param x Value.
     * @param a Parameter {@code a}.
     * @param b Parameter {@code b}.
     * @return the complement of the regularized beta function \( 1 - I_x(a, b) \).
     * @throws ArithmeticException if the series evaluation fails to converge.
     * @since 1.1
     */
    public static double complement(double x,
                                    double a,
                                    double b) {
        return BoostBeta.ibetac(a, b, x);
    }

    /**
     * Computes the complement of the
     * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
     * regularized beta function</a> I(x, a, b).
     *
     * <p>\[ 1 - I_x(a,b) = I_{1-x}(b, a) \]
     *
     * @param x the value.
     * @param a Parameter {@code a}.
     * @param b Parameter {@code b}.
     * @param epsilon Tolerance in series evaluation.
     * @param maxIterations Maximum number of iterations in series evaluation.
     * @return the complement of the regularized beta function \( 1 - I_x(a, b) \).
     * @throws ArithmeticException if the series evaluation fails to converge.
     * @since 1.1
     */
    public static double complement(double x,
                                    final double a,
                                    final double b,
                                    double epsilon,
                                    int maxIterations) {
        return BoostBeta.ibetac(a, b, x, new Policy(epsilon, maxIterations));
    }

    /**
     * Computes the derivative of the
     * <a href="https://mathworld.wolfram.com/RegularizedBetaFunction.html">
     * regularized beta function</a> I(x, a, b).
     *
     * <p>\[ \frac{\delta}{\delta x} I_x(a,b) = \frac{(1-x)^{b-1} x^{a-1}}{B(a, b)} \]
     *
     * <p>where \( B(a, b) \) is the beta function.
     *
     * <p>This function has uses in some statistical distributions.
     *
     * @param x Value.
     * @param a Parameter {@code a}.
     * @param b Parameter {@code b}.
     * @return the derivative of the regularized beta function \( I_x(a, b) \).
     * @throws ArithmeticException if the series evaluation fails to converge.
     * @since 1.1
     */
    public static double derivative(double x,
                                    double a,
                                    double b) {
        return BoostBeta.ibetaDerivative(a, b, x);
    }
}
