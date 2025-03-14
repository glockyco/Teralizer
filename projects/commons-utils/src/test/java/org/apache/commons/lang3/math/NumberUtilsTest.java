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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link org.apache.commons.lang3.math.NumberUtils}.
 */
public class NumberUtilsTest {
    @Test
    public void compareByte() {
        assertTrue(NumberUtils.compare((byte) -3, (byte) 0) < 0);
        assertEquals(0, NumberUtils.compare((byte) 113, (byte) 113));
        assertTrue(NumberUtils.compare((byte) 123, (byte) 32) > 0);
    }

    @Test
    public void compareInt() {
        assertTrue(NumberUtils.compare(-3, 0) < 0);
        assertEquals(0, NumberUtils.compare(113, 113));
        assertTrue(NumberUtils.compare(213, 32) > 0);
    }

    @Test
    public void compareLong() {
        assertTrue(NumberUtils.compare(-3L, 0L) < 0);
        assertEquals(0, NumberUtils.compare(113L, 113L));
        assertTrue(NumberUtils.compare(213L, 32L) > 0);
    }

    @Test
    public void compareShort() {
        assertTrue(NumberUtils.compare((short) -3, (short) 0) < 0);
        assertEquals(0, NumberUtils.compare((short) 113, (short) 113));
        assertTrue(NumberUtils.compare((short) 213, (short) 32) > 0);
    }

    private boolean isApplyNonNull(final String s, final Function<String, ?> function) {
        try {
            assertNotNull(function.apply(s));
            return true;
        } catch (final Exception e) {
            if (!s.matches(".*\\s.*")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private boolean isNumberFormatParsable(final String s) {
        final NumberFormat instance = NumberFormat.getInstance();
        try {
            // Stops parsing when a space is found, then returns an object.
            assertNotNull(instance.parse(s));
            return true;
        } catch (final ParseException e) {
            return false;
        }
    }

    private boolean isNumberIntegerOnlyFormatParsable(final String s) {
        final NumberFormat instance = NumberFormat.getInstance();
        instance.setParseIntegerOnly(true);
        try {
            // Stops parsing when a space is found, then returns an object.
            assertNotNull(instance.parse(s));
            return true;
        } catch (final ParseException e) {
            return false;
        }
    }

    @Test
    public void testCompareDouble() {
        assertEquals(0, Double.compare(Double.NaN, Double.NaN));
        assertEquals(Double.compare(Double.NaN, Double.POSITIVE_INFINITY), +1);
        assertEquals(Double.compare(Double.NaN, Double.MAX_VALUE), +1);
        assertEquals(Double.compare(Double.NaN, 1.2d), +1);
        assertEquals(Double.compare(Double.NaN, 0.0d), +1);
        assertEquals(Double.compare(Double.NaN, -0.0d), +1);
        assertEquals(Double.compare(Double.NaN, -1.2d), +1);
        assertEquals(Double.compare(Double.NaN, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(Double.NaN, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(Double.POSITIVE_INFINITY, Double.NaN), -1);
        assertEquals(0, Double.compare(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, Double.MAX_VALUE), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, 1.2d), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, 0.0d), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, -0.0d), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, -1.2d), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(Double.MAX_VALUE, Double.NaN), -1);
        assertEquals(Double.compare(Double.MAX_VALUE, Double.POSITIVE_INFINITY), -1);
        assertEquals(0, Double.compare(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(Double.compare(Double.MAX_VALUE, 1.2d), +1);
        assertEquals(Double.compare(Double.MAX_VALUE, 0.0d), +1);
        assertEquals(Double.compare(Double.MAX_VALUE, -0.0d), +1);
        assertEquals(Double.compare(Double.MAX_VALUE, -1.2d), +1);
        assertEquals(Double.compare(Double.MAX_VALUE, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(Double.MAX_VALUE, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(1.2d, Double.NaN), -1);
        assertEquals(Double.compare(1.2d, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(1.2d, Double.MAX_VALUE), -1);
        assertEquals(0, Double.compare(1.2d, 1.2d));
        assertEquals(Double.compare(1.2d, 0.0d), +1);
        assertEquals(Double.compare(1.2d, -0.0d), +1);
        assertEquals(Double.compare(1.2d, -1.2d), +1);
        assertEquals(Double.compare(1.2d, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(1.2d, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(0.0d, Double.NaN), -1);
        assertEquals(Double.compare(0.0d, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(0.0d, Double.MAX_VALUE), -1);
        assertEquals(Double.compare(0.0d, 1.2d), -1);
        assertEquals(0, Double.compare(0.0d, 0.0d));
        assertEquals(Double.compare(0.0d, -0.0d), +1);
        assertEquals(Double.compare(0.0d, -1.2d), +1);
        assertEquals(Double.compare(0.0d, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(0.0d, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(-0.0d, Double.NaN), -1);
        assertEquals(Double.compare(-0.0d, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(-0.0d, Double.MAX_VALUE), -1);
        assertEquals(Double.compare(-0.0d, 1.2d), -1);
        assertEquals(Double.compare(-0.0d, 0.0d), -1);
        assertEquals(0, Double.compare(-0.0d, -0.0d));
        assertEquals(Double.compare(-0.0d, -1.2d), +1);
        assertEquals(Double.compare(-0.0d, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(-0.0d, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(-1.2d, Double.NaN), -1);
        assertEquals(Double.compare(-1.2d, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(-1.2d, Double.MAX_VALUE), -1);
        assertEquals(Double.compare(-1.2d, 1.2d), -1);
        assertEquals(Double.compare(-1.2d, 0.0d), -1);
        assertEquals(Double.compare(-1.2d, -0.0d), -1);
        assertEquals(0, Double.compare(-1.2d, -1.2d));
        assertEquals(Double.compare(-1.2d, -Double.MAX_VALUE), +1);
        assertEquals(Double.compare(-1.2d, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(-Double.MAX_VALUE, Double.NaN), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, Double.MAX_VALUE), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, 1.2d), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, 0.0d), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, -0.0d), -1);
        assertEquals(Double.compare(-Double.MAX_VALUE, -1.2d), -1);
        assertEquals(0, Double.compare(-Double.MAX_VALUE, -Double.MAX_VALUE));
        assertEquals(Double.compare(-Double.MAX_VALUE, Double.NEGATIVE_INFINITY), +1);

        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, Double.NaN), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, Double.MAX_VALUE), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, 1.2d), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, 0.0d), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, -0.0d), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, -1.2d), -1);
        assertEquals(Double.compare(Double.NEGATIVE_INFINITY, -Double.MAX_VALUE), -1);
        assertEquals(0, Double.compare(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testCompareFloat() {
        assertEquals(0, Float.compare(Float.NaN, Float.NaN));
        assertEquals(Float.compare(Float.NaN, Float.POSITIVE_INFINITY), +1);
        assertEquals(Float.compare(Float.NaN, Float.MAX_VALUE), +1);
        assertEquals(Float.compare(Float.NaN, 1.2f), +1);
        assertEquals(Float.compare(Float.NaN, 0.0f), +1);
        assertEquals(Float.compare(Float.NaN, -0.0f), +1);
        assertEquals(Float.compare(Float.NaN, -1.2f), +1);
        assertEquals(Float.compare(Float.NaN, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(Float.NaN, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(Float.POSITIVE_INFINITY, Float.NaN), -1);
        assertEquals(0, Float.compare(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, Float.MAX_VALUE), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, 1.2f), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, 0.0f), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, -0.0f), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, -1.2f), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(Float.MAX_VALUE, Float.NaN), -1);
        assertEquals(Float.compare(Float.MAX_VALUE, Float.POSITIVE_INFINITY), -1);
        assertEquals(0, Float.compare(Float.MAX_VALUE, Float.MAX_VALUE));
        assertEquals(Float.compare(Float.MAX_VALUE, 1.2f), +1);
        assertEquals(Float.compare(Float.MAX_VALUE, 0.0f), +1);
        assertEquals(Float.compare(Float.MAX_VALUE, -0.0f), +1);
        assertEquals(Float.compare(Float.MAX_VALUE, -1.2f), +1);
        assertEquals(Float.compare(Float.MAX_VALUE, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(Float.MAX_VALUE, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(1.2f, Float.NaN), -1);
        assertEquals(Float.compare(1.2f, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(1.2f, Float.MAX_VALUE), -1);
        assertEquals(0, Float.compare(1.2f, 1.2f));
        assertEquals(Float.compare(1.2f, 0.0f), +1);
        assertEquals(Float.compare(1.2f, -0.0f), +1);
        assertEquals(Float.compare(1.2f, -1.2f), +1);
        assertEquals(Float.compare(1.2f, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(1.2f, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(0.0f, Float.NaN), -1);
        assertEquals(Float.compare(0.0f, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(0.0f, Float.MAX_VALUE), -1);
        assertEquals(Float.compare(0.0f, 1.2f), -1);
        assertEquals(0, Float.compare(0.0f, 0.0f));
        assertEquals(Float.compare(0.0f, -0.0f), +1);
        assertEquals(Float.compare(0.0f, -1.2f), +1);
        assertEquals(Float.compare(0.0f, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(0.0f, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(-0.0f, Float.NaN), -1);
        assertEquals(Float.compare(-0.0f, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(-0.0f, Float.MAX_VALUE), -1);
        assertEquals(Float.compare(-0.0f, 1.2f), -1);
        assertEquals(Float.compare(-0.0f, 0.0f), -1);
        assertEquals(0, Float.compare(-0.0f, -0.0f));
        assertEquals(Float.compare(-0.0f, -1.2f), +1);
        assertEquals(Float.compare(-0.0f, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(-0.0f, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(-1.2f, Float.NaN), -1);
        assertEquals(Float.compare(-1.2f, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(-1.2f, Float.MAX_VALUE), -1);
        assertEquals(Float.compare(-1.2f, 1.2f), -1);
        assertEquals(Float.compare(-1.2f, 0.0f), -1);
        assertEquals(Float.compare(-1.2f, -0.0f), -1);
        assertEquals(0, Float.compare(-1.2f, -1.2f));
        assertEquals(Float.compare(-1.2f, -Float.MAX_VALUE), +1);
        assertEquals(Float.compare(-1.2f, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(-Float.MAX_VALUE, Float.NaN), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, Float.MAX_VALUE), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, 1.2f), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, 0.0f), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, -0.0f), -1);
        assertEquals(Float.compare(-Float.MAX_VALUE, -1.2f), -1);
        assertEquals(0, Float.compare(-Float.MAX_VALUE, -Float.MAX_VALUE));
        assertEquals(Float.compare(-Float.MAX_VALUE, Float.NEGATIVE_INFINITY), +1);

        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, Float.NaN), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, Float.MAX_VALUE), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, 1.2f), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, 0.0f), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, -0.0f), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, -1.2f), -1);
        assertEquals(Float.compare(Float.NEGATIVE_INFINITY, -Float.MAX_VALUE), -1);
        assertEquals(0, Float.compare(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY));
    }

    @SuppressWarnings("cast") // suppress instanceof warning check
    @Test
    public void testConstants() {
        assertInstanceOf(Long.class, NumberUtils.LONG_ZERO);
        assertInstanceOf(Long.class, NumberUtils.LONG_ONE);
        assertInstanceOf(Long.class, NumberUtils.LONG_MINUS_ONE);
        assertInstanceOf(Integer.class, NumberUtils.INTEGER_ZERO);
        assertInstanceOf(Integer.class, NumberUtils.INTEGER_ONE);
        assertInstanceOf(Integer.class, NumberUtils.INTEGER_MINUS_ONE);
        assertInstanceOf(Short.class, NumberUtils.SHORT_ZERO);
        assertInstanceOf(Short.class, NumberUtils.SHORT_ONE);
        assertInstanceOf(Short.class, NumberUtils.SHORT_MINUS_ONE);
        assertInstanceOf(Byte.class, NumberUtils.BYTE_ZERO);
        assertInstanceOf(Byte.class, NumberUtils.BYTE_ONE);
        assertInstanceOf(Byte.class, NumberUtils.BYTE_MINUS_ONE);
        assertInstanceOf(Double.class, NumberUtils.DOUBLE_ZERO);
        assertInstanceOf(Double.class, NumberUtils.DOUBLE_ONE);
        assertInstanceOf(Double.class, NumberUtils.DOUBLE_MINUS_ONE);
        assertInstanceOf(Float.class, NumberUtils.FLOAT_ZERO);
        assertInstanceOf(Float.class, NumberUtils.FLOAT_ONE);
        assertInstanceOf(Float.class, NumberUtils.FLOAT_MINUS_ONE);

        assertEquals(0, NumberUtils.LONG_ZERO.longValue());
        assertEquals(1, NumberUtils.LONG_ONE.longValue());
        assertEquals(NumberUtils.LONG_MINUS_ONE.longValue(), -1);
        assertEquals(0, NumberUtils.INTEGER_ZERO.intValue());
        assertEquals(1, NumberUtils.INTEGER_ONE.intValue());
        assertEquals(NumberUtils.INTEGER_MINUS_ONE.intValue(), -1);
        assertEquals(0, NumberUtils.SHORT_ZERO.shortValue());
        assertEquals(1, NumberUtils.SHORT_ONE.shortValue());
        assertEquals(NumberUtils.SHORT_MINUS_ONE.shortValue(), -1);
        assertEquals(0, NumberUtils.BYTE_ZERO.byteValue());
        assertEquals(1, NumberUtils.BYTE_ONE.byteValue());
        assertEquals(NumberUtils.BYTE_MINUS_ONE.byteValue(), -1);
        assertEquals(0.0d, NumberUtils.DOUBLE_ZERO.doubleValue());
        assertEquals(1.0d, NumberUtils.DOUBLE_ONE.doubleValue());
        assertEquals(NumberUtils.DOUBLE_MINUS_ONE.doubleValue(), -1.0d);
        assertEquals(0.0f, NumberUtils.FLOAT_ZERO.floatValue());
        assertEquals(1.0f, NumberUtils.FLOAT_ONE.floatValue());
        assertEquals(NumberUtils.FLOAT_MINUS_ONE.floatValue(), -1.0f);
    }

    @Test
    public void testConstructor() {
        assertNotNull(new NumberUtils());
        final Constructor<?>[] cons = NumberUtils.class.getDeclaredConstructors();
        assertEquals(1, cons.length);
        assertTrue(Modifier.isPublic(cons[0].getModifiers()));
        assertTrue(Modifier.isPublic(NumberUtils.class.getModifiers()));
        assertFalse(Modifier.isFinal(NumberUtils.class.getModifiers()));
    }

    @Test
    public void testMaximumByte() {
        final byte low = 123;
        final byte mid = 123 + 1;
        final byte high = 123 + 2;
        assertEquals(high, NumberUtils.max(low, mid, high), "maximum(byte, byte, byte) 1 failed");
        assertEquals(high, NumberUtils.max(mid, low, high), "maximum(byte, byte, byte) 2 failed");
        assertEquals(high, NumberUtils.max(mid, high, low), "maximum(byte, byte, byte) 3 failed");
        assertEquals(high, NumberUtils.max(high, mid, high), "maximum(byte, byte, byte) 4 failed");
    }

    @Test
    public void testMaximumDouble() {
        final double low = 12.3;
        final double mid = 12.3 + 1;
        final double high = 12.3 + 2;
        assertEquals(high, NumberUtils.max(low, mid, high), 0.0001);
        assertEquals(high, NumberUtils.max(mid, low, high), 0.0001);
        assertEquals(high, NumberUtils.max(mid, high, low), 0.0001);
        assertEquals(mid, NumberUtils.max(low, mid, low), 0.0001);
        assertEquals(high, NumberUtils.max(high, mid, high), 0.0001);
    }

    @Test
    public void testMaximumFloat() {
        final float low = 12.3f;
        final float mid = 12.3f + 1;
        final float high = 12.3f + 2;
        assertEquals(high, NumberUtils.max(low, mid, high), 0.0001f);
        assertEquals(high, NumberUtils.max(mid, low, high), 0.0001f);
        assertEquals(high, NumberUtils.max(mid, high, low), 0.0001f);
        assertEquals(mid, NumberUtils.max(low, mid, low), 0.0001f);
        assertEquals(high, NumberUtils.max(high, mid, high), 0.0001f);
    }

    @Test
    public void testMaximumInt() {
        assertEquals(12345, NumberUtils.max(12345, 12345 - 1, 12345 - 2), "maximum(int, int, int) 1 failed");
        assertEquals(12345, NumberUtils.max(12345 - 1, 12345, 12345 - 2), "maximum(int, int, int) 2 failed");
        assertEquals(12345, NumberUtils.max(12345 - 1, 12345 - 2, 12345), "maximum(int, int, int) 3 failed");
        assertEquals(12345, NumberUtils.max(12345 - 1, 12345, 12345), "maximum(int, int, int) 4 failed");
        assertEquals(12345, NumberUtils.max(12345, 12345, 12345), "maximum(int, int, int) 5 failed");
    }

    @Test
    public void testMaximumLong() {
        assertEquals(12345L, NumberUtils.max(12345L, 12345L - 1L, 12345L - 2L), "maximum(long, long, long) 1 failed");
        assertEquals(12345L, NumberUtils.max(12345L - 1L, 12345L, 12345L - 2L), "maximum(long, long, long) 2 failed");
        assertEquals(12345L, NumberUtils.max(12345L - 1L, 12345L - 2L, 12345L), "maximum(long, long, long) 3 failed");
        assertEquals(12345L, NumberUtils.max(12345L - 1L, 12345L, 12345L), "maximum(long, long, long) 4 failed");
        assertEquals(12345L, NumberUtils.max(12345L, 12345L, 12345L), "maximum(long, long, long) 5 failed");
    }

    @Test
    public void testMaximumShort() {
        final short low = 1234;
        final short mid = 1234 + 1;
        final short high = 1234 + 2;
        assertEquals(high, NumberUtils.max(low, mid, high), "maximum(short, short, short) 1 failed");
        assertEquals(high, NumberUtils.max(mid, low, high), "maximum(short, short, short) 2 failed");
        assertEquals(high, NumberUtils.max(mid, high, low), "maximum(short, short, short) 3 failed");
        assertEquals(high, NumberUtils.max(high, mid, high), "maximum(short, short, short) 4 failed");
    }

    @Test
    public void testMinimumByte() {
        final byte low = 123;
        final byte mid = 123 + 1;
        final byte high = 123 + 2;
        assertEquals(low, NumberUtils.min(low, mid, high), "minimum(byte, byte, byte) 1 failed");
        assertEquals(low, NumberUtils.min(mid, low, high), "minimum(byte, byte, byte) 2 failed");
        assertEquals(low, NumberUtils.min(mid, high, low), "minimum(byte, byte, byte) 3 failed");
        assertEquals(low, NumberUtils.min(low, mid, low), "minimum(byte, byte, byte) 4 failed");
    }

    @Test
    public void testMinimumDouble() {
        final double low = 12.3;
        final double mid = 12.3 + 1;
        final double high = 12.3 + 2;
        assertEquals(low, NumberUtils.min(low, mid, high), 0.0001);
        assertEquals(low, NumberUtils.min(mid, low, high), 0.0001);
        assertEquals(low, NumberUtils.min(mid, high, low), 0.0001);
        assertEquals(low, NumberUtils.min(low, mid, low), 0.0001);
        assertEquals(mid, NumberUtils.min(high, mid, high), 0.0001);
    }

    @Test
    public void testMinimumFloat() {
        final float low = 12.3f;
        final float mid = 12.3f + 1;
        final float high = 12.3f + 2;
        assertEquals(low, NumberUtils.min(low, mid, high), 0.0001f);
        assertEquals(low, NumberUtils.min(mid, low, high), 0.0001f);
        assertEquals(low, NumberUtils.min(mid, high, low), 0.0001f);
        assertEquals(low, NumberUtils.min(low, mid, low), 0.0001f);
        assertEquals(mid, NumberUtils.min(high, mid, high), 0.0001f);
    }

    @Test
    public void testMinimumInt() {
        assertEquals(12345, NumberUtils.min(12345, 12345 + 1, 12345 + 2), "minimum(int, int, int) 1 failed");
        assertEquals(12345, NumberUtils.min(12345 + 1, 12345, 12345 + 2), "minimum(int, int, int) 2 failed");
        assertEquals(12345, NumberUtils.min(12345 + 1, 12345 + 2, 12345), "minimum(int, int, int) 3 failed");
        assertEquals(12345, NumberUtils.min(12345 + 1, 12345, 12345), "minimum(int, int, int) 4 failed");
        assertEquals(12345, NumberUtils.min(12345, 12345, 12345), "minimum(int, int, int) 5 failed");
    }

    @Test
    public void testMinimumLong() {
        assertEquals(12345L, NumberUtils.min(12345L, 12345L + 1L, 12345L + 2L), "minimum(long, long, long) 1 failed");
        assertEquals(12345L, NumberUtils.min(12345L + 1L, 12345L, 12345 + 2L), "minimum(long, long, long) 2 failed");
        assertEquals(12345L, NumberUtils.min(12345L + 1L, 12345L + 2L, 12345L), "minimum(long, long, long) 3 failed");
        assertEquals(12345L, NumberUtils.min(12345L + 1L, 12345L, 12345L), "minimum(long, long, long) 4 failed");
        assertEquals(12345L, NumberUtils.min(12345L, 12345L, 12345L), "minimum(long, long, long) 5 failed");
    }

    @Test
    public void testMinimumShort() {
        final short low = 1234;
        final short mid = 1234 + 1;
        final short high = 1234 + 2;
        assertEquals(low, NumberUtils.min(low, mid, high), "minimum(short, short, short) 1 failed");
        assertEquals(low, NumberUtils.min(mid, low, high), "minimum(short, short, short) 2 failed");
        assertEquals(low, NumberUtils.min(mid, high, low), "minimum(short, short, short) 3 failed");
        assertEquals(low, NumberUtils.min(low, mid, low), "minimum(short, short, short) 4 failed");
    }

}
