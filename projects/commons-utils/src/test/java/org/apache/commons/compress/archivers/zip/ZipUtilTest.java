/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.compress.archivers.zip;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ZipUtilTest {

    static void assertDosDate(final long value, final int year, final int month, final int day, final int hour, final int minute, final int second) {
        int pos = 0;
        assertEquals(year - 1980, (int) (value << pos) >>> 32 - 7);
        assertEquals(month, (int) (value << (pos += 7)) >>> 32 - 4);
        assertEquals(day, (int) (value << (pos += 4)) >>> 32 - 5);
        assertEquals(hour, (int) (value << (pos += 5)) >>> 32 - 5);
        assertEquals(minute, (int) (value << (pos += 5)) >>> 32 - 6);
        assertEquals(second, (int) (value << pos + 6) >>> 32 - 5 << 1); // DOS dates only store even seconds
    }

    static Instant toLocalInstant(final String date) {
        return LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant();
    }

    private Date time;

    @Test
    public void testAdjustToLong() {
        assertEquals(Integer.MAX_VALUE, ZipUtil.adjustToLong(Integer.MAX_VALUE));
        assertEquals((long) Integer.MAX_VALUE + 1, ZipUtil.adjustToLong(Integer.MAX_VALUE + 1));
        assertEquals(2 * (long) Integer.MAX_VALUE, ZipUtil.adjustToLong(2 * Integer.MAX_VALUE));
    }

    @Test
    public void testBigToLong() {
        final BigInteger big1 = BigInteger.valueOf(1);
        final BigInteger big2 = BigInteger.valueOf(Long.MAX_VALUE);
        final BigInteger big3 = BigInteger.valueOf(Long.MIN_VALUE);

        assertEquals(1L, ZipUtil.toLong(big1));
        assertEquals(Long.MAX_VALUE, ZipUtil.toLong(big2));
        assertEquals(Long.MIN_VALUE, ZipUtil.toLong(big3));

        final BigInteger big4 = big2.add(big1);
        assertThrows(IllegalArgumentException.class, () -> ZipUtil.toLong(big4), "Should have thrown IllegalArgumentException");

        final BigInteger big5 = big3.subtract(big1);
        assertThrows(IllegalArgumentException.class, () -> ZipUtil.toLong(big5),
                "ZipUtil.bigToLong(BigInteger) should have thrown IllegalArgumentException");
    }

    @Test
    public void testLongToBig() {
        final long l0 = 0;
        final long l1 = 1;
        final long l2 = -1;
        final long l3 = Integer.MIN_VALUE;
        final long l4 = Long.MAX_VALUE;
        final long l5 = Long.MIN_VALUE;

        final BigInteger big0 = ZipUtil.longToBig(l0);
        final BigInteger big1 = ZipUtil.longToBig(l1);
        final BigInteger big2 = ZipUtil.longToBig(l2);
        final BigInteger big3 = ZipUtil.longToBig(l3);
        final BigInteger big4 = ZipUtil.longToBig(l4);

        assertEquals(0, big0.longValue());
        assertEquals(1, big1.longValue());
        assertEquals(0xFFFFFFFFL, big2.longValue());
        assertEquals(0x80000000L, big3.longValue());
        assertEquals(Long.MAX_VALUE, big4.longValue());

        assertThrows(IllegalArgumentException.class, () -> ZipUtil.longToBig(l5), "ZipUtil.longToBig(long) should have thrown IllegalArgumentException");
    }

    @Test
    public void testReverse() {
        final byte[][] bTest = new byte[6][];
        bTest[0] = new byte[] {};
        bTest[1] = new byte[] { 1 };
        bTest[2] = new byte[] { 1, 2 };
        bTest[3] = new byte[] { 1, 2, 3 };
        bTest[4] = new byte[] { 1, 2, 3, 4 };
        bTest[5] = new byte[] { 1, 2, 3, 4, 5 };

        final byte[][] rTest = new byte[6][];
        rTest[0] = new byte[] {};
        rTest[1] = new byte[] { 1 };
        rTest[2] = new byte[] { 2, 1 };
        rTest[3] = new byte[] { 3, 2, 1 };
        rTest[4] = new byte[] { 4, 3, 2, 1 };
        rTest[5] = new byte[] { 5, 4, 3, 2, 1 };

        assertEquals(bTest.length, rTest.length, "test and result arrays are same length");

        for (int i = 0; i < bTest.length; i++) {
            final byte[] result = ZipUtil.reverse(bTest[i]);
            assertSame(bTest[i], result, "reverse mutates in-place");
            assertArrayEquals(rTest[i], result, "reverse actually reverses");
        }
    }

    @Test
    public void testSignedByteToUnsignedInt() {
        // Yay, we can completely test all possible input values in this case!
        int expectedVal = 128;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            final byte b = (byte) i;
            assertEquals(expectedVal, Byte.toUnsignedInt(b));
            expectedVal++;
            if (expectedVal == 256) {
                expectedVal = 0;
            }
        }
    }

    @Test
    public void testUnsignedIntToSignedByte() {
        int unsignedVal = 128;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            final byte expectedVal = (byte) i;
            assertEquals(expectedVal, ZipUtil.unsignedIntToSignedByte(unsignedVal));
            unsignedVal++;
            if (unsignedVal == 256) {
                unsignedVal = 0;
            }
        }

        assertThrows(IllegalArgumentException.class, () -> ZipUtil.unsignedIntToSignedByte(-1),
                "ZipUtil.unsignedIntToSignedByte(-1) should have thrown IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> ZipUtil.unsignedIntToSignedByte(256),
                "ZipUtil.unsignedIntToSignedByte(256) should have thrown IllegalArgumentException");
    }
}
