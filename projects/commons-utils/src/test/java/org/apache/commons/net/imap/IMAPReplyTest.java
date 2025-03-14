/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.net.imap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IMAPReplyTest {

    private static Stream<String> invalidLiteralCommands() {
        return Stream.of(
                "",
                "{",
                "}",
                "{}",
                "{foobar}",
                "STORE +FLAGS.SILENT \\DELETED {",
                "STORE +FLAGS.SILENT \\DELETED }",
                "STORE +FLAGS.SILENT \\DELETED {-1}",
                "STORE +FLAGS.SILENT \\DELETED {-10}",
                "STORE +FLAGS.SILENT \\DELETED {-2147483648}"
        );
    }

    private static Stream<Arguments> literalCommands() {
        return Stream.of(
                Arguments.of(310, "A003 APPEND saved-messages (\\Seen) {310}"),
                Arguments.of(6, "A284 SEARCH CHARSET UTF-8 TEXT {6}"),
                Arguments.of(7, "FRED FOOBAR {7}"),
                Arguments.of(102856, "A044 BLURDYBLOOP {102856}"),
                Arguments.of(342, "* 12 FETCH (BODY[HEADER] {342}"),
                Arguments.of(0, "X999 LOGIN {0}"),
                Arguments.of(Integer.MAX_VALUE, "X999 LOGIN {2147483647}")
        );
    }
    @Test
    public void testIsContinuationReplyCode() {
        final int replyCode = 3;
        assertTrue(IMAPReply.isContinuation(replyCode));
    }

    @Test
    public void testIsContinuationReplyCodeInvalidCode() {
        final int invalidContinuationReplyCode = 1;
        assertFalse(IMAPReply.isContinuation(invalidContinuationReplyCode));
    }

    @Test
    public void testIsSuccessReplyCode() {
        final int successfulReplyCode = 0;
        assertTrue(IMAPReply.isSuccess(successfulReplyCode));
    }

    @Test
    public void testIsSuccessReplyCodeUnsuccessfulCode() {
        final int unsuccessfulReplyCode = 2;
        assertFalse(IMAPReply.isSuccess(unsuccessfulReplyCode));
    }
}
