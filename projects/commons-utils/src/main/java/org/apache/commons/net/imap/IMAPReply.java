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

package org.apache.commons.net.imap;


import java.util.regex.Pattern;

/**
 * Stores IMAP reply code constants.
 */
public final class IMAPReply {
    /** The reply code indicating success of an operation. */
    public static final int OK = 0;

    /** The reply code indicating failure of an operation. */
    public static final int NO = 1;

    /** The reply code indicating command rejection. */
    public static final int BAD = 2;

    /** The reply code indicating command continuation. */
    public static final int CONT = 3;

    /**
     * The reply code indicating a partial response. This is used when a chunk listener is registered and the listener requests that the reply lines are cleared
     * on return.
     *
     * @since 3.4
     */
    public static final int PARTIAL = 3;

    /** The IMAP reply String indicating success of an operation. */
    private static final String IMAP_OK = "OK";

    /** The IMAP reply String indicating failure of an operation. */
    private static final String IMAP_NO = "NO";

    /** The IMAP reply String indicating command rejection. */
    private static final String IMAP_BAD = "BAD";

    // Start of line for untagged replies
    private static final String IMAP_UNTAGGED_PREFIX = "* ";

    // Start of line for continuation replies
    private static final String IMAP_CONTINUATION_PREFIX = "+";

    /**
     * Guards against Polynomial regular expression used on uncontrolled data.
     * <ol>
     * <li>the start of a line.</li>
     * <li>letters, up to 80.</li>
     * <li>a space.</li>
     * <li>non-whitespace characters, up to 80, for example {@code OK}.</li>
     * <li>up to 500 extra characters.</li>
     * </ol>
     */
    private static final String TAGGED_RESPONSE = "^\\w{1,80} (\\S{1,80}).{0,500}";

    /**
     * Tag cannot contain: + ( ) { SP CTL % * " \ ]
     */
    private static final Pattern TAGGED_PATTERN = Pattern.compile(TAGGED_RESPONSE);

    /**
     * Guards against Polynomial regular expression used on uncontrolled data.
     * <ol>
     * <li>the start of a line, then a star, then a space.</li>
     * <li>non-whitespace characters, up to 80, for example {@code OK}.</li>
     * <li>up to 500 extra characters.</li>
     * </ol>
     */
    private static final String UNTAGGED_RESPONSE = "^\\* (\\S{1,80}).{0,500}";

    private static final Pattern UNTAGGED_PATTERN = Pattern.compile(UNTAGGED_RESPONSE);
    private static final Pattern LITERAL_PATTERN = Pattern.compile("\\{(\\d+)\\}$"); // {dd}


    /**
     * Tests whether the reply line is a continuation, i.e. starts with "+"
     *
     * @param replyCode the code to be checked
     * @return {@code true} if the response was a continuation
     */
    public static boolean isContinuation(final int replyCode) {
        return replyCode == CONT;
    }

    /**
     * Tests whether whether the reply code indicates success or not
     *
     * @param replyCode the code to check
     * @return {@code true} if the code equals {@link #OK}
     */
    public static boolean isSuccess(final int replyCode) {
        return replyCode == OK;
    }

    /** Cannot be instantiated. */
    private IMAPReply() {
    }

}

