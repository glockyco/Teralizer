/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.codec.binary;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for Base64 class.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 */
public class Base64Test {

    private static final String FOX_BASE64 = "VGhlIH@$#$@%F1aWN@#@#@@rIGJyb3duIGZve\n\r\t%#%#%#%CBqd##$#$W1wZWQgb3ZlciB0aGUgbGF6eSBkb2dzLg==";

    private static final String FOX_TEXT = "The quick brown fox jumped over the lazy dogs.";

    private static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;

    /**
     * Example test cases with valid characters but impossible combinations of
     * trailing characters (i.e. cannot be created during encoding).
     */
    static final String[] BASE64_IMPOSSIBLE_CASES = {
        "ZE==",
        "ZmC=",
        "Zm9vYE==",
        "Zm9vYmC=",
        "AB",
    };

    /**
     * Copy of the standard base-64 encoding table. Used to test decoding the final
     * character of encoded bytes.
     */
    private static final byte[] STANDARD_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    @Test
    public void testIsArrayByteBase64() {
        assertFalse(Base64.isBase64(new byte[] { Byte.MIN_VALUE }));
        assertFalse(Base64.isBase64(new byte[] { -125 }));
        assertFalse(Base64.isBase64(new byte[] { -10 }));
        assertFalse(Base64.isBase64(new byte[] { 0 }));
        assertFalse(Base64.isBase64(new byte[] { 64, Byte.MAX_VALUE }));
        assertFalse(Base64.isBase64(new byte[] { Byte.MAX_VALUE }));

        assertTrue(Base64.isBase64(new byte[] { 'A' }));

        assertFalse(Base64.isBase64(new byte[] { 'A', Byte.MIN_VALUE }));

        assertTrue(Base64.isBase64(new byte[] { 'A', 'Z', 'a' }));
        assertTrue(Base64.isBase64(new byte[] { '/', '=', '+' }));

        assertFalse(Base64.isBase64(new byte[] { '$' }));
    }

}
