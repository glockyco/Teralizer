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

import java.io.FilterReader;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Utility functions that do not really belong to any class in particular.
 */
// @since 6.0 methods are no longer final
public abstract class Utility {

    /*
     * How many chars have been consumed during parsing in typeSignatureToString(). Read by methodSignatureToString(). Set
     * by side effect, but only internally.
     */
    private static final ThreadLocal<Integer> CONSUMER_CHARS = ThreadLocal.withInitial(() -> Integer.valueOf(0));

    /*
     * The 'WIDE' instruction is used in the byte code to allow 16-bit wide indices for local variables. This opcode
     * precedes an 'ILOAD', for example. The opcode immediately following takes an extra byte which is combined with the following
     * byte to form a 16-bit value.
     */
    private static boolean wide;

    // A-Z, g-z, _, $
    private static final int FREE_CHARS = 48;

    private static final int[] CHAR_MAP = new int[FREE_CHARS];

    private static final int[] MAP_CHAR = new int[256]; // Reverse map

    private static final char ESCAPE_CHAR = '$';

    static {
        int j = 0;
        for (int i = 'A'; i <= 'Z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        for (int i = 'g'; i <= 'z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        CHAR_MAP[j] = '$';
        MAP_CHAR['$'] = j;
        j++;
        CHAR_MAP[j] = '_';
        MAP_CHAR['_'] = j;
    }

    /**
     * @return 'flag' with bit 'i' set to 0
     */
    public static int clearBit(final int flag, final int i) {
        final int bit = pow2(i);
        return (flag & bit) == 0 ? flag : flag ^ bit;
    }

    /**
     * @return true, if bit 'i' in 'flag' is set
     */
    public static boolean isSet(final int flag, final int i) {
        return (flag & pow2(i)) != 0;
    }

    private static int pow2(final int n) {
        return 1 << n;
    }

    /**
     * @return 'flag' with bit 'i' set to 1
     */
    public static int setBit(final int flag, final int i) {
        return flag | pow2(i);
    }

}
