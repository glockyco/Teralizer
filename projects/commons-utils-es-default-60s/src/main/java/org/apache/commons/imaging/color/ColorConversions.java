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
package org.apache.commons.imaging.color;

public final class ColorConversions {

    // White reference
    /** See: https://en.wikipedia.org/wiki/CIELAB_color_space#From_CIEXYZ_to_CIELAB[10] */
    private static final double REF_X = 95.047; // Observer= 2°, Illuminant= D65

    /** See: https://en.wikipedia.org/wiki/CIELAB_color_space#From_CIEXYZ_to_CIELAB[10] */
    private static final double REF_Y = 100.000;

    /** See: https://en.wikipedia.org/wiki/CIELAB_color_space#From_CIEXYZ_to_CIELAB[10] */
    private static final double REF_Z = 108.883;

    /** See: https://en.wikipedia.org/wiki/CIELAB_color_space#From_CIEXYZ_to_CIELAB[10] */
    private static final double XYZ_m = 7.787037; // match in slope. Note commonly seen 7.787 gives worse results

    /** See: https://en.wikipedia.org/wiki/CIELAB_color_space#From_CIEXYZ_to_CIELAB[10] */
    private static final double XYZ_t0 = 0.008856;
    private static double convertHueToRgb(final double v1, final double v2, double vH) {
        if (vH < 0) {
            vH += 1;
        }
        if (vH > 1) {
            vH -= 1;
        }
        if (6 * vH < 1) {
            return v1 + (v2 - v1) * 6 * vH;
        }
        if (2 * vH < 1) {
            return v2;
        }
        if (3 * vH < 2) {
            return v1 + (v2 - v1) * (2 / 3.0 - vH) * 6;
        }
        return v1;
    }

    public static int convertCieLabToArgbTest(final int cieL, final int cieA, final int cieB) {
        final double x;
        final double y;
        final double z;
        {

            double varY = (cieL * 100.0 / 255.0 + 16.0) / 116.0;
            double varX = cieA / 500.0 + varY;
            double varZ = varY - cieB / 200.0;

            varX = unPivotXyz(varX);
            varY = unPivotXyz(varY);
            varZ = unPivotXyz(varZ);

            x = REF_X * varX; // REF_X = 95.047 Observer= 2°, Illuminant= D65
            y = REF_Y * varY; // REF_Y = 100.000
            z = REF_Z * varZ; // REF_Z = 108.883

        }

        final double r;
        final double g;
        final double b;
        {
            final double varX = x / 100; // X = From 0 to REF_X
            final double varY = y / 100; // Y = From 0 to REF_Y
            final double varZ = z / 100; // Z = From 0 to REF_Y

            double varR = varX * 3.2406 + varY * -1.5372 + varZ * -0.4986;
            double varG = varX * -0.9689 + varY * 1.8758 + varZ * 0.0415;
            double varB = varX * 0.0557 + varY * -0.2040 + varZ * 1.0570;

            varR = pivotRgb(varR);
            varG = pivotRgb(varG);
            varB = pivotRgb(varB);

            r = varR * 255;
            g = varG * 255;
            b = varB * 255;
        }

        return convertRgbToRgb(r, g, b);
    }

    public static int convertCmykToRgb(final int c, final int m, final int y, final int k) {
        final double C = c / 255.0;
        final double M = m / 255.0;
        final double Y = y / 255.0;
        final double K = k / 255.0;

        return convertCmyToRgb(convertCmykToCmy(C, M, Y, K));
    }

    public static int convertHslToRgb(final double h, final double s, final double l) {
        final double r;
        final double g;
        final double b;
        if (s == 0) {
            // HSL values = 0 ÷ 1
            r = l * 255; // RGB results = 0 ÷ 255
            g = l * 255;
            b = l * 255;
        } else {
            final double var2;

            if (l < 0.5) {
                var2 = l * (1 + s);
            } else {
                var2 = l + s - s * l;
            }

            final double var1 = 2 * l - var2;

            r = 255 * convertHueToRgb(var1, var2, h + 1 / 3.0);
            g = 255 * convertHueToRgb(var1, var2, h);
            b = 255 * convertHueToRgb(var1, var2, h - 1 / 3.0);
        }

        return convertRgbToRgb(r, g, b);
    }

    public static int convertHsvToRgb(final double h, final double s, final double v) {
        final double r;
        final double g;
        final double b;
        if (s == 0) {
            // HSV values = 0 ÷ 1
            r = v * 255;
            g = v * 255;
            b = v * 255;
        } else {
            double varH = h * 6;
            if (varH == 6) {
                varH = 0; // H must be < 1
            }
            final double varI = Math.floor(varH); // Or ... varI = floor( varH )
            final double var1 = v * (1 - s);
            final double var2 = v * (1 - s * (varH - varI));
            final double var3 = v * (1 - s * (1 - (varH - varI)));

            final double varR;
            final double varG;
            final double varB;

            if (varI == 0) {
                varR = v;
                varG = var3;
                varB = var1;
            } else if (varI == 1) {
                varR = var2;
                varG = v;
                varB = var1;
            } else if (varI == 2) {
                varR = var1;
                varG = v;
                varB = var3;
            } else if (varI == 3) {
                varR = var1;
                varG = var2;
                varB = v;
            } else if (varI == 4) {
                varR = var3;
                varG = var1;
                varB = v;
            } else {
                varR = v;
                varG = var1;
                varB = var2;
            }

            r = varR * 255; // RGB results = 0 ÷ 255
            g = varG * 255;
            b = varB * 255;
        }

        return convertRgbToRgb(r, g, b);
    }

    public static int convertCmykToRgbAdobe(final int sc, final int sm, final int sy, final int sk) {
        final int red = 255 - (sc + sk);
        final int green = 255 - (sm + sk);
        final int blue = 255 - (sy + sk);

        return convertRgbToRgb(red, green, blue);
    }

    public static int convertXyzToRgb(final double x, final double y, final double z) {
        // Observer = 2°, Illuminant = D65
        final double varX = x / 100.0; // Where X = 0 ÷ 95.047
        final double varY = y / 100.0; // Where Y = 0 ÷ 100.000
        final double varZ = z / 100.0; // Where Z = 0 ÷ 108.883

        // see: https://github.com/StanfordHCI/c3/blob/master/java/src/edu/stanford/vis/color/LAB.java
        double varR = varX * 3.2404542 + varY * -1.5371385 + varZ * -0.4985314;
        double varG = varX * -0.9692660 + varY * 1.8760108 + varZ * 0.0415560;
        double varB = varX * 0.0556434 + varY * -0.2040259 + varZ * 1.0572252;

        // Attention: A lot of sources do list these values with less precision. But it makes a visual difference:
        // double var_R = var_X * 3.2406 + var_Y * -1.5372 + var_Z * -0.4986;
        // double var_G = var_X * -0.9689 + var_Y * 1.8758 + var_Z * 0.0415;
        // double var_B = var_X * 0.0557 + var_Y * -0.2040 + var_Z * 1.0570;

        varR = pivotRgb(varR);
        varG = pivotRgb(varG);
        varB = pivotRgb(varB);

        final double r = varR * 255;
        final double g = varG * 255;
        final double b = varB * 255;
        return convertRgbToRgb(r, g, b);
    }

    public static ColorCmy convertCmykToCmy(double c, double m, double y, final double k) {
        // Where CMYK and CMY values = 0 ÷ 1

        c = c * (1 - k) + k;
        m = m * (1 - k) + k;
        y = y * (1 - k) + k;

        return new ColorCmy(c, m, y);
    }

    public static int convertCmyToRgb(final ColorCmy cmy) {
        // From Ghostscript's gdevcdj.c:
        // * Ghostscript: R = (1.0 - C) * (1.0 - K)
        // * Adobe: R = 1.0 - min(1.0, C + K)
        // and similarly for G and B.
        // This is Ghostscript's formula with K = 0.

        // CMY values = 0 ÷ 1
        // RGB values = 0 ÷ 255

        final double r = (1 - cmy.c) * 255.0;
        final double g = (1 - cmy.m) * 255.0;
        final double b = (1 - cmy.y) * 255.0;

        return convertRgbToRgb(r, g, b);
    }


    private static int convertRgbToRgb(final double r, final double g, final double b) {
        int red = (int) Math.round(r);
        int green = (int) Math.round(g);
        int blue = (int) Math.round(b);

        red = Math.min(255, Math.max(0, red));
        green = Math.min(255, Math.max(0, green));
        blue = Math.min(255, Math.max(0, blue));

        final int alpha = 0xff;

        return alpha << 24 | red << 16 | green << 8 | blue << 0;
    }

    private static int convertRgbToRgb(int red, int green, int blue) {
        red = Math.min(255, Math.max(0, red));
        green = Math.min(255, Math.max(0, green));
        blue = Math.min(255, Math.max(0, blue));

        final int alpha = 0xff;

        return alpha << 24 | red << 16 | green << 8 | blue << 0;
    }



    public static double degree2radian(final double degree) {
        return degree * Math.PI / 180.0;
    }

    private static double pivotRgb(double n) {
        if (n > 0.0031308) {
            n = 1.055 * Math.pow(n, 1 / 2.4) - 0.055;
        } else {
            n = 12.92 * n;
        }
        return n;
    }

    private static double pivotXyz(double n) {
        if (n > XYZ_t0) {
            n = Math.pow(n, 1 / 3.0);
        } else {
            n = XYZ_m * n + 16 / 116.0;
        }
        return n;
    }

    public static double radian2degree(final double radian) {
        return radian * 180.0 / Math.PI;
    }

    private static double square(final double f) {
        return f * f;
    }

    private static double unPivotRgb(double n) {
        if (n > 0.04045) {
            n = Math.pow((n + 0.055) / 1.055, 2.4);
        } else {
            n /= 12.92;
        }
        return n;
    }

    private static double unPivotXyz(double n) {
        final double nCube = Math.pow(n, 3);
        if (nCube > XYZ_t0) {
            n = nCube;
        } else {
            n = (n - 16 / 116.0) / XYZ_m;
        }
        return n;
    }

    private ColorConversions() {
    }

}
