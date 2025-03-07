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
package org.apache.commons.validator;

import java.io.Serializable;

/**
 * This class contains basic methods for performing validations.
 */
public class GenericValidator implements Serializable {

    private static final long serialVersionUID = -7212095066891517618L;

    /**
    * <p>Checks if a value is within a range (min &amp; max specified
    * in the vars attribute).</p>
    *
    * @param value The value validation is being performed on.
    * @param min The minimum value of the range.
    * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
    */
    public static boolean isInRange(final byte value, final byte min, final byte max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if a value is within a range (min &amp; max specified
     * in the vars attribute).</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
     */
    public static boolean isInRange(final double value, final double min, final double max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if a value is within a range (min &amp; max specified
     * in the vars attribute).</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
     */
    public static boolean isInRange(final float value, final float min, final float max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if a value is within a range (min &amp; max specified
     * in the vars attribute).</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
     */
    public static boolean isInRange(final int value, final int min, final int max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if a value is within a range (min &amp; max specified
     * in the vars attribute).</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
     */
    public static boolean isInRange(final long value, final long min, final long max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if a value is within a range (min &amp; max specified
     * in the vars attribute).</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return true if the value is in the specified range.
     */
    public static boolean isInRange(final short value, final short min, final short max) {
        return value >= min && value <= max;
    }

    /**
     * <p>Checks if the value is less than or equal to the max.</p>
     *
     * @param value The value validation is being performed on.
     * @param max The maximum numeric value.
     * @return true if the value is &lt;= the specified maximum.
     */
    public static boolean maxValue(final double value, final double max) {
        return value <= max;
    }

    /**
     * <p>Checks if the value is less than or equal to the max.</p>
     *
     * @param value The value validation is being performed on.
     * @param max The maximum numeric value.
     * @return true if the value is &lt;= the specified maximum.
     */
    public static boolean maxValue(final float value, final float max) {
        return value <= max;
    }

    /**
     * <p>Checks if the value is less than or equal to the max.</p>
     *
     * @param value The value validation is being performed on.
     * @param max The maximum numeric value.
     * @return true if the value is &lt;= the specified maximum.
     */
    public static boolean maxValue(final int value, final int max) {
        return value <= max;
    }

    // See https://issues.apache.org/bugzilla/show_bug.cgi?id=29015 WRT the "value" methods

    /**
     * <p>Checks if the value is less than or equal to the max.</p>
     *
     * @param value The value validation is being performed on.
     * @param max The maximum numeric value.
     * @return true if the value is &lt;= the specified maximum.
     */
    public static boolean maxValue(final long value, final long max) {
        return value <= max;
    }

    /**
     * <p>Checks if the value is greater than or equal to the min.</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum numeric value.
     * @return true if the value is &gt;= the specified minimum.
     */
    public static boolean minValue(final double value, final double min) {
        return value >= min;
    }

    /**
     * <p>Checks if the value is greater than or equal to the min.</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum numeric value.
     * @return true if the value is &gt;= the specified minimum.
     */
    public static boolean minValue(final float value, final float min) {
        return value >= min;
    }

    /**
     * <p>Checks if the value is greater than or equal to the min.</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum numeric value.
     * @return true if the value is &gt;= the specified minimum.
     */
    public static boolean minValue(final int value, final int min) {
        return value >= min;
    }

    /**
     * <p>Checks if the value is greater than or equal to the min.</p>
     *
     * @param value The value validation is being performed on.
     * @param min The minimum numeric value.
     * @return true if the value is &gt;= the specified minimum.
     */
    public static boolean minValue(final long value, final long min) {
        return value >= min;
    }

    /**
     * Constructs a new instance.
     *
     * @deprecated Will be private in the next major version.
     */
    @Deprecated
    public GenericValidator() {
        // empty
    }

}
