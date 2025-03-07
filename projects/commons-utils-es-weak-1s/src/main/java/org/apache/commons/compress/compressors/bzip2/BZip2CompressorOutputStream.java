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
package org.apache.commons.compress.compressors.bzip2;


public class BZip2CompressorOutputStream  {

    /**
     * The minimum supported blocksize {@code  == 1}.
     */
    public static final int MIN_BLOCKSIZE = 1;

    /**
     * The maximum supported blocksize {@code  == 9}.
     */
    public static final int MAX_BLOCKSIZE = 9;
    private static final int GREATER_ICOST = 15;

    private static final int LESSER_ICOST = 0;

    /**
     * Chooses a blocksize based on the given length of the data to compress.
     *
     * @return The blocksize, between {@link #MIN_BLOCKSIZE} and {@link #MAX_BLOCKSIZE} both inclusive. For a negative {@code inputLength} this method returns
     *         {@code MAX_BLOCKSIZE} always.
     *
     * @param inputLength The length of the data which will be compressed by {@code BZip2CompressorOutputStream}.
     */
    public static int chooseBlockSize(final long inputLength) {
        return inputLength > 0 ? (int) Math.min(inputLength / 132000 + 1, 9) : MAX_BLOCKSIZE;
    }

}
