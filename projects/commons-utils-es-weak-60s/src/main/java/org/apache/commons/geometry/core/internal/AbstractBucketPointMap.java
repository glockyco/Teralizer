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
package org.apache.commons.geometry.core.internal;


public abstract class AbstractBucketPointMap {
    /** Get an encoded search location value for the given comparison result. If
     * {@code cmp} is {@code 0}, then the bitwise OR of {@code neg} and {@code pos}
     * is returned, indicating that both spaces are valid search locations. Otherwise,
     * {@code neg} is returned for negative {@code cmp} values and {@code pos} for
     * positive ones. This location value is to be used during entry searches,
     * when comparisons must be loose and all possible locations included.
     * @param cmp comparison result
     * @param neg negative flag
     * @param pos positive flag
     * @return encoded search location value
     */
    public static int getSearchLocationValue(final int cmp, final int neg, final int pos) {
        if (cmp < 0) {
            return neg;
        } else if (cmp > 0) {
            return pos;
        }
        return neg | pos;
    }

    /** Get an insert location value for the given comparison result. If {@code cmp}
     * is less than or equal to {@code 0}, then {@code neg} is returned. Otherwise,
     * {@code pos} is returned. This location value is to be used during entry inserts,
     * where comparisons must be strict.
     * @param cmp comparison result
     * @param neg negative flag
     * @param pos positive flag
     * @return encoded insert location value
     */
    public static int getInsertLocationValue(final int cmp, final int neg, final int pos) {
        return cmp <= 0 ?
                neg :
                pos;
    }

    /** Get the maximum distance value from {@code n} to either {@code a} or {@code b}.
     * @param n test coordinate
     * @param a first coordinate
     * @param b second coordinate
     * @return maximum distance from {@code n} to {@code a} or {@code b}
     */
    public static double getMaxDistance(final double n, final double a, final double b) {
        return Math.max(
                Math.abs(n - a),
                Math.abs(n - b));
    }
}
