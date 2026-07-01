package teralizer.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import teralizer.domain.NullValue;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.StringValue;

/**
 * Pins how a typed {@link teralizer.domain.Value} renders to a Java literal. The typed renderer
 * supersedes the stringly {@code transform(MethodArgument)} path: a {@code float} keeps its exact
 * representation, a null reference renders as the literal (never re-parsed from {@code "null"}), and
 * a {@code String} is escaped into a valid literal — including a NUL, which the old raw-value path
 * passed through unquoted and uncorrected.
 */
class ModelToJavaTransformerValueTest {

    private final ModelToJavaTransformer transformer = new ModelToJavaTransformer();

    @Test
    void rendersIntegralValues() {
        assertEquals("7", transformer.transform(new PrimitiveValue("int", 7)));
        assertEquals("7", transformer.transform(new PrimitiveValue("java.lang.Integer", 7)));
        assertEquals("5", transformer.transform(new PrimitiveValue("byte", (byte) 5)));
        assertEquals("9999999999L", transformer.transform(new PrimitiveValue("long", 9999999999L)));
        // A small long still takes the L suffix from its declared type (unlike transform(long)).
        assertEquals("7L", transformer.transform(new PrimitiveValue("long", 7L)));
    }

    @Test
    void rendersRealValuesWithSuffixAndNonFiniteSpecials() {
        assertEquals("3.14F", transformer.transform(new PrimitiveValue("float", 3.14f)));
        assertEquals("Float.NaN", transformer.transform(new PrimitiveValue("float", Float.NaN)));
        assertEquals("Float.POSITIVE_INFINITY",
            transformer.transform(new PrimitiveValue("float", Float.POSITIVE_INFINITY)));
        assertEquals("3.14", transformer.transform(new PrimitiveValue("double", 3.14d)));
        assertEquals("Double.NEGATIVE_INFINITY",
            transformer.transform(new PrimitiveValue("double", Double.NEGATIVE_INFINITY)));
    }

    @Test
    void rendersBooleanLiterals() {
        assertEquals("true", transformer.transform(new PrimitiveValue("boolean", true)));
        assertEquals("false", transformer.transform(new PrimitiveValue("java.lang.Boolean", false)));
    }

    @Test
    void rendersCharAsCodePointCastForEveryChar() {
        assertEquals("(char) 65", transformer.transform(new PrimitiveValue("char", 'A')));
        assertEquals("(char) 0", transformer.transform(new PrimitiveValue("char", '\u0000')));
    }

    @Test
    void rendersNullReferenceAsLiteral() {
        assertEquals("null", transformer.transform(new NullValue("java.lang.Boolean")));
    }

    @Test
    void rendersStringWithProperEscaping() {
        assertEquals("\"n=7\"", transformer.transform(new StringValue("n=7")));
        assertEquals("\"a\\\"b\\\\c\"", transformer.transform(new StringValue("a\"b\\c")));
        assertEquals("\"line1\\nline2\"", transformer.transform(new StringValue("line1\nline2")));
    }

    @Test
    void rendersNulAndControlCharsInStringAsUnicodeEscapes() {
        assertEquals("\"a\\u0000b\"", transformer.transform(new StringValue("a\u0000b")));
        assertEquals("\"\\u0001\"", transformer.transform(new StringValue("\u0001")));
    }
}
