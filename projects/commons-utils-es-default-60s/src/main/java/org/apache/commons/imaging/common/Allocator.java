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

package org.apache.commons.imaging.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.function.IntFunction;

/**
 * Checks inputs for meeting allocation limits and allocates arrays.
 */
public class Allocator {

    private static final String CANONICAL_NAME = Allocator.class.getCanonicalName();

    /** One GB. */
    private static final int DEFAULT = 1_073_741_824;
    private static final int LIMIT;

    static {
        LIMIT = Integer.getInteger(CANONICAL_NAME, DEFAULT);
    }


    /**
     * Checks a request for meeting allocation limits.
     * <p>
     * The default limit is {@value #DEFAULT}, override with the system property "org.apache.commons.imaging.common.mylzw.AllocationChecker".
     * </p>
     *
     * @param request an allocation request.
     * @return the request.
     * @throws AllocationRequestException Thrown when the request exceeds the limit.
     */
    public static int check(final int request) {
        if (request > LIMIT) {
            throw new AllocationRequestException(LIMIT, request);
        }
        return request;
    }

    /**
     * Checks a request for meeting allocation limits.
     * <p>
     * The default limit is {@value #DEFAULT}, override with the system property "org.apache.commons.imaging.common.mylzw.AllocationChecker".
     * </p>
     *
     * @param request     an allocation request count.
     * @param elementSize The element size.
     * @return the request.
     * @throws AllocationRequestException Thrown when the request exceeds the limit.
     */
    public static int check(final int request, final int elementSize) {
        final int multiplyExact;
        try {
            multiplyExact = Math.multiplyExact(request, elementSize);
        } catch (final ArithmeticException e) {
            throw new AllocationRequestException(LIMIT, BigInteger.valueOf(request).multiply(BigInteger.valueOf(elementSize)), e);
        }
        if (multiplyExact > LIMIT) {
            throw new AllocationRequestException(LIMIT, request);
        }
        return request;
    }

    /**
     * Checks a request for meeting allocation limits.
     * <p>
     * The default limit is {@value #DEFAULT}, override with the system property "org.apache.commons.imaging.common.mylzw.AllocationChecker".
     * </p>
     *
     * @param request     an allocation request count is cast down to an int.
     * @param elementSize The element size.
     * @return the request.
     * @throws AllocationRequestException Thrown when the request exceeds the limit.
     */
    public static int check(final long request, final int elementSize) {
        try {
            return check(Math.toIntExact(request), elementSize);
        } catch (final ArithmeticException e) {
            throw new AllocationRequestException(LIMIT, request, e);
        }
    }

}
