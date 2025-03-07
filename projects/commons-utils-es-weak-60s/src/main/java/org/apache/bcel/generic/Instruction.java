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
package org.apache.bcel.generic;

/**
 * Abstract super class for all Java byte codes.
 */
public abstract class Instruction implements Cloneable {
    public static boolean isValidByte(final int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    /**
     * Tests if the value can fit in a short (signed)
     *
     * @param value the value to check
     * @return true if the value is in range
     * @since 6.0
     */
    public static boolean isValidShort(final int value) {
        return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
    }

}
