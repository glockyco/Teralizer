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
package org.apache.commons.numbers.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Test cases for {@link DD} arithmetic.
 */
class DDTest {
    /** Down scale factors to apply to argument y of f(x, y). */
    private static final double[] DOWN_SCALES = {
        1.0, 0x1.0p-1, 0x1.0p-2, 0x1.0p-3, 0x1.0p-5, 0x1.0p-10, 0x1.0p-25, 0x1.0p-51, 0x1.0p-52, 0x1.0p-53, 0x1.0p-100
    };
    /** Scale factors to apply to argument y of f(x, y). */
    private static final double[] SCALES = {
        1.0, 0x1.0p-1, 0x1.0p-2, 0x1.0p-3, 0x1.0p-5, 0x1.0p-10, 0x1.0p-25, 0x1.0p-51, 0x1.0p-52, 0x1.0p-53, 0x1.0p-100,
        0x1.0p1, 0x1.0p2, 0x1.0p3, 0x1.0p5, 0x1.0p10, 0x1.0p25, 0x1.0p51, 0x1.0p52, 0x1.0p53, 0x1.0p100
    };
    /** MathContext for division. A double-double has approximately 34 digits of precision so
     * use twice this to allow computation of relative error of the results to a useful precision. */
    private static final MathContext MC_DIVIDE = new MathContext(MathContext.DECIMAL128.getPrecision() * 2);
    /** A BigDecimal for Long.MAX_VALUE. */
    private static final BigDecimal BD_LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    /** A BigDecimal for Long.MIN_VALUE. */
    private static final BigDecimal BD_LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    /** Number of random samples for arithmetic data. */
    private static final int SAMPLES = 100;
    /** The epsilon for relative error. Equivalent to 2^-106 for the precision of a double-double
     * 106-bit mantissa. This value is used to report the accuracy of the functions in the DD javadoc. */
    private static final double EPS = 0x1.0p-106;

    @Test
    void testOne() {
        Assertions.assertEquals(1, DD.ONE.hi());
        Assertions.assertEquals(0, DD.ONE.lo());
        Assertions.assertSame(DD.ONE, DD.of(1.23).one());
    }

    @Test
    void testZero() {
        Assertions.assertEquals(0, DD.ZERO.hi());
        Assertions.assertEquals(0, DD.ZERO.lo());
        Assertions.assertSame(DD.ZERO, DD.of(1.23).zero());
    }

    @Test
    void testIsOne() {
        Assertions.assertTrue(DD.ONE.isOne());
        Assertions.assertTrue(DD.of(0.5, 0).add(DD.of(0.5, 0)).isOne());
        DD value = DD.ofSum(1e300, 1e-300);
        Assertions.assertTrue(value.divide(value).isOne());

        Assertions.assertFalse(DD.ZERO.isOne());
        Assertions.assertFalse(DD.of(0.5).isOne());
        Assertions.assertFalse(DD.of(0.5, 1e-20).isOne());
        Assertions.assertFalse(DD.ofSum(1.0, 1e-20).isOne());
    }

    @Test
    void testIsZero() {
        Assertions.assertTrue(DD.ZERO.isZero());
        Assertions.assertTrue(DD.of(-0.0).isZero());
        Assertions.assertTrue(DD.of(0.5, 0).subtract(DD.of(0.5, 0)).isZero());
        DD value = DD.ofSum(1e300, 1e-300);
        Assertions.assertTrue(value.multiply(DD.of(0.0)).isZero());

        Assertions.assertFalse(DD.ONE.isZero());
        Assertions.assertFalse(DD.of(3.1415926).isZero());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, Math.PI, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN})
    void testOfDouble(double x) {
        DD dd = DD.of(x);
        Assertions.assertEquals(x, dd.hi(), "x hi");
        Assertions.assertEquals(0, dd.lo(), "x lo");
        dd = DD.of(-x);
        Assertions.assertEquals(-x, dd.hi(), "-x hi");
        Assertions.assertEquals(0, dd.lo(), "-x lo");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 42, 4674567, Integer.MIN_VALUE, Integer.MAX_VALUE - 42, Integer.MAX_VALUE})
    void testOfInt(int x) {
        DD dd = DD.of(x);
        Assertions.assertEquals(x, dd.hi(), "x hi");
        Assertions.assertEquals(0, dd.lo(), "x lo");
        dd = DD.of(-x);
        Assertions.assertEquals(-x, dd.hi(), "-x hi");
        Assertions.assertEquals(0, dd.lo(), "-x lo");
    }

    /**
     * Test conversion of a {@code long}. The upper part should be the value cast as a double.
     * The lower part is any remaining value. If done incorrectly this can lose bits due
     * to rounding to 2^53 so we have extra cases for this.
     */
    @ParameterizedTest
    @ValueSource(longs = {0, 1, 42, 89545664, 8263492364L, Long.MIN_VALUE,
        Long.MAX_VALUE - (1L << 10), Long.MAX_VALUE - 42, Long.MAX_VALUE - 1, Long.MAX_VALUE})
    void testOfLong(long x) {
        DD dd = DD.of(x);
        Assertions.assertEquals(x, dd.hi(), "x hi should be (double) x");
        Assertions.assertEquals(BigDecimal.valueOf(x).subtract(bd(x)).doubleValue(), dd.lo(), "x lo should be remaining bits");
        dd = DD.of(-x);
        Assertions.assertEquals(-x, dd.hi(), "-x hi should be (double) -x");
        Assertions.assertEquals(BigDecimal.valueOf(-x).subtract(bd(-x)).doubleValue(), dd.lo(), "-x lo should be remaining bits");
    }

    @ParameterizedTest
    @CsvSource({
        "1e500, Infinity, 0",
        "-1e600, -Infinity, 0",
    })
    void testFromBigDecimalInfinite(String value, double x, double xx) {
        final DD z = DD.from(new BigDecimal(value));
        Assertions.assertEquals(x, z.hi(), "hi");
        Assertions.assertEquals(xx, z.lo(), "lo");
    }

    @ParameterizedTest
    @MethodSource
    void testIsFinite(double x, double xx) {
        final DD dd = DD.of(x, xx);
        final boolean isFinite = Double.isFinite(x + xx);
        Assertions.assertEquals(isFinite, dd.isFinite(), "finite evaluated sum");
    }

    static Stream<Arguments> testIsFinite() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        // Note: (max + max) will overflow but the DD number should be finite.
        final double[] values = {0, 1, Double.MAX_VALUE, Double.MIN_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (final double x : values) {
            for (final double xx : values) {
                builder.add(Arguments.of(x, xx));
            }
        }
        return builder.build();
    }

    static Stream<Arguments> testDoubleFloatValue() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        // Note: (max + max) will overflow but the DD number should be finite.
        final double[] values = {0, 1, -42, -0.5, Double.MAX_VALUE, Double.MIN_VALUE,
            -Double.MIN_NORMAL, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (final double x : values) {
            for (final double xx : values) {
                builder.add(Arguments.of(x, xx));
            }
        }
        return builder.build();
    }

    @ParameterizedTest
    @MethodSource(value = {"testFloorCeil"})
    void testFloor(double x, double xx) {
        assertFloorCeil(x, xx, true);
    }

    @ParameterizedTest
    @MethodSource(value = {"testFloorCeil"})
    void testCeil(double x, double xx) {
        assertFloorCeil(x, xx, false);
    }

    /**
     * Assert the floor or ceil functions. These are tested together as they should match
     * rounding performed by BigDecimal. BigDecimal cannot handle non-finite values; these
     * are tested as mapping to the floor or ceil result with the low part as zero.
     */
    private static void assertFloorCeil(double x, double xx, boolean isFloor) {
        DD dd = DD.of(x, xx);
        dd = isFloor ? dd.floor() : dd.ceil();
        double y = isFloor ? Math.floor(x) : Math.ceil(x);
        double yy;
        // General floor/ceil result changes x (so assume abs(xx) < 1),
        // or special mappings of non-finite/zero.
        // Here the low part is always +0.0.
        // x != op(x) -> (op(x), 0)
        // (+/0.0, xx) -> (x, 0)
        // (NaN, xx) -> (NaN, 0)
        // (+/-infinity, xx) -> (x, 0)
        if (x == 0 || !Double.isFinite(x) || x != y) {
            yy = +0.0;
        } else {
            assertNormalized(x, xx, "x");
            // scale is the number of digits to the right of the decimal point
            final BigDecimal value = bd(x).add(bd(xx)).setScale(0,
                isFloor ? RoundingMode.FLOOR : RoundingMode.CEILING);
            y = value.doubleValue();
            yy = value.subtract(bd(y)).doubleValue();
            // Note: If yy is zero then BigDecimal math will always create +0.0.
            // The DD class is written to match this by never returning a -0.0
            // for the low component of floor or ceiling.
            if (yy == 0) {
                Assertions.assertEquals(+0.0, yy, "BigDecimal should not generate -0.0 values");
            }
        }
        Assertions.assertEquals(y, dd.hi(), "hi");
        Assertions.assertEquals(yy, dd.lo(), "lo");
    }

    static Stream<Arguments> testFloorCeil() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        final double inf = Double.POSITIVE_INFINITY;
        final double nan = Double.NaN;
        final double[] lo = {-0.0, 0.0, -1.0, 1.0, -inf, inf, nan};
        for (final double xx : lo) {
            builder.add(Arguments.of(0.0, xx));
            builder.add(Arguments.of(-0.0, xx));
            builder.add(Arguments.of(nan, xx));
            builder.add(Arguments.of(inf, xx));
            builder.add(Arguments.of(-inf, xx));
        }
        // Must be non-zero. Zero has a special mapping to (x, 0.0).
        // Use both representable integers, and fractions for the high part.
        // We require the low part to be zero, an integer or a fraction.
        // When the values are large they are always representable integers
        // and the low part contains the fraction.
        final double[] hi = {1, 1.5, 1234, 1234.5,
            // has a ulp of 2
            1.2345 * 0x1.0p53,
            // has a ulp of 4
            1.2345 * 0x1.0p54,
            // has a ulp of 2048
            1.2345 * 0x1.0p63};
        final double min = Double.MIN_VALUE;
        for (final double x : hi) {
            builder.add(Arguments.of(x, 0));
            builder.add(Arguments.of(-x, 0));
            builder.add(Arguments.of(x, -min));
            builder.add(Arguments.of(x, min));
            builder.add(Arguments.of(-x, min));
            builder.add(Arguments.of(-x, -min));
            // Avoid Math.ulp in case x is an exact power of 2.
            double down = Math.nextDown(x) - x;
            double up = Math.nextUp(x) - x;
            // xx must be less than 0.5 ulp to be a normalised double-double.
            // Create a non-power of 2 low part with a division by 3.
            down /= 3;
            up /= 3;
            builder.add(Arguments.of(x, down));
            builder.add(Arguments.of(x, up));
            builder.add(Arguments.of(-x, -down));
            builder.add(Arguments.of(-x, -up));
            // Large numbers can use an integer low part
            down = Math.ceil(down);
            if (down <= -1) {
                up = Math.floor(up);
                builder.add(Arguments.of(x, down));
                builder.add(Arguments.of(x, up));
                builder.add(Arguments.of(-x, -down));
                builder.add(Arguments.of(-x, -up));
            }
        }
        return builder.build();
    }

    /**
     * Test {@link DD#twoSum(double, double)} with cases that have non-normal input
     * or create overflow.
     */
    @Test
    void testTwoSumSpecialCases() {
        // x + y is sub-normal or zero
        assertSum(0.0, Math.nextDown(Double.MIN_NORMAL), -Math.ulp(Double.MIN_NORMAL));
        assertSum(0.0, -1.0, 1.0);
        // x or y is infinite or NaN
        assertSum(Double.NaN, 1.0, Double.POSITIVE_INFINITY);
        assertSum(Double.NaN, 1.0, Double.NEGATIVE_INFINITY);
        assertSum(Double.NaN, 1.0, Double.NaN);
        // x + y is infinite
        assertSum(Double.NaN, 0x1.0p1023, 0x1.0p1023);
        assertSum(Double.NaN, Math.ulp(Double.MAX_VALUE), Double.MAX_VALUE);
    }

    private static void assertSum(double expectedLo, double x, double y) {
        final DD z = DD.ofSum(x, y);
        Assertions.assertEquals(x + y, z.hi(), "hi");
        // Requires a delta of 0.0 to assert -0.0 == 0.0
        Assertions.assertEquals(expectedLo, z.lo(), 0.0, "lo");
        final DD z2 = DD.ofSum(y, x);
        Assertions.assertEquals(z.hi(), z2.hi(), "y+x hi");
        Assertions.assertEquals(z.lo(), z2.lo(), "y+x lo");
    }

    /**
     * Test {@link DD#twoProd(double, double)} with cases that have non-normal input
     * or create intermediate overflow when splitting.
     */
    @Test
    void testTwoProdSpecialCases() {
        // x * y is sub-normal or zero
        assertProduct(0.0, 1.0, Math.nextDown(Double.MIN_NORMAL));
        assertProduct(0.0, -1.0, Math.nextDown(Double.MIN_NORMAL));
        assertProduct(0.0, 0x1.0p-512, 0x1.0p-512);
        // x or y is infinite or NaN
        assertProduct(Double.NaN, 1.0, Double.POSITIVE_INFINITY);
        assertProduct(Double.NaN, 1.0, Double.NEGATIVE_INFINITY);
        assertProduct(Double.NaN, 1.0, Double.NaN);
        // x * y is infinite
        assertProduct(Double.NaN, 0x1.0p511, 0x1.0p513);
        // |x| or |y| > ~2^997
        assertProduct(Double.NaN, 0.5, 0x1.0p997);
        assertProduct(Double.NaN, 0.5, Double.MAX_VALUE);
    }

    private static void assertProduct(double expectedLo, double x, double y) {
        final DD z = DD.ofProduct(x, y);
        Assertions.assertEquals(x * y, z.hi(), "hi");
        // Requires a delta of 0.0 to assert -0.0 == 0.0
        Assertions.assertEquals(expectedLo, z.lo(), 0.0, "lo");
        final DD z2 = DD.ofProduct(y, x);
        Assertions.assertEquals(z.hi(), z2.hi(), "y*x hi");
        Assertions.assertEquals(z.lo(), z2.lo(), "y*x lo");
    }


    /**
     * Test {@link DD#twoSquare(double)} with cases that have non-normal input
     * or create intermediate overflow when splitting.
     */
    @Test
    void testTwoSquareSpecialCases() {
        // x * x is sub-normal or zero
        assertSquare(0.0, 0);
        assertSquare(0.0, Double.MIN_NORMAL);
        assertSquare(0.0, 0x1.0p-512);
        // x is infinite or NaN
        assertSquare(Double.NaN, Double.POSITIVE_INFINITY);
        assertSquare(Double.NaN, Double.NaN);
        // x * x is infinite
        assertSquare(Double.NaN, 0x1.0p512);
        // |x| > ~2^997
        assertSquare(Double.NaN, 0x1.0p997);
        assertSquare(Double.NaN, Double.MAX_VALUE);
    }

    private static void assertSquare(double expectedLo, double x) {
        final DD z = DD.ofSquare(x);
        Assertions.assertEquals(x * x, z.hi(), "hi");
        // Requires a delta of 0.0 to assert -0.0 == 0.0
        Assertions.assertEquals(expectedLo, z.lo(), 0.0, "lo");
        final DD z2 = DD.ofSquare(-x);
        Assertions.assertEquals(z.hi(), z2.hi(), "(-x)^2 hi");
        Assertions.assertEquals(z.lo(), z2.lo(), "(-x)^2 lo");
    }

    /**
     * Test {@link DD#fromQuotient(double, double)} with cases that have non-normal input
     * or create intermediate overflow when splitting.
     */
    @Test
    void testQuotientSpecialCases() {
        // x / y is sub-normal or zero
        assertQuotient(0.0, Double.MIN_NORMAL, 3);
        assertQuotient(0.0, Math.nextUp(Double.MIN_NORMAL), 2);
        assertQuotient(0.0, 0, 1);
        // x is infinite or NaN
        assertQuotient(Double.NaN, Double.POSITIVE_INFINITY, 1);
        assertQuotient(Double.NaN, Double.NEGATIVE_INFINITY, 1);
        assertQuotient(Double.NaN, Double.NaN, 1);
        // y is infinite (here low part could be zero if checks were added)
        assertQuotient(Double.NaN, 1.0, Double.POSITIVE_INFINITY);
        assertQuotient(Double.NaN, 1.0, Double.NEGATIVE_INFINITY);
        // y is nan
        assertQuotient(Double.NaN, 1.0, Double.NaN);
        // x / y is infinite
        assertQuotient(Double.NaN, 0x1.0p511, 0x1.0p-513);
        assertQuotient(Double.NaN, 1, Double.MIN_VALUE);
        // |x / y| > ~2^997
        assertQuotient(Double.NaN, 0x1.0p997, 0.5);
        assertQuotient(Double.NaN, Double.MAX_VALUE, 2);
        // |y| > ~2^997
        assertQuotient(Double.NaN, 0.5, 0x1.0p997);
        assertQuotient(Double.NaN, 2, Double.MAX_VALUE);
        // x / y is sub-normal or zero with intermediate overflow
        assertQuotient(Double.NaN, 0.5, Double.MAX_VALUE);
        assertQuotient(Double.NaN, Double.MIN_NORMAL, 0x1.0p997);
    }

    private static void assertQuotient(double expectedLo, double x, double y) {
        final DD z = DD.fromQuotient(x, y);
        Assertions.assertEquals(x / y, z.hi(), "hi");
        // Requires a delta of 0.0 to assert -0.0 == 0.0
        Assertions.assertEquals(expectedLo, z.lo(), 0.0, "lo");
    }

    /**
     * Adds the two double-double numbers as arguments. Ensured the (x,yy) values is normalized.
     * The argument {@code t} is used for working.
     */
    private static Stream.Builder<Arguments> add(Stream.Builder<Arguments> builder,
            DD x, double y, double yy) {
        final DD t = DD.fastTwoSum(y, yy);
        builder.add(Arguments.of(x.hi(), x.lo(), t.hi(), t.lo()));
        return builder;
    }

    // Subtraction must be consistent with addition


    @Test
    void testInfiniteOperationsCreateNaN() {
        // Demonstrate that operations on inf creates NaN.
        // For this reason special handling of single operations to return the IEEE correct result
        // for overflow are not be required. It is possible to include a multiply that is safe
        // against intermediate overflow. But this may never be used. Instead the class is
        // documented as unsuitable for computations that approach +/- inf, and documented
        // that the multiply is safe when the exponent is < 996.
        for (final DD x : new DD[] {DD.of(Double.POSITIVE_INFINITY)}) {
            for (final DD a : new DD[] {DD.ZERO, DD.ONE}) {
                assertNaN(x.add(a), () -> String.format("%s.add(%s)", x, a));
                assertNaN(x.add(a), () -> String.format("%s.add(%s)", x, a));
                assertNaN(x.add(a), () -> String.format("%s.multiply(%s)", x, a));
            }
            for (final double a : new double[] {0, 1}) {
                assertNaN(x.add(a), () -> String.format("%s.add(%s)", x, a));
                assertNaN(x.add(a), () -> String.format("%s.add(%s)", x, a));
                assertNaN(x.add(a), () -> String.format("%s.multiply(%s)", x, a));
            }
        }
    }

    /**
     * Assert both parts of the DD are NaN.
     */
    private static void assertNaN(DD x, Supplier<String> msg) {
        Assertions.assertEquals(Double.NaN, x.hi(), () -> "hi " + msg.get());
        Assertions.assertEquals(Double.NaN, x.lo(), () -> "lo " + msg.get());
    }

    @ParameterizedTest
    @MethodSource
    void testSqrtSpecialCases(double x, double xx, double hi, double lo) {
        final DD z = DD.of(x, xx).sqrt();
        Assertions.assertEquals(hi, z.hi(), "hi");
        Assertions.assertEquals(lo, z.lo(), "lo");
    }

    @ParameterizedTest
    @MethodSource(value = {"testSqrtSpecialCases"})
    void testAccurateSqrtSpecialCases(double x, double xx, double hi, double lo) {
        final DD z = DDExt.sqrt(DD.of(x, xx));
        Assertions.assertEquals(hi, z.hi(), "hi");
        Assertions.assertEquals(lo, z.lo(), "lo");
    }

    static Stream<Arguments> testSqrtSpecialCases() {
        // Note: Cases for non-normalized numbers are not supported
        // (these are commented out using ///).
        // The method assumes |x| > |xx|.

        final Stream.Builder<Arguments> builder = Stream.builder();
        final double inf = Double.POSITIVE_INFINITY;
        ///final double max = Double.MAX_VALUE;
        final double nan = Double.NaN;
        builder.add(Arguments.of(1, 0, 1, 0));
        builder.add(Arguments.of(4, 0, 2, 0));
        ///builder.add(Arguments.of(0, 1, 1, 0));
        // x+xx is NaN
        builder.add(Arguments.of(nan, 3, nan, 0));
        // x+xx is negative
        builder.add(Arguments.of(-1, 0, nan, 0));
        builder.add(Arguments.of(-inf, 3, nan, 0));
        ///builder.add(Arguments.of(1, -3, nan, 0));
        ///builder.add(Arguments.of(42, -inf, nan, 0));
        // x+xx is infinite
        builder.add(Arguments.of(inf, 0, inf, 0));
        builder.add(Arguments.of(inf, 3, inf, 0));
        ///builder.add(Arguments.of(0, inf, inf, 0));
        ///builder.add(Arguments.of(3, inf, inf, 0));
        ///builder.add(Arguments.of(max, max, inf, 0));
        // x+xx is zero
        final double[] zero = {0.0, -0.0};
        for (final double x : zero) {
            for (final double xx : zero) {
                ///builder.add(Arguments.of(x, xx, x + xx, 0));
                builder.add(Arguments.of(x, xx, x, 0));
            }
        }
        // Numbers are normalized before computation
        ///builder.add(Arguments.of(5, -1, 2, 0));
        ///builder.add(Arguments.of(-1, 5, 2, 0));
        return builder.build();
    }

    @ParameterizedTest
    @CsvSource({
        // Non-scalable numbers:
        // exponent is always zero, (x,xx) is unchanged
        "0.0, 0.0, 0, 0.0, 0.0",
        "0.0, -0.0, 0, 0.0, -0.0",
        "NaN, 0.0, 0, NaN, 0.0",
        "NaN, NaN, 0, NaN, NaN",
        "Infinity, 0.0, 0, Infinity, 0.0",
        "Infinity, NaN, 0, Infinity, NaN",
        // Normalisation of (1, 0)
        "1.0, 0, 1, 0.5, 0",
        "-1.0, 0, 1, -0.5, 0",
        // Power of 2 with round-off to reduce the magnitude
        "0.5, -5.551115123125783E-17, -1, 1.0, -1.1102230246251565E-16",
        "1.0, -1.1102230246251565E-16, 0, 1.0, -1.1102230246251565E-16",
        "2.0, -2.220446049250313E-16, 1, 1.0, -1.1102230246251565E-16",
        "0.5, 5.551115123125783E-17, 0, 0.5, 5.551115123125783E-17",
        "1.0, 1.1102230246251565E-16, 1, 0.5, 5.551115123125783E-17",
        "2.0, 2.220446049250313E-16, 2, 0.5, 5.551115123125783E-17",
    })
    void testFrexpEdgeCases(double x, double xx, int exp, double fx, double fxx) {
        // Initialize to something so we know it changes
        final int[] e = {62783468};
        DD f = DD.of(x, xx).frexp(e);
        Assertions.assertEquals(exp, e[0], "exp");
        Assertions.assertEquals(fx, f.hi(), "hi");
        Assertions.assertEquals(fxx, f.lo(), "lo");
        // Reset
        e[0] = 126943276;
        f = DD.of(-x, -xx).frexp(e);
        Assertions.assertEquals(exp, e[0], "exp");
        Assertions.assertEquals(-fx, f.hi(), "hi");
        Assertions.assertEquals(-fxx, f.lo(), "lo");
    }


    @ParameterizedTest
    @CsvSource({
        // Math.pow(x, 0) == 1, even for non-finite values
        "0.0, 0.0, 0, 1.0, 0.0",
        "1.23, 0.0, 0, 1.0, 0.0",
        "1.0, 0.0, 0, 1.0, 0.0",
        "-2.0, 0.0, 0, 1.0, 0.0",
        "Infinity, 0.0, 0, 1.0, 0.0",
        "NaN, 0.0, 0, 1.0, 0.0",
        // Math.pow(0.0, n) == +/- 0.0
        "0.0, 0.0, 1, 0.0, 0.0",
        "0.0, 0.0, 2, 0.0, 0.0",
        "-0.0, 0.0, 1, -0.0, 0.0",
        "-0.0, 0.0, 2, 0.0, 0.0",
        // Math.pow(1, n) == 1
        "1.0, 0.0, 1, 1.0, 0.0",
        "1.0, 0.0, 2, 1.0, 0.0",
        // Math.pow(-1, n) == +/-1 - requires round-off sign propagation
        "-1.0, 0.0, 1, -1.0, 0.0",
        "-1.0, 0.0, 2, 1.0, -0.0",
        "-1.0, -0.0, 1, -1.0, -0.0",
        "-1.0, -0.0, 2, 1.0, 0.0",
        // Math.pow(0.0, -n)
        "0.0, 0.0, -1, Infinity, 0.0",
        "0.0, 0.0, -2, Infinity, 0.0",
        "-0.0, 0.0, -1, -Infinity, 0.0",
        "-0.0, 0.0, -2, Infinity, 0.0",
        // NaN / Infinite is IEEE pow result for x
        "Infinity, 0.0, 1, Infinity, 0.0, 0",
        "-Infinity, 0.0, 1, -Infinity, 0.0, 0",
        "-Infinity, 0.0, 2, Infinity, 0.0, 0",
        "Infinity, 0.0, -1, 0.0, 0.0, 0",
        "-Infinity, 0.0, -1, -0.0, 0.0, 0",
        "-Infinity, 0.0, -2, 0.0, 0.0, 0",
        "NaN, 0.0, 1, NaN, 0.0, 0",
        // Inversion creates infinity (sub-normal x^-n < 2.22e-308)
        // Signed zeros should match inversion when the result is large and finite.
        "1e-312, 0.0, -1, Infinity, -0.0",
        "1e-312, -0.0, -1, Infinity, -0.0",
        "-1e-312, 0.0, -1, -Infinity, 0.0",
        "-1e-312, -0.0, -1, -Infinity, 0.0",
        "1e-156, 0.0, -2, Infinity, -0.0",
        "1e-156, -0.0, -2, Infinity, -0.0",
        "-1e-156, 0.0, -2, Infinity, -0.0",
        "-1e-156, -0.0, -2, Infinity, -0.0",
        "1e-106, 0.0, -3, Infinity, -0.0",
        "1e-106, -0.0, -3, Infinity, -0.0",
        "-1e-106, 0.0, -3, -Infinity, 0.0",
        "-1e-106, -0.0, -3, -Infinity, 0.0",
    })
    void testSimplePowEdgeCases(double x, double xx, int n, double z, double zz) {
        final DD f = DDExt.simplePow(x, xx, n);
        Assertions.assertEquals(z, f.hi(), "hi");
        Assertions.assertEquals(zz, f.lo(), "lo");
    }

    @ParameterizedTest
    @MethodSource(value = {"testPowScaledEdgeCases"})
    void testSimplePowScaledEdgeCases(double x, double xx, int n, double z, double zz, long exp) {
        final long[] e = {126384};
        final DD f = DDExt.simplePowScaled(x, xx, n, e);
        Assertions.assertEquals(z, f.hi(), "hi");
        Assertions.assertEquals(zz, f.lo(), "lo");
        Assertions.assertEquals(exp, e[0], "exp");
    }

    /**
     * Test cases of {@link DDExt#simplePowScaled(double, double, int, long[])} where no scaling is
     * required. It should be the same as {@link DDExt#simplePow(double, double, int)}.
     */
    @ParameterizedTest
    @CsvSource({
        "1.23, 0.0, 3",
        "1.23, 0.0, -3",
        "1.23, 1e-16, 2",
        "1.23, 1e-16, -2",
        // No underflow - Do not get close to underflowing the low part
        "0.5, 1e-17, 900",
        // x > sqrt(0.5)
        "0.75, 1e-17, 2000",  // 1.33e-250
        "0.9, 1e-17, 5000",   // 1.63e-229
        "0.99, 1e-17, 50000", // 5.75e-219
        "0.75, 1e-17, 100",   // (safe n)
        "0.9999999999999999, 1e-17, 2147483647", // (safe x)
        // No overflow
        "2.0, 1e-16, 1000",
        // 2x < sqrt(0.5)
        "1.5, 1e-16, 1500",   // 1.37e264
        "1.1, 1e-16, 6000",   // 2.27e248
        "1.01, 1e-16, 60000", // 1.92e259
        "2.0, 1e-16, 100",   // (safe n)
        "1.0000000000000002, 1e-17, 2147483647", // (safe x)
    })
    void testSimplePowScaledSafe(double x, double xx, int n) {
        final long[] exp = {61273468};
        final DD f = DDExt.simplePowScaled(x, xx, n, exp);
        // Same
        DD z = DDExt.simplePow(x, xx, n);
        final int[] ez = {168168681};
        z = z.frexp(ez);
        Assertions.assertEquals(z.hi(), f.hi(), "hi");
        Assertions.assertEquals(z.lo(), f.lo(), "lo");
        Assertions.assertEquals(ez[0], exp[0], "exp");
    }

    @ParameterizedTest
    @MethodSource
    void testPowNotNormalizedFinite(double x, int n) {
        final Supplier<String> msg = () -> String.format("(%s,0)^%d", x, n);
        final DD s = DD.of(x).pow(n);
        Assertions.assertEquals(Math.pow(x, n), s.hi(), () -> msg.get() + " hi");
        Assertions.assertEquals(0, s.lo(), () -> msg.get() + " lo");
    }

    static Stream<Arguments> testPowNotNormalizedFinite() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        for (final int n : new int[] {-2, -1, 0, 1, 2}) {
            for (final double x : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                    Math.nextDown(Double.MIN_NORMAL), Double.MIN_VALUE, 0}) {
                builder.add(Arguments.of(x, n));
                builder.add(Arguments.of(-x, n));
            }
        }
        return builder.build();
    }

    @ParameterizedTest
    @MethodSource(value = {"testPowScaledEdgeCases"})
    void testPowScaledEdgeCases(double x, double xx, int n, double z, double zz, long exp) {
        final long[] e = {457578688};
        final DD f = DD.of(x, xx).pow(n, e);
        Assertions.assertEquals(z, f.hi(), "hi");
        Assertions.assertEquals(zz, f.lo(), "lo");
        Assertions.assertEquals(exp, e[0], "exp");
    }

    static Stream<Arguments> testPowScaledEdgeCases() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        final double inf = Double.POSITIVE_INFINITY;
        final double nan = Double.NaN;
        // Math.pow(x, 0) == 1, even for non-finite values (fractional representation)
        builder.add(Arguments.of(0.0, 0.0, 0, 0.5, 0.0, 1));
        builder.add(Arguments.of(1.23, 0.0, 0, 0.5, 0.0, 1));
        builder.add(Arguments.of(1.0, 0.0, 0, 0.5, 0.0, 1));
        builder.add(Arguments.of(inf, 0.0, 0, 0.5, 0.0, 1));
        builder.add(Arguments.of(nan, 0.0, 0, 0.5, 0.0, 1));
        // Math.pow(0.0, n) == +/- 0.0 (no fractional representation)
        builder.add(Arguments.of(0.0, 0.0, 1, 0.0, 0.0, 0));
        builder.add(Arguments.of(0.0, 0.0, 2, 0.0, 0.0, 0));
        builder.add(Arguments.of(-0.0, 0.0, 1, -0.0, 0.0, 0));
        builder.add(Arguments.of(-0.0, 0.0, 2, 0.0, 0.0, 0));
        // Math.pow(1, n) == 1 (fractional representation)
        builder.add(Arguments.of(1.0, 0.0, 1, 0.5, 0.0, 1));
        builder.add(Arguments.of(1.0, 0.0, 2, 0.5, 0.0, 1));
        // Math.pow(-1, n) == +/-1 (fractional representation) - requires round-off sign propagation
        builder.add(Arguments.of(-1.0, 0.0, 1, -0.5, 0.0, 1));
        builder.add(Arguments.of(-1.0, 0.0, 2, 0.5, -0.0, 1));
        builder.add(Arguments.of(-1.0, -0.0, 1, -0.5, -0.0, 1));
        builder.add(Arguments.of(-1.0, -0.0, 2, 0.5, 0.0, 1));
        // Math.pow(0.0, -n) - No fractional representation
        builder.add(Arguments.of(0.0, 0.0, -1, inf, 0.0, 0));
        builder.add(Arguments.of(0.0, 0.0, -2, inf, 0.0, 0));
        builder.add(Arguments.of(-0.0, 0.0, -1, -inf, 0.0, 0));
        builder.add(Arguments.of(-0.0, 0.0, -2, inf, 0.0, 0));
        // NaN / Infinite is IEEE pow result for x
        builder.add(Arguments.of(inf, 0.0, 1, inf, 0.0, 0));
        builder.add(Arguments.of(-inf, 0.0, 1, -inf, 0.0, 0));
        builder.add(Arguments.of(-inf, 0.0, 2, inf, 0.0, 0));
        builder.add(Arguments.of(inf, 0.0, -1, 0.0, 0.0, 0));
        builder.add(Arguments.of(-inf, 0.0, -1, -0.0, 0.0, 0));
        builder.add(Arguments.of(-inf, 0.0, -2, 0.0, 0.0, 0));
        builder.add(Arguments.of(nan, 0.0, 1, nan, 0.0, 0));
        // Hit edge case of zero low part
        builder.add(Arguments.of(0.5, 0.0, -1, 0.5, 0.0, 2));
        builder.add(Arguments.of(1.0, 0.0, -1, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, -1, 0.5, 0.0, 0));
        builder.add(Arguments.of(4.0, 0.0, -1, 0.5, 0.0, -1));
        builder.add(Arguments.of(0.5, 0.0, 2, 0.5, 0.0, -1));
        builder.add(Arguments.of(1.0, 0.0, 2, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, 2, 0.5, 0.0, 3));
        builder.add(Arguments.of(4.0, 0.0, 2, 0.5, 0.0, 5));
        // Exact power of two (representable)
        // Math.pow(0.5, 123) == 0.5 * Math.scalb(1.0, -122)
        // Math.pow(2.0, 123) == 0.5 * Math.scalb(1.0, 124)
        builder.add(Arguments.of(0.5, 0.0, 123, 0.5, 0.0, -122));
        builder.add(Arguments.of(1.0, 0.0, 123, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, 123, 0.5, 0.0, 124));
        builder.add(Arguments.of(0.5, 0.0, -123, 0.5, 0.0, 124));
        builder.add(Arguments.of(1.0, 0.0, -123, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, -123, 0.5, 0.0, -122));
        // Exact power of two (not representable)
        builder.add(Arguments.of(0.5, 0.0, 12345, 0.5, 0.0, -12344));
        builder.add(Arguments.of(1.0, 0.0, 12345, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, 12345, 0.5, 0.0, 12346));
        builder.add(Arguments.of(0.5, 0.0, -12345, 0.5, 0.0, 12346));
        builder.add(Arguments.of(1.0, 0.0, -12345, 0.5, 0.0, 1));
        builder.add(Arguments.of(2.0, 0.0, -12345, 0.5, 0.0, -12344));
        return builder.build();
    }

    static Stream<Arguments> testPowScaledLargeN() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        // The scaled BigDecimal power is pre-computed as it takes >10 seconds per case.
        // Results are obtained from the debugging assertion
        // message in TestUtils and thus the BigDecimal is rounded to DECIMAL128 format.
        // simplePowScaled loses ~ 67-bits from a double-double (14-bits from a double).
        // fastPowScaled   loses ~ 26-bits from a double-double.
        // powScaled       loses ~ 1-bit from a double-double.
        builder.add(Arguments.of(1.402774996679172, 4.203934137477261E-17, 58162209, 28399655, "0.5069511623667528687158515355802548", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4024304626662112, -1.4084179645855846E-17, 55066019, 26868321, "0.8324073012126417513056910315887745", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4125582593027008, -3.545476880711939E-17, 50869441, 25348771, "0.5062665858255789519032946906819150", 0x1.0p-38, 0x1.0p-80));
        builder.add(Arguments.of(1.4119649130236207, -6.64913621578422E-17, 57868054, 28801176, "0.8386830789932243373181320367289536", 0x1.0p-41, 0x1.0p-80));
        builder.add(Arguments.of(1.4138979166089836, 1.9810424188649008E-17, 57796577, 28879676, "0.8521759805456274150644862351758441", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4145051107021165, 6.919285583856237E-17, -58047003, -29040764, "0.9609529369187483264098384290609811", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4146512942500389, 5.809007274041755E-17, -52177565, -26112078, "0.6333625587966193592039026704846324", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4145748596525067, -1.7347735766459908E-17, -58513216, -29278171, "0.6273407549603278011188148414634989", 0x1.0p-39, 0x1.0p-80));
        builder.add(Arguments.of(1.4120799563428865, -5.594285001190042E-17, -52544350, -26157721, "0.5406504832406102336189856859270558", 0x1.0p-38, 0x1.0p-80));
        builder.add(Arguments.of(1.4092258370859025, -8.549761437095368E-17, -51083370, -25281304, "0.7447168954354128135078570760787011", 0x1.0p-39, 0x1.0p-80));
        return builder.build();
    }

    // Note: equals and hashcode tests adapted from ComplexTest (since Complex is
    // also an immutable tuple of two doubles)

    @Test
    void testEqualsWithNull() {
        final DD x = DD.of(3.0);
        Assertions.assertNotEquals(x, null);
    }

    @Test
    void testEqualsWithAnotherClass() {
        final DD x = DD.of(3.0);
        Assertions.assertNotEquals(x, new Object());
    }

    @Test
    void testEqualsWithSameObject() {
        final DD x = DD.of(3.0);
        Assertions.assertEquals(x, x);
    }

    @Test
    void testEqualsWithCopyObject() {
        final DD x = DD.of(3.0);
        final DD y = DD.of(3.0);
        Assertions.assertEquals(x, y);
    }

    @Test
    void testEqualsWithHiDifference() {
        final DD x = DD.of(0.0, 0.0);
        final DD y = DD.of(Double.MIN_VALUE, 0.0);
        Assertions.assertNotEquals(x, y);
    }

    @Test
    void testEqualsWithLoDifference() {
        final DD x = DD.of(1.0, 0.0);
        final DD y = DD.of(1.0, Double.MIN_VALUE);
        Assertions.assertNotEquals(x, y);
    }

    /**
     * Test {@link DD#equals(Object)}. It should be consistent with
     * {@link Arrays#equals(double[], double[])} called using the components of two
     * DD numbers.
     */
    @Test
    void testEqualsIsConsistentWithArraysEquals() {
        // Explicit check of the cases documented in the Javadoc:
        assertEqualsIsConsistentWithArraysEquals(DD.of(Double.NaN, 0.0),
            DD.of(Double.NaN, 1.0), "NaN high and different non-NaN low");
        assertEqualsIsConsistentWithArraysEquals(DD.of(0.0, Double.NaN),
            DD.of(1.0, Double.NaN), "Different non-NaN high and NaN low");
        assertEqualsIsConsistentWithArraysEquals(DD.of(0.0, 0.0), DD.of(-0.0, 0.0),
            "Different high zeros");
        assertEqualsIsConsistentWithArraysEquals(DD.of(0.0, 0.0), DD.of(0.0, -0.0),
            "Different low zeros");

        // Test some values of edge cases
        final double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1, 0, 1};
        final ArrayList<DD> list = createCombinations(values);

        for (final DD c : list) {
            final double hi = c.hi();
            final double lo = c.lo();

            // Check a copy is equal
            assertEqualsIsConsistentWithArraysEquals(c, DD.of(hi, lo), "Copy DD");

            // Perform the smallest change to the two components
            final double hiDelta = smallestChange(hi);
            final double loDelta = smallestChange(lo);
            Assertions.assertNotEquals(hi, hiDelta, "high was not changed");
            Assertions.assertNotEquals(lo, loDelta, "low was not changed");

            assertEqualsIsConsistentWithArraysEquals(c, DD.of(hiDelta, lo), "Delta high");
            assertEqualsIsConsistentWithArraysEquals(c, DD.of(hi, loDelta), "Delta low");
        }
    }

    /**
     * Specific test to target different representations that contain NaN are {@code false}
     * for {@link DD#equals(Object)}.
     */
    @Test
    void testEqualsWithDifferentNaNs() {
        // Test some NaN combinations
        final ArrayList<DD> list = createCombinations(Double.NaN, 0, 1);

        // Is the all-vs-all comparison only the exact same values should be equal, e.g.
        // (nan,0) not equals (nan,nan)
        // (nan,0) equals (nan,0)
        // (nan,0) not equals (0,nan)
        for (int i = 0; i < list.size(); i++) {
            final DD c1 = list.get(i);
            final DD copy = DD.of(c1.hi(), c1.lo());
            assertEqualsIsConsistentWithArraysEquals(c1, copy, "Copy is not equal");
            for (int j = i + 1; j < list.size(); j++) {
                final DD c2 = list.get(j);
                assertEqualsIsConsistentWithArraysEquals(c1, c2, "Different NaNs should not be equal");
            }
        }
    }

    /**
     * Test the two DD numbers with {@link DD#equals(Object)} and check the
     * result is consistent with {@link Arrays#equals(double[], double[])}.
     *
     * @param c1 the first DD
     * @param c2 the second DD
     * @param msg the message to append to an assertion error
     */
    private static void assertEqualsIsConsistentWithArraysEquals(DD c1, DD c2, String msg) {
        final boolean expected = Arrays.equals(new double[] {c1.hi() + 0.0, c1.lo() + 0.0},
                                               new double[] {c2.hi() + 0.0, c2.lo() + 0.0});
        final boolean actual = c1.equals(c2);
        Assertions.assertEquals(expected, actual,
            () -> String.format("equals(Object) is not consistent with Arrays.equals: %s. %s vs %s", msg, c1, c2));
    }

    /**
     * Test {@link DD#hashCode()}. It should be consistent with
     * {@link Arrays#hashCode(double[])} called using the components of the DD number
     * and fulfil the contract of {@link Object#hashCode()}, i.e. objects with different
     * hash codes are {@code false} for {@link Object#equals(Object)}.
     */
    @Test
    void testHashCode() {
        // Test some values match Arrays.hashCode(double[])
        final double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -3.45, -1, -0.0, 0.0, Double.MIN_VALUE, 1, 3.45,
            Double.POSITIVE_INFINITY};
        final ArrayList<DD> list = createCombinations(values);

        final String msg = "'equals' not compatible with 'hashCode'";

        for (final DD c : list) {
            final double hi = c.hi();
            final double lo = c.lo();
            final int expected = Arrays.hashCode(new double[] {hi + 0.0, lo + 0.0});
            final int hash = c.hashCode();
            Assertions.assertEquals(expected, hash, "hashCode does not match Arrays.hashCode({re, im})");

            // Test a copy has the same hash code, i.e. is not
            // System.identityHashCode(Object)
            final DD copy = DD.of(hi, lo);
            Assertions.assertEquals(hash, copy.hashCode(), "Copy hash code is not equal");

            // MATH-1118
            // "equals" and "hashCode" must be compatible: if two objects have
            // different hash codes, "equals" must return false.
            // Perform the smallest change to the two components.
            // Note: The hash could actually be the same so we check it changes.
            final double hiDelta = smallestChange(hi);
            final double loDelta = smallestChange(lo);
            Assertions.assertNotEquals(hi, hiDelta, "hi was not changed");
            Assertions.assertNotEquals(lo, loDelta, "lo was not changed");

            final DD cHiDelta = DD.of(hiDelta, lo);
            final DD cLoDelta = DD.of(hi, loDelta);
            if (hash != cHiDelta.hashCode()) {
                Assertions.assertNotEquals(c, cHiDelta, () -> "hi+delta: " + msg);
            }
            if (hash != cLoDelta.hashCode()) {
                Assertions.assertNotEquals(c, cLoDelta, () -> "lo+delta: " + msg);
            }
        }
    }

    /**
     * Specific test that different representations of zero satisfy the contract of
     * {@link Object#hashCode()}: if two objects have different hash codes, "equals" must
     * return false. This is an issue with using {@link Double#hashCode(double)} to create
     * hash codes and {@code ==} for equality when using different representations of
     * zero: Double.hashCode(-0.0) != Double.hashCode(0.0) but -0.0 == 0.0 is
     * {@code true}.
     *
     * @see <a
     * href="https://issues.apache.org/jira/projects/MATH/issues/MATH-1118">MATH-1118</a>
     */
    @Test
    void testHashCodeWithDifferentZeros() {
        final ArrayList<DD> list = createCombinations(-0.0, 0.0);

        // Explicit test for issue MATH-1118
        // "equals" and "hashCode" must be compatible
        for (int i = 0; i < list.size(); i++) {
            final DD c1 = list.get(i);
            for (int j = i; j < list.size(); j++) {
                final DD c2 = list.get(j);
                Assertions.assertEquals(c1.hashCode(), c2.hashCode());
                Assertions.assertEquals(c1, c2);
                Assertions.assertEquals(c2, c1);
            }
        }
    }

    /**
     * Creates a list of DD numbers using an all-vs-all combination of the provided
     * values for both the parts.
     *
     * @param values the values
     * @return the list
     */
    private static ArrayList<DD> createCombinations(double... values) {
        final ArrayList<DD> list = new ArrayList<>(values.length * values.length);
        for (final double x : values) {
            for (final double xx : values) {
                list.add(DD.of(x, xx));
            }
        }
        return list;
    }

    /**
     * Perform the smallest change to the value. This returns the next double value
     * adjacent to d in the direction of infinity. Edge cases: if already infinity then
     * return the next closest in the direction of negative infinity; if nan then return
     * 0.
     *
     * @param x the x
     * @return the new value
     */
    private static double smallestChange(double x) {
        if (Double.isNaN(x)) {
            return 0;
        }
        return x == Double.POSITIVE_INFINITY ? Math.nextDown(x) : Math.nextUp(x);
    }

    @Test
    void testIsNotNormal() {
        for (double a : new double[] {Double.MAX_VALUE, 1.0, Double.MIN_NORMAL}) {
            Assertions.assertFalse(DD.isNotNormal(a));
            Assertions.assertFalse(DD.isNotNormal(-a));
        }
        for (double a : new double[] {Double.POSITIVE_INFINITY, 0.0, Double.MIN_VALUE,
                                      Math.nextDown(Double.MIN_NORMAL), Double.NaN}) {
            Assertions.assertTrue(DD.isNotNormal(a));
            Assertions.assertTrue(DD.isNotNormal(-a));
        }
    }


    /**
     * Create a BigDecimal for the given value.
     *
     * @param v Value
     * @return the BigDecimal
     */
    private static BigDecimal bd(double v) {
        return new BigDecimal(v);
    }

    /**
     * Assert the number is normalized such that {@code |xx| <= eps * |x|}.
     *
     * @param x High part.
     * @param xx Low part.
     * @param name Name of the number.
     */
    private static void assertNormalized(double x, double xx, String name) {
        // Note: Use delta of 0 to allow addition of signed zeros (which may change the sign)
        Assertions.assertEquals(x, x + xx, 0.0, () -> name + " not a normalized double-double");
    }

    /**
     * Class to compute the error statistics.
     *
     * <p>This class can be used to summarise relative errors if used as the DoubleConsumer
     * argument to {@link TestUtils#assertEquals(BigDecimal, DD, double, java.util.function.DoubleConsumer, Supplier)}.
     * Errors below the precision of a double-double number are treated as zero.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Root_mean_square">Wikipedia: RMS</a>
     */
    static class ErrorStatistics {
        /** Sum of squared error. */
        private DD ss = DD.ZERO;
        /** Maximum absolute error. */
        private double maxAbs;
        /** Number of terms. */
        private int n;
        /** Positive sum. */
        private DD ps = DD.ZERO;
        /** Negative sum. */
        private DD ns = DD.ZERO;

        /**
         * Add the relative error. Values below 2^-107 are ignored.
         *
         * @param x Value
         */
        void add(double x) {
            n++;
            // Ignore errors below 2^-107. This is the effective half ULP limit of DD and
            // it is not possible to get closer.
            if (Math.abs(x) <= 0x1.0p-107) {
                return;
            }
            // Overflow is not supported.
            // Assume the expected and actual are quite close when measuring the RMS.
            // Here we sum the regular square for speed.
            ss = add(ss, x * x);
            // Summing terms of the same sign avoids cancellation in the working sums.
            if (x < 0) {
                ns = add(ns, x);
                maxAbs = maxAbs < -x ? -x : maxAbs;
            } else {
                ps = add(ps, x);
                //ps = ps.add(x);
                maxAbs = maxAbs < x ? x : maxAbs;
            }
        }

        /**
         * Adds the term to the total.
         *
         * @param dd Total
         * @param x Value
         * @return the new total
         */
        private static DD add(DD dd, double x) {
            // We use a fastTwoSum here for speed. This is equivalent to a Kahan summation
            // of the total and is accurate if the total is larger than the terms.
            return DD.fastTwoSum(dd.hi(), dd.lo() + x);
        }

        /**
         * Gets the count of recorded values.
         *
         * @return the size
         */
        int size() {
            return n;
        }

        /**
         * Gets the maximum absolute error.
         *
         * <p>This can be used to set maximum ULP thresholds for test data if the
         * TestUtils.assertEquals method is used with a large maxUlps to measure the ulp
         * (and effectively ignore failures) and the maximum reported as the end of
         * testing.
         *
         * @return maximum absolute error
         */
        double getMaxAbs() {
            return maxAbs;
        }

        /**
         * Gets the root mean squared error (RMS).
         *
         * <p> Note: If no data has been added this will return 0/0 = nan.
         * This prevents using in assertions without adding data.
         *
         * @return root mean squared error (RMS)
         */
        double getRMS() {
            return n == 0 ? 0 : ss.divide(n).sqrt().doubleValue();
        }

        /**
         * Gets the mean error.
         *
         * <p>The mean can be used to determine if the error is consistently above or
         * below zero.
         *
         * @return mean error
         */
        double getMean() {
            return n == 0 ? 0 : ps.add(ns).divide(n).doubleValue();
        }
    }
}
