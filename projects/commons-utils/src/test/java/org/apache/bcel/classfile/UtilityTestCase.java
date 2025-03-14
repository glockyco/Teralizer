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

package org.apache.bcel.classfile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilityTestCase {

    @Test
    public void testClearBit() {
        assertEquals(0, Utility.clearBit(0, 0));
        assertEquals(0, Utility.clearBit(1, 0), "1 bit 0 set to 0 -> 0");
        assertEquals(1, Utility.clearBit(1, 1), "1 bit 1 is 0 hence no change");
        assertEquals(8, Utility.clearBit(8, 4), "1000 only has 4 bit hence no change");
        assertEquals(1, Utility.clearBit(9, 3), "1001 bit 3 set to 0 -> 0001");
        assertEquals(-2, Utility.clearBit(-1, 0), "111...11 set bit 0 to 0 -> 111..10");
        assertEquals(0, Utility.clearBit(Integer.MIN_VALUE, 31), "100...00 set bit 31 to 0 -> 000..00");
    }

    @Test
    public void testIsSet() {
        assertTrue(Utility.isSet(1, 0));
        assertTrue(Utility.isSet(7, 1));
        assertTrue(Utility.isSet(8, 3));
        assertTrue(Utility.isSet(9, 0));
        assertTrue(Utility.isSet(Integer.MIN_VALUE, 31));
        assertFalse(Utility.isSet(0, 0));
        assertFalse(Utility.isSet(8, 4));
        assertFalse(Utility.isSet(9, 1));
    }

    @Test
    public void testSetBit() {
        assertEquals(1, Utility.setBit(0, 0), "0 bit 0 set to 1 -> 1");
        assertEquals(1, Utility.setBit(1, 0), "1 bit 0 is 1 hence no change");
        assertEquals(3, Utility.setBit(1, 1), "1 bit 1 set to 1 -> 3");
        assertEquals(8, Utility.setBit(8, 3), "1000 bit 3 is 1 hence no change");
        assertEquals(9, Utility.setBit(1, 3), "0001 bit 3 set to 1 -> 1001");
        assertEquals(-1, Utility.setBit(-2, 0), "111...10 set bit 0 to 1 -> 111..11");
        assertEquals(Integer.MIN_VALUE, Utility.setBit(0, 31), "000...00 set bit 31 to 0 -> 100..00");
    }
}