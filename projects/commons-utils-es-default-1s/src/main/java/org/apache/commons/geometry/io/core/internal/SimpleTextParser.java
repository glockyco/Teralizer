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
package org.apache.commons.geometry.io.core.internal;

import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** Class providing basic text parsing capabilities. The goals of this class are to
 * (1) provide a simple, flexible API for performing common text parsing operations and
 * (2) provide a mechanism for creating consistent and informative parsing errors.
 * This class is not intended as a replacement for grammar-based parsers and/or lexers.
 */
public class SimpleTextParser {
    /** Carriage return character. */
    private static final char CR = '\r';

    /** Line feed character. */
    private static final char LF = '\n';

    public static boolean isWhitespace(final int ch) {
        return Character.isWhitespace(ch);
    }

    public static boolean isNotWhitespace(final int ch) {
        return !isWhitespace(ch);
    }

    public static boolean isLineWhitespace(final int ch) {
        return isWhitespace(ch) && isNotNewLinePart(ch);
    }

    public static boolean isNewLinePart(final int ch) {
        return ch == CR || ch == LF;
    }

    public static boolean isNotNewLinePart(final int ch) {
        return !isNewLinePart(ch);
    }

}
