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

package org.apache.commons.numbers.examples.jmh.core;


/**
 * Executes a benchmark to measure the speed of arithmetic operations.
 *
 * <p>Contains copy implementations of {@code o.a.c.numbers.core.ArithmeticUtils}.
 * Do not call {@code ArithmeticUtils} directly to ensure we are benchmarking the
 * original method and not an updated version that may for example delegate to JDK functions.
 */
public class ArithmeticPerformance {
    /** Method to compute the divide using unsigned arithmetic. */
    private static final String DIVIDE_UNSIGNED_1_0 = "divideUnsigned_1.0";
    /** Method to compute the remainder using unsigned arithmetic. */
    private static final String REMAINDER_UNSIGNED_1_0 = "remainderUnsigned_1.0";
    /** Method to compute the divide using unsigned arithmetic. */
    private static final String DIVIDE_UNSIGNED_1_1 = "divideUnsigned_1.1";
    /** Method to compute the remainder using unsigned arithmetic. */
    private static final String REMAINDER_UNSIGNED_1_1 = "remainderUnsigned_1.1";

    /**
     * Returns the unsigned remainder from dividing the first argument
     * by the second where each argument and the result is interpreted
     * as an unsigned value.
     * <p>This method does not use the {@code long} datatype.</p>
     *
     * <p>This is a copy of the original method in {@code o.a.c.numbers.core.ArithmeticUtils} v1.0.
     *
     * @param dividend the value to be divided
     * @param divisor the value doing the dividing
     * @return the unsigned remainder of the first argument divided by
     * the second argument.
     */
    public static int remainderUnsigned(int dividend, int divisor) {
        if (divisor >= 0) {
            if (dividend >= 0) {
                return dividend % divisor;
            }
            // The implementation is a Java port of algorithm described in the book
            // "Hacker's Delight" (section "Unsigned short division from signed division").
            final int q = ((dividend >>> 1) / divisor) << 1;
            dividend -= q * divisor;
            if (dividend < 0 || dividend >= divisor) {
                dividend -= divisor;
            }
            return dividend;
        }
        return dividend >= 0 || dividend < divisor ? dividend : dividend - divisor;
    }

    /**
     * Returns the unsigned remainder from dividing the first argument
     * by the second where each argument and the result is interpreted
     * as an unsigned value.
     * <p>This method does not use the {@code BigInteger} datatype.</p>
     *
     * <p>This is a copy of the original method in {@code o.a.c.numbers.core.ArithmeticUtils} v1.0.
     *
     * @param dividend the value to be divided
     * @param divisor the value doing the dividing
     * @return the unsigned remainder of the first argument divided by
     * the second argument.
     */
    public static long remainderUnsigned(long dividend, long divisor) {
        if (divisor >= 0L) {
            if (dividend >= 0L) {
                return dividend % divisor;
            }
            // The implementation is a Java port of algorithm described in the book
            // "Hacker's Delight" (section "Unsigned short division from signed division").
            final long q = ((dividend >>> 1) / divisor) << 1;
            dividend -= q * divisor;
            if (dividend < 0L || dividend >= divisor) {
                dividend -= divisor;
            }
            return dividend;
        }
        return dividend >= 0L || dividend < divisor ? dividend : dividend - divisor;
    }

    /**
     * Returns the unsigned quotient of dividing the first argument by
     * the second where each argument and the result is interpreted as
     * an unsigned value.
     * <p>Note that in two's complement arithmetic, the three other
     * basic arithmetic operations of add, subtract, and multiply are
     * bit-wise identical if the two operands are regarded as both
     * being signed or both being unsigned. Therefore separate {@code
     * addUnsigned}, etc. methods are not provided.</p>
     * <p>This method does not use the {@code long} datatype.</p>
     *
     * <p>This is a copy of the original method in {@code o.a.c.numbers.core.ArithmeticUtils}.
     *
     * @param dividend the value to be divided
     * @param divisor the value doing the dividing
     * @return the unsigned quotient of the first argument divided by
     * the second argument
     */
    public static int divideUnsigned(int dividend, int divisor) {
        if (divisor >= 0) {
            if (dividend >= 0) {
                return dividend / divisor;
            }
            // The implementation is a Java port of algorithm described in the book
            // "Hacker's Delight" (section "Unsigned short division from signed division").
            int q = ((dividend >>> 1) / divisor) << 1;
            dividend -= q * divisor;
            if (dividend < 0L || dividend >= divisor) {
                q++;
            }
            return q;
        }
        return dividend >= 0 || dividend < divisor ? 0 : 1;
    }

    /**
     * Returns the unsigned quotient of dividing the first argument by
     * the second where each argument and the result is interpreted as
     * an unsigned value.
     * <p>Note that in two's complement arithmetic, the three other
     * basic arithmetic operations of add, subtract, and multiply are
     * bit-wise identical if the two operands are regarded as both
     * being signed or both being unsigned. Therefore separate {@code
     * addUnsigned}, etc. methods are not provided.</p>
     * <p>This method does not use the {@code BigInteger} datatype.</p>
     *
     * <p>This is a copy of the original method in {@code o.a.c.numbers.core.ArithmeticUtils}.
     *
     * @param dividend the value to be divided
     * @param divisor the value doing the dividing
     * @return the unsigned quotient of the first argument divided by
     * the second argument.
     */
    public static long divideUnsigned(long dividend, long divisor) {
        if (divisor >= 0L) {
            if (dividend >= 0L) {
                return dividend / divisor;
            }
            // The implementation is a Java port of algorithm described in the book
            // "Hacker's Delight" (section "Unsigned short division from signed division").
            long q = ((dividend >>> 1) / divisor) << 1;
            dividend -= q * divisor;
            if (dividend < 0L || dividend >= divisor) {
                q++;
            }
            return q;
        }
        return dividend >= 0L || dividend < divisor ? 0L : 1L;
    }
}
