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
package org.apache.commons.lang3.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link org.apache.commons.lang3.math.IEEE754rUtils}.
 */
public class IEEE754rUtilsTest {

    @Test
    public void testConstructorExists() {
        new IEEE754rUtils();
    }


    @Test
    public void testLang381() {
        assertEquals(1.2, IEEE754rUtils.min(1.2, 2.5, Double.NaN), 0.01);
        assertEquals(2.5, IEEE754rUtils.max(1.2, 2.5, Double.NaN), 0.01);
        assertTrue(Double.isNaN(IEEE754rUtils.max(Double.NaN, Double.NaN, Double.NaN)));
        assertEquals(1.2f, IEEE754rUtils.min(1.2f, 2.5f, Float.NaN), 0.01);
        assertEquals(2.5f, IEEE754rUtils.max(1.2f, 2.5f, Float.NaN), 0.01);
        assertTrue(Float.isNaN(IEEE754rUtils.max(Float.NaN, Float.NaN, Float.NaN)));

    }

}
