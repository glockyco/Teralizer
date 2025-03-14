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
package org.apache.commons.validator.routines.checkdigit;

import java.io.Serializable;

import org.apache.commons.validator.GenericValidator;

/**
 * Abstract <strong>Modulus</strong> Check digit calculation/validation.
 * <p>
 * Provides a <em>base</em> class for building <em>modulus</em> Check Digit routines.
 * </p>
 * <p>
 * This implementation only handles <em>single-digit numeric</em> codes, such as <strong>EAN-13</strong>. For <em>alphanumeric</em> codes such as <strong>EAN-128</strong> you will need
 * to implement/override the {@code toInt()} and {@code toChar()} methods.
 * </p>
 *
 * @since 1.4
 */
public abstract class ModulusCheckDigit implements Serializable {

    static final int MODULUS_10 = 10;
    static final int MODULUS_11 = 11;
    private static final long serialVersionUID = 2948962251251528941L;

    /**
     * Add together the individual digits in a number.
     *
     * @param number The number whose digits are to be added
     * @return The sum of the digits
     */
    public static int sumDigits(final int number) {
        int total = 0;
        int todo = number;
        while (todo > 0) {
            total += todo % 10; // CHECKSTYLE IGNORE MagicNumber
            todo /= 10; // CHECKSTYLE IGNORE MagicNumber
        }
        return total;
    }

    /**
     * The modulus can be greater than 10 provided that the implementing class overrides toCheckDigit and toInt (for example as in ISBN10CheckDigit).
     */
    private final int modulus;

    ModulusCheckDigit() {
        this(MODULUS_10);
    }

    public ModulusCheckDigit(final int modulus) {
        this.modulus = modulus;
    }

    public int getModulus() {
        return modulus;
    }
}
