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
package org.apache.commons.lang3;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link BooleanUtils}.
 */
public class BooleanUtilsTest {
    @Test
    public void test_isFalse_Boolean() {
        assertFalse(BooleanUtils.isFalse(Boolean.TRUE));
        assertTrue(BooleanUtils.isFalse(Boolean.FALSE));
        assertFalse(BooleanUtils.isFalse(null));
    }

    @Test
    public void test_isNotFalse_Boolean() {
        assertTrue(BooleanUtils.isNotFalse(Boolean.TRUE));
        assertFalse(BooleanUtils.isNotFalse(Boolean.FALSE));
        assertTrue(BooleanUtils.isNotFalse(null));
    }

    @Test
    public void test_isNotTrue_Boolean() {
        assertFalse(BooleanUtils.isNotTrue(Boolean.TRUE));
        assertTrue(BooleanUtils.isNotTrue(Boolean.FALSE));
        assertTrue(BooleanUtils.isNotTrue(null));
    }

    @Test
    public void test_isTrue_Boolean() {
        assertTrue(BooleanUtils.isTrue(Boolean.TRUE));
        assertFalse(BooleanUtils.isTrue(Boolean.FALSE));
        assertFalse(BooleanUtils.isTrue(null));
    }

    @Test
    public void test_negate_Boolean() {
        assertSame(null, BooleanUtils.negate(null));
        assertSame(Boolean.TRUE, BooleanUtils.negate(Boolean.FALSE));
        assertSame(Boolean.FALSE, BooleanUtils.negate(Boolean.TRUE));
    }

    @Test
    public void test_toBoolean_Boolean() {
        assertTrue(BooleanUtils.toBoolean(Boolean.TRUE));
        assertFalse(BooleanUtils.toBoolean(Boolean.FALSE));
        assertFalse(BooleanUtils.toBoolean((Boolean) null));
    }

    @Test
    public void test_toBoolean_int() {
        assertTrue(BooleanUtils.toBoolean(1));
        assertTrue(BooleanUtils.toBoolean(-1));
        assertFalse(BooleanUtils.toBoolean(0));
    }

    @Test
    public void test_toBoolean_int_int_int() {
        assertTrue(BooleanUtils.toBoolean(6, 6, 7));
        assertFalse(BooleanUtils.toBoolean(7, 6, 7));
    }

    @Test
    public void test_toBoolean_int_int_int_noMatch() {
        assertThrows(IllegalArgumentException.class, () -> BooleanUtils.toBoolean(8, 6, 7));
    }

    @Test
    public void test_toBoolean_Integer_Integer_Integer() {
        final Integer six = Integer.valueOf(6);
        final Integer seven = Integer.valueOf(7);

        assertTrue(BooleanUtils.toBoolean(null, null, seven));
        assertFalse(BooleanUtils.toBoolean(null, six, null));

        assertTrue(BooleanUtils.toBoolean(Integer.valueOf(6), six, seven));
        assertFalse(BooleanUtils.toBoolean(Integer.valueOf(7), six, seven));
    }

    @Test
    public void test_toBoolean_Integer_Integer_Integer_noMatch() {
        assertThrows(IllegalArgumentException.class,
                () -> BooleanUtils.toBoolean(Integer.valueOf(8), Integer.valueOf(6), Integer.valueOf(7)));
    }

    @Test
    public void test_toBoolean_Integer_Integer_Integer_nullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BooleanUtils.toBoolean(null, Integer.valueOf(6), Integer.valueOf(7)));
    }

    @Test
    public void test_toBoolean_String_String_String() {
        assertTrue(BooleanUtils.toBoolean(null, null, "N"));
        assertFalse(BooleanUtils.toBoolean(null, "Y", null));
        assertTrue(BooleanUtils.toBoolean("Y", "Y", "N"));
        assertTrue(BooleanUtils.toBoolean("Y", "Y", "N"));
        assertFalse(BooleanUtils.toBoolean("N", "Y", "N"));
        assertFalse(BooleanUtils.toBoolean("N", "Y", "N"));
        assertTrue(BooleanUtils.toBoolean((String) null, null, null));
        assertTrue(BooleanUtils.toBoolean("Y", "Y", "Y"));
        assertTrue(BooleanUtils.toBoolean("Y", "Y", "Y"));
    }

    @Test
    public void test_toBoolean_String_String_String_noMatch() {
        assertThrows(IllegalArgumentException.class, () -> BooleanUtils.toBoolean("X", "Y", "N"));
    }

    @Test
    public void test_toBoolean_String_String_String_nullValue() {
        assertThrows(IllegalArgumentException.class, () -> BooleanUtils.toBoolean(null, "Y", "N"));
    }

    @Test
    public void test_toBooleanDefaultIfNull_Boolean_boolean() {
        assertTrue(BooleanUtils.toBooleanDefaultIfNull(Boolean.TRUE, true));
        assertTrue(BooleanUtils.toBooleanDefaultIfNull(Boolean.TRUE, false));
        assertFalse(BooleanUtils.toBooleanDefaultIfNull(Boolean.FALSE, true));
        assertFalse(BooleanUtils.toBooleanDefaultIfNull(Boolean.FALSE, false));
        assertTrue(BooleanUtils.toBooleanDefaultIfNull(null, true));
        assertFalse(BooleanUtils.toBooleanDefaultIfNull(null, false));
    }

    @Test
    public void test_toBooleanObject_int() {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(1));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(-1));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(0));
    }

    @Test
    public void test_toBooleanObject_int_int_int() {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(6, 6, 7, 8));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(7, 6, 7, 8));
        assertNull(BooleanUtils.toBooleanObject(8, 6, 7, 8));
    }

    @Test
    public void test_toBooleanObject_int_int_int_noMatch() {
        assertThrows(IllegalArgumentException.class, () -> BooleanUtils.toBooleanObject(9, 6, 7, 8));
    }

    @Test
    public void test_toBooleanObject_Integer() {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(Integer.valueOf(1)));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(Integer.valueOf(-1)));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(Integer.valueOf(0)));
        assertNull(BooleanUtils.toBooleanObject((Integer) null));
    }

    @Test
    public void test_toBooleanObject_Integer_Integer_Integer_Integer() {
        final Integer six = Integer.valueOf(6);
        final Integer seven = Integer.valueOf(7);
        final Integer eight = Integer.valueOf(8);

        assertSame(Boolean.TRUE, BooleanUtils.toBooleanObject(null, null, seven, eight));
        assertSame(Boolean.FALSE, BooleanUtils.toBooleanObject(null, six, null, eight));
        assertSame(null, BooleanUtils.toBooleanObject(null, six, seven, null));

        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(Integer.valueOf(6), six, seven, eight));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(Integer.valueOf(7), six, seven, eight));
        assertNull(BooleanUtils.toBooleanObject(Integer.valueOf(8), six, seven, eight));
    }

    @Test
    public void test_toBooleanObject_Integer_Integer_Integer_Integer_noMatch() {
        assertThrows(IllegalArgumentException.class,
                () -> BooleanUtils.toBooleanObject(Integer.valueOf(9), Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8)));
    }

    @Test
    public void test_toBooleanObject_Integer_Integer_Integer_Integer_nullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BooleanUtils.toBooleanObject(null, Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8)));
    }

    @Test
    public void test_toInteger_boolean() {
        assertEquals(1, BooleanUtils.toInteger(true));
        assertEquals(0, BooleanUtils.toInteger(false));
    }

    @Test
    public void test_toInteger_boolean_int_int() {
        assertEquals(6, BooleanUtils.toInteger(true, 6, 7));
        assertEquals(7, BooleanUtils.toInteger(false, 6, 7));
    }

    @Test
    public void test_toInteger_Boolean_int_int_int() {
        assertEquals(6, BooleanUtils.toInteger(Boolean.TRUE, 6, 7, 8));
        assertEquals(7, BooleanUtils.toInteger(Boolean.FALSE, 6, 7, 8));
        assertEquals(8, BooleanUtils.toInteger(null, 6, 7, 8));
    }

    @Test
    public void test_toIntegerObject_boolean() {
        assertEquals(Integer.valueOf(1), BooleanUtils.toIntegerObject(true));
        assertEquals(Integer.valueOf(0), BooleanUtils.toIntegerObject(false));
    }

    @Test
    public void test_toIntegerObject_Boolean() {
        assertEquals(Integer.valueOf(1), BooleanUtils.toIntegerObject(Boolean.TRUE));
        assertEquals(Integer.valueOf(0), BooleanUtils.toIntegerObject(Boolean.FALSE));
        assertNull(BooleanUtils.toIntegerObject(null));
    }

    @Test
    public void test_toIntegerObject_boolean_Integer_Integer() {
        final Integer six = Integer.valueOf(6);
        final Integer seven = Integer.valueOf(7);
        assertEquals(six, BooleanUtils.toIntegerObject(true, six, seven));
        assertEquals(seven, BooleanUtils.toIntegerObject(false, six, seven));
    }

    @Test
    public void test_toIntegerObject_Boolean_Integer_Integer_Integer() {
        final Integer six = Integer.valueOf(6);
        final Integer seven = Integer.valueOf(7);
        final Integer eight = Integer.valueOf(8);
        assertEquals(six, BooleanUtils.toIntegerObject(Boolean.TRUE, six, seven, eight));
        assertEquals(seven, BooleanUtils.toIntegerObject(Boolean.FALSE, six, seven, eight));
        assertEquals(eight, BooleanUtils.toIntegerObject(null, six, seven, eight));
        assertNull(BooleanUtils.toIntegerObject(null, six, seven, null));
    }

    @Test
    public void testCompare() {
        assertTrue(BooleanUtils.compare(true, false) > 0);
        assertEquals(0, BooleanUtils.compare(true, true));
        assertEquals(0, BooleanUtils.compare(false, false));
        assertTrue(BooleanUtils.compare(false, true) < 0);
    }

    @Test
    public void testConstructor() {
        assertNotNull(new BooleanUtils());
        final Constructor<?>[] cons = BooleanUtils.class.getDeclaredConstructors();
        assertEquals(1, cons.length);
        assertTrue(Modifier.isPublic(cons[0].getModifiers()));
        assertTrue(Modifier.isPublic(BooleanUtils.class.getModifiers()));
        assertFalse(Modifier.isFinal(BooleanUtils.class.getModifiers()));
    }
}
