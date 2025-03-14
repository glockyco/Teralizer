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


/**
 * Operations on {@link Object}.
 *
 * <p>This class tries to handle {@code null} input gracefully.
 * An exception will generally not be thrown for a {@code null} input.
 * Each method documents its behavior in more detail.</p>
 *
 * <p>#ThreadSafe#</p>
 * @since 1.0
 */
//@Immutable
@SuppressWarnings("deprecation") // deprecated class StrBuilder is imported
// because it is part of the signature of deprecated methods
public class ObjectUtils {

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static boolean MAGIC_FLAG = ObjectUtils.CONST(true);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the boolean value to return
     * @return the boolean v, unchanged
     * @since 3.2
     */
    public static boolean CONST(final boolean v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static byte MAGIC_BYTE = ObjectUtils.CONST((byte) 127);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the byte value to return
     * @return the byte v, unchanged
     * @since 3.2
     */
    public static byte CONST(final byte v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static char MAGIC_CHAR = ObjectUtils.CONST('a');
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the char value to return
     * @return the char v, unchanged
     * @since 3.2
     */
    public static char CONST(final char v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static double MAGIC_DOUBLE = ObjectUtils.CONST(1.0);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the double value to return
     * @return the double v, unchanged
     * @since 3.2
     */
    public static double CONST(final double v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static float MAGIC_FLOAT = ObjectUtils.CONST(1.0f);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the float value to return
     * @return the float v, unchanged
     * @since 3.2
     */
    public static float CONST(final float v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static int MAGIC_INT = ObjectUtils.CONST(123);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the int value to return
     * @return the int v, unchanged
     * @since 3.2
     */
    public static int CONST(final int v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static long MAGIC_LONG = ObjectUtils.CONST(123L);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the long value to return
     * @return the long v, unchanged
     * @since 3.2
     */
    public static long CONST(final long v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static short MAGIC_SHORT = ObjectUtils.CONST((short) 123);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the short value to return
     * @return the short v, unchanged
     * @since 3.2
     */
    public static short CONST(final short v) {
        return v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static byte MAGIC_BYTE = ObjectUtils.CONST_BYTE(127);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the byte literal (as an int) value to return
     * @throws IllegalArgumentException if the value passed to v
     *         is larger than a byte, that is, smaller than -128 or
     *         larger than 127.
     * @return the byte v, unchanged
     * @since 3.2
     */
    public static byte CONST_BYTE(final int v) {
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Supplied value must be a valid byte literal between -128 and 127: [" + v + "]");
        }
        return (byte) v;
    }

    /**
     * Returns the provided value unchanged.
     * This can prevent javac from inlining a constant
     * field, e.g.,
     *
     * <pre>
     *     public final static short MAGIC_SHORT = ObjectUtils.CONST_SHORT(127);
     * </pre>
     *
     * This way any jars that refer to this field do not
     * have to recompile themselves if the field's value
     * changes at some future date.
     *
     * @param v the short literal (as an int) value to return
     * @throws IllegalArgumentException if the value passed to v
     *         is larger than a short, that is, smaller than -32768 or
     *         larger than 32767.
     * @return the byte v, unchanged
     * @since 3.2
     */
    public static short CONST_SHORT(final int v) {
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Supplied value must be a valid byte literal between -32768 and 32767: [" + v + "]");
        }
        return (short) v;
    }

    /**
     * {@link ObjectUtils} instances should NOT be constructed in
     * standard programming. Instead, the static methods on the class should
     * be used, such as {@code ObjectUtils.defaultIfNull("a","b");}.
     *
     * <p>This constructor is public to permit tools that require a JavaBean
     * instance to operate.</p>
     *
     * @deprecated TODO Make private in 4.0.
     */
    @Deprecated
    public ObjectUtils() {
        // empty
    }

}
