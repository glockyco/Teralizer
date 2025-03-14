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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.math.NumberUtils;

/**
 * Operations on boolean primitives and Boolean objects.
 *
 * <p>This class tries to handle {@code null} input gracefully.
 * An exception will not be thrown for a {@code null} input.
 * Each method documents its behavior in more detail.</p>
 *
 * <p>#ThreadSafe#</p>
 * @since 2.0
 */
public class BooleanUtils {

    private static final List<Boolean> BOOLEAN_LIST = Collections.unmodifiableList(Arrays.asList(Boolean.FALSE, Boolean.TRUE));

    /**
     * The false String {@code "false"}.
     *
     * @since 3.12.0
     */
    public static final String FALSE = "false";

    /**
     * The no String {@code "no"}.
     *
     * @since 3.12.0
     */
    public static final String NO = "no";

    /**
     * The off String {@code "off"}.
     *
     * @since 3.12.0
     */
    public static final String OFF = "off";

    /**
     * The on String {@code "on"}.
     *
     * @since 3.12.0
     */
    public static final String ON = "on";

    /**
     * The true String {@code "true"}.
     *
     * @since 3.12.0
     */
    public static final String TRUE = "true";

    /**
     * The yes String {@code "yes"}.
     *
     * @since 3.12.0
     */
    public static final String YES = "yes";

    /**
     * Compares two {@code boolean} values. This is the same functionality as provided in Java 7.
     *
     * @param x the first {@code boolean} to compare
     * @param y the second {@code boolean} to compare
     * @return the value {@code 0} if {@code x == y};
     *         a value less than {@code 0} if {@code !x && y}; and
     *         a value greater than {@code 0} if {@code x && !y}
     * @since 3.4
     */
    public static int compare(final boolean x, final boolean y) {
        if (x == y) {
            return 0;
        }
        return x ? 1 : -1;
    }

    /**
     * Checks if a {@link Boolean} value is {@code false},
     * handling {@code null} by returning {@code false}.
     *
     * <pre>
     *   BooleanUtils.isFalse(Boolean.TRUE)  = false
     *   BooleanUtils.isFalse(Boolean.FALSE) = true
     *   BooleanUtils.isFalse(null)          = false
     * </pre>
     *
     * @param bool  the boolean to check, null returns {@code false}
     * @return {@code true} only if the input is non-{@code null} and {@code false}
     * @since 2.1
     */
    public static boolean isFalse(final Boolean bool) {
        return Boolean.FALSE.equals(bool);
    }

    /**
     * Checks if a {@link Boolean} value is <em>not</em> {@code false},
     * handling {@code null} by returning {@code true}.
     *
     * <pre>
     *   BooleanUtils.isNotFalse(Boolean.TRUE)  = true
     *   BooleanUtils.isNotFalse(Boolean.FALSE) = false
     *   BooleanUtils.isNotFalse(null)          = true
     * </pre>
     *
     * @param bool  the boolean to check, null returns {@code true}
     * @return {@code true} if the input is {@code null} or {@code true}
     * @since 2.3
     */
    public static boolean isNotFalse(final Boolean bool) {
        return !isFalse(bool);
    }

    /**
     * Checks if a {@link Boolean} value is <em>not</em> {@code true},
     * handling {@code null} by returning {@code true}.
     *
     * <pre>
     *   BooleanUtils.isNotTrue(Boolean.TRUE)  = false
     *   BooleanUtils.isNotTrue(Boolean.FALSE) = true
     *   BooleanUtils.isNotTrue(null)          = true
     * </pre>
     *
     * @param bool  the boolean to check, null returns {@code true}
     * @return {@code true} if the input is null or false
     * @since 2.3
     */
    public static boolean isNotTrue(final Boolean bool) {
        return !isTrue(bool);
    }

    /**
     * Checks if a {@link Boolean} value is {@code true},
     * handling {@code null} by returning {@code false}.
     *
     * <pre>
     *   BooleanUtils.isTrue(Boolean.TRUE)  = true
     *   BooleanUtils.isTrue(Boolean.FALSE) = false
     *   BooleanUtils.isTrue(null)          = false
     * </pre>
     *
     * @param bool the boolean to check, {@code null} returns {@code false}
     * @return {@code true} only if the input is non-null and true
     * @since 2.1
     */
    public static boolean isTrue(final Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }
    /**
     * Negates the specified boolean.
     *
     * <p>If {@code null} is passed in, {@code null} will be returned.</p>
     *
     * <p>NOTE: This returns {@code null} and will throw a {@link NullPointerException}
     * if unboxed to a boolean.</p>
     *
     * <pre>
     *   BooleanUtils.negate(Boolean.TRUE)  = Boolean.FALSE;
     *   BooleanUtils.negate(Boolean.FALSE) = Boolean.TRUE;
     *   BooleanUtils.negate(null)          = null;
     * </pre>
     *
     * @param bool  the Boolean to negate, may be null
     * @return the negated Boolean, or {@code null} if {@code null} input
     */
    public static Boolean negate(final Boolean bool) {
        if (bool == null) {
            return null;
        }
        return bool.booleanValue() ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * Converts a Boolean to a boolean handling {@code null}
     * by returning {@code false}.
     *
     * <pre>
     *   BooleanUtils.toBoolean(Boolean.TRUE)  = true
     *   BooleanUtils.toBoolean(Boolean.FALSE) = false
     *   BooleanUtils.toBoolean(null)          = false
     * </pre>
     *
     * @param bool  the boolean to convert
     * @return {@code true} or {@code false}, {@code null} returns {@code false}
     */
    public static boolean toBoolean(final Boolean bool) {
        return bool != null && bool.booleanValue();
    }

    /**
     * Converts an int to a boolean using the convention that {@code zero}
     * is {@code false}, everything else is {@code true}.
     *
     * <pre>
     *   BooleanUtils.toBoolean(0) = false
     *   BooleanUtils.toBoolean(1) = true
     *   BooleanUtils.toBoolean(2) = true
     * </pre>
     *
     * @param value  the int to convert
     * @return {@code true} if non-zero, {@code false}
     *  if zero
     */
    public static boolean toBoolean(final int value) {
        return value != 0;
    }

    /**
     * Converts an int to a boolean specifying the conversion values.
     *
     * <p>If the {@code trueValue} and {@code falseValue} are the same number then
     * the return value will be {@code true} in case {@code value} matches it.</p>
     *
     * <pre>
     *   BooleanUtils.toBoolean(0, 1, 0) = false
     *   BooleanUtils.toBoolean(1, 1, 0) = true
     *   BooleanUtils.toBoolean(1, 1, 1) = true
     *   BooleanUtils.toBoolean(2, 1, 2) = false
     *   BooleanUtils.toBoolean(2, 2, 0) = true
     * </pre>
     *
     * @param value  the {@link Integer} to convert
     * @param trueValue  the value to match for {@code true}
     * @param falseValue  the value to match for {@code false}
     * @return {@code true} or {@code false}
     * @throws IllegalArgumentException if {@code value} does not match neither
     * {@code trueValue} no {@code falseValue}
     */
    public static boolean toBoolean(final int value, final int trueValue, final int falseValue) {
        if (value == trueValue) {
            return true;
        }
        if (value == falseValue) {
            return false;
        }
        throw new IllegalArgumentException("The Integer did not match either specified value");
    }

    /**
     * Converts an Integer to a boolean specifying the conversion values.
     *
     * <pre>
     *   BooleanUtils.toBoolean(Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(0)) = false
     *   BooleanUtils.toBoolean(Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(0)) = true
     *   BooleanUtils.toBoolean(Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(2)) = false
     *   BooleanUtils.toBoolean(Integer.valueOf(2), Integer.valueOf(2), Integer.valueOf(0)) = true
     *   BooleanUtils.toBoolean(null, null, Integer.valueOf(0))                     = true
     * </pre>
     *
     * @param value  the Integer to convert
     * @param trueValue  the value to match for {@code true}, may be {@code null}
     * @param falseValue  the value to match for {@code false}, may be {@code null}
     * @return {@code true} or {@code false}
     * @throws IllegalArgumentException if no match
     */
    public static boolean toBoolean(final Integer value, final Integer trueValue, final Integer falseValue) {
        if (value == null) {
            if (trueValue == null) {
                return true;
            }
            if (falseValue == null) {
                return false;
            }
        } else if (value.equals(trueValue)) {
            return true;
        } else if (value.equals(falseValue)) {
            return false;
        }
        throw new IllegalArgumentException("The Integer did not match either specified value");
    }

    /**
     * Converts a String to a Boolean throwing an exception if no match found.
     *
     * <pre>
     *   BooleanUtils.toBoolean("true", "true", "false")  = true
     *   BooleanUtils.toBoolean("false", "true", "false") = false
     * </pre>
     *
     * @param str  the String to check
     * @param trueString  the String to match for {@code true} (case-sensitive), may be {@code null}
     * @param falseString  the String to match for {@code false} (case-sensitive), may be {@code null}
     * @return the boolean value of the string
     * @throws IllegalArgumentException if the String doesn't match
     */
    public static boolean toBoolean(final String str, final String trueString, final String falseString) {
        if (str == trueString) {
            return true;
        }
        if (str == falseString) {
            return false;
        }
        if (str != null) {
            if (str.equals(trueString)) {
                return true;
            }
            if (str.equals(falseString)) {
                return false;
            }
        }
        throw new IllegalArgumentException("The String did not match either specified value");
    }

    /**
     * Converts a Boolean to a boolean handling {@code null}.
     *
     * <pre>
     *   BooleanUtils.toBooleanDefaultIfNull(Boolean.TRUE, false)  = true
     *   BooleanUtils.toBooleanDefaultIfNull(Boolean.TRUE, true)   = true
     *   BooleanUtils.toBooleanDefaultIfNull(Boolean.FALSE, true)  = false
     *   BooleanUtils.toBooleanDefaultIfNull(Boolean.FALSE, false) = false
     *   BooleanUtils.toBooleanDefaultIfNull(null, true)           = true
     *   BooleanUtils.toBooleanDefaultIfNull(null, false)          = false
     * </pre>
     *
     * @param bool  the boolean object to convert to primitive
     * @param valueIfNull  the boolean value to return if the parameter {@code bool} is {@code null}
     * @return {@code true} or {@code false}
     */
    public static boolean toBooleanDefaultIfNull(final Boolean bool, final boolean valueIfNull) {
        if (bool == null) {
            return valueIfNull;
        }
        return bool.booleanValue();
    }

    /**
     * Converts an int to a Boolean using the convention that {@code zero}
     * is {@code false}, everything else is {@code true}.
     *
     * <pre>
     *   BooleanUtils.toBoolean(0) = Boolean.FALSE
     *   BooleanUtils.toBoolean(1) = Boolean.TRUE
     *   BooleanUtils.toBoolean(2) = Boolean.TRUE
     * </pre>
     *
     * @param value  the int to convert
     * @return Boolean.TRUE if non-zero, Boolean.FALSE if zero,
     *  {@code null} if {@code null}
     */
    public static Boolean toBooleanObject(final int value) {
        return value == 0 ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * Converts an int to a Boolean specifying the conversion values.
     *
     * <p>NOTE: This method may return {@code null} and may throw a {@link NullPointerException}
     * if unboxed to a {@code boolean}.</p>
     *
     * <p>The checks are done first for the {@code trueValue}, then for the {@code falseValue} and
     * finally for the {@code nullValue}.</p>
     *
     * <pre>
     *   BooleanUtils.toBooleanObject(0, 0, 2, 3) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(0, 0, 0, 3) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(0, 0, 0, 0) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(2, 1, 2, 3) = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(2, 1, 2, 2) = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(3, 1, 2, 3) = null
     * </pre>
     *
     * @param value  the Integer to convert
     * @param trueValue  the value to match for {@code true}
     * @param falseValue  the value to match for {@code false}
     * @param nullValue  the value to match for {@code null}
     * @return Boolean.TRUE, Boolean.FALSE, or {@code null}
     * @throws IllegalArgumentException if no match
     */
    public static Boolean toBooleanObject(final int value, final int trueValue, final int falseValue, final int nullValue) {
        if (value == trueValue) {
            return Boolean.TRUE;
        }
        if (value == falseValue) {
            return Boolean.FALSE;
        }
        if (value == nullValue) {
            return null;
        }
        throw new IllegalArgumentException("The Integer did not match any specified value");
    }

    /**
     * Converts an Integer to a Boolean using the convention that {@code zero}
     * is {@code false}, every other numeric value is {@code true}.
     *
     * <p>{@code null} will be converted to {@code null}.</p>
     *
     * <p>NOTE: This method may return {@code null} and may throw a {@link NullPointerException}
     * if unboxed to a {@code boolean}.</p>
     *
     * <pre>
     *   BooleanUtils.toBooleanObject(Integer.valueOf(0))    = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(1))    = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(null)) = null
     * </pre>
     *
     * @param value  the Integer to convert
     * @return Boolean.TRUE if non-zero, Boolean.FALSE if zero,
     *  {@code null} if {@code null} input
     */
    public static Boolean toBooleanObject(final Integer value) {
        if (value == null) {
            return null;
        }
        return value.intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * Converts an Integer to a Boolean specifying the conversion values.
     *
     * <p>NOTE: This method may return {@code null} and may throw a {@link NullPointerException}
     * if unboxed to a {@code boolean}.</p>
     *
     * <p>The checks are done first for the {@code trueValue}, then for the {@code falseValue} and
     * finally for the {@code nullValue}.</p>
     **
     * <pre>
     *   BooleanUtils.toBooleanObject(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(2), Integer.valueOf(3)) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(3)) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0)) = Boolean.TRUE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)) = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(2)) = Boolean.FALSE
     *   BooleanUtils.toBooleanObject(Integer.valueOf(3), Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)) = null
     * </pre>
     *
     * @param value  the Integer to convert
     * @param trueValue  the value to match for {@code true}, may be {@code null}
     * @param falseValue  the value to match for {@code false}, may be {@code null}
     * @param nullValue  the value to match for {@code null}, may be {@code null}
     * @return Boolean.TRUE, Boolean.FALSE, or {@code null}
     * @throws IllegalArgumentException if no match
     */
    public static Boolean toBooleanObject(final Integer value, final Integer trueValue, final Integer falseValue, final Integer nullValue) {
        if (value == null) {
            if (trueValue == null) {
                return Boolean.TRUE;
            }
            if (falseValue == null) {
                return Boolean.FALSE;
            }
            if (nullValue == null) {
                return null;
            }
        } else if (value.equals(trueValue)) {
            return Boolean.TRUE;
        } else if (value.equals(falseValue)) {
            return Boolean.FALSE;
        } else if (value.equals(nullValue)) {
            return null;
        }
        throw new IllegalArgumentException("The Integer did not match any specified value");
    }

    /**
     * Converts a boolean to an int using the convention that
     * {@code true} is {@code 1} and {@code false} is {@code 0}.
     *
     * <pre>
     *   BooleanUtils.toInteger(true)  = 1
     *   BooleanUtils.toInteger(false) = 0
     * </pre>
     *
     * @param bool  the boolean to convert
     * @return one if {@code true}, zero if {@code false}
     */
    public static int toInteger(final boolean bool) {
        return bool ? 1 : 0;
    }

    /**
     * Converts a boolean to an int specifying the conversion values.
     *
     * <pre>
     *   BooleanUtils.toInteger(true, 1, 0)  = 1
     *   BooleanUtils.toInteger(false, 1, 0) = 0
     * </pre>
     *
     * @param bool  the to convert
     * @param trueValue  the value to return if {@code true}
     * @param falseValue  the value to return if {@code false}
     * @return the appropriate value
     */
    public static int toInteger(final boolean bool, final int trueValue, final int falseValue) {
        return bool ? trueValue : falseValue;
    }

    /**
     * Converts a Boolean to an int specifying the conversion values.
     *
     * <pre>
     *   BooleanUtils.toInteger(Boolean.TRUE, 1, 0, 2)  = 1
     *   BooleanUtils.toInteger(Boolean.FALSE, 1, 0, 2) = 0
     *   BooleanUtils.toInteger(null, 1, 0, 2)          = 2
     * </pre>
     *
     * @param bool  the Boolean to convert
     * @param trueValue  the value to return if {@code true}
     * @param falseValue  the value to return if {@code false}
     * @param nullValue  the value to return if {@code null}
     * @return the appropriate value
     */
    public static int toInteger(final Boolean bool, final int trueValue, final int falseValue, final int nullValue) {
        if (bool == null) {
            return nullValue;
        }
        return bool.booleanValue() ? trueValue : falseValue;
    }

    /**
     * Converts a boolean to an Integer using the convention that
     * {@code true} is {@code 1} and {@code false} is {@code 0}.
     *
     * <pre>
     *   BooleanUtils.toIntegerObject(true)  = Integer.valueOf(1)
     *   BooleanUtils.toIntegerObject(false) = Integer.valueOf(0)
     * </pre>
     *
     * @param bool  the boolean to convert
     * @return one if {@code true}, zero if {@code false}
     */
    public static Integer toIntegerObject(final boolean bool) {
        return bool ? NumberUtils.INTEGER_ONE : NumberUtils.INTEGER_ZERO;
    }

    /**
     * Converts a boolean to an Integer specifying the conversion values.
     *
     * <pre>
     *   BooleanUtils.toIntegerObject(true, Integer.valueOf(1), Integer.valueOf(0))  = Integer.valueOf(1)
     *   BooleanUtils.toIntegerObject(false, Integer.valueOf(1), Integer.valueOf(0)) = Integer.valueOf(0)
     * </pre>
     *
     * @param bool  the to convert
     * @param trueValue  the value to return if {@code true}, may be {@code null}
     * @param falseValue  the value to return if {@code false}, may be {@code null}
     * @return the appropriate value
     */
    public static Integer toIntegerObject(final boolean bool, final Integer trueValue, final Integer falseValue) {
        return bool ? trueValue : falseValue;
    }

    /**
     * Converts a Boolean to an Integer using the convention that
     * {@code zero} is {@code false}.
     *
     * <p>{@code null} will be converted to {@code null}.</p>
     *
     * <pre>
     *   BooleanUtils.toIntegerObject(Boolean.TRUE)  = Integer.valueOf(1)
     *   BooleanUtils.toIntegerObject(Boolean.FALSE) = Integer.valueOf(0)
     * </pre>
     *
     * @param bool  the Boolean to convert
     * @return one if Boolean.TRUE, zero if Boolean.FALSE, {@code null} if {@code null}
     */
    public static Integer toIntegerObject(final Boolean bool) {
        if (bool == null) {
            return null;
        }
        return bool.booleanValue() ? NumberUtils.INTEGER_ONE : NumberUtils.INTEGER_ZERO;
    }

    /**
     * Converts a Boolean to an Integer specifying the conversion values.
     *
     * <pre>
     *   BooleanUtils.toIntegerObject(Boolean.TRUE, Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(2))  = Integer.valueOf(1)
     *   BooleanUtils.toIntegerObject(Boolean.FALSE, Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(2)) = Integer.valueOf(0)
     *   BooleanUtils.toIntegerObject(null, Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(2))          = Integer.valueOf(2)
     * </pre>
     *
     * @param bool  the Boolean to convert
     * @param trueValue  the value to return if {@code true}, may be {@code null}
     * @param falseValue  the value to return if {@code false}, may be {@code null}
     * @param nullValue  the value to return if {@code null}, may be {@code null}
     * @return the appropriate value
     */
    public static Integer toIntegerObject(final Boolean bool, final Integer trueValue, final Integer falseValue, final Integer nullValue) {
        if (bool == null) {
            return nullValue;
        }
        return bool.booleanValue() ? trueValue : falseValue;
    }


    /**
     * {@link BooleanUtils} instances should NOT be constructed in standard programming.
     * Instead, the class should be used as {@code BooleanUtils.negate(true);}.
     *
     * <p>This constructor is public to permit tools that require a JavaBean instance
     * to operate.</p>
     *
     * @deprecated TODO Make private in 4.0.
     */
    @Deprecated
    public BooleanUtils() {
        // empty
    }

}
