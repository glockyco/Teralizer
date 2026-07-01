package teralizer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pins the construction invariants of the typed {@link Value} variants: the typed boundary must
 * reject inconsistent pairs at construction rather than carrying them like the stringly
 * {@link MethodArgument} could.
 */
class ValueTest {

    @Test
    void primitiveValueRejectsNullPayload() {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveValue("int", null));
    }

    @Test
    void primitiveValueRejectsWrapperMismatchingTheDeclaredType() {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveValue("int", Boolean.TRUE));
    }

    @Test
    void primitiveValueRejectsNonPrimitiveOrNullType() {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveValue("java.lang.String", "x"));
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveValue(null, 1));
    }

    @Test
    void primitiveValueAcceptsMatchingWrapperAndKeepsExactValue() {
        PrimitiveValue intValue = new PrimitiveValue("int", 7);
        assertEquals("int", intValue.getJavaType());
        assertEquals(7, intValue.getValue());
        // The boxed Float wrapper preserves the exact value (no double-widening).
        assertEquals(3.14f, new PrimitiveValue("float", 3.14f).getValue());
    }

    @Test
    void nullValueRejectsBlankOrPrimitiveTypes() {
        assertThrows(IllegalArgumentException.class, () -> new NullValue(null));
        assertThrows(IllegalArgumentException.class, () -> new NullValue("  "));
        assertThrows(IllegalArgumentException.class, () -> new NullValue("int"));
    }

    @Test
    void nullValueAcceptsReferenceTypes() {
        assertEquals("java.lang.Boolean", new NullValue("java.lang.Boolean").getJavaType());
        assertEquals("java.lang.String", new NullValue("java.lang.String").getJavaType());
    }

    @Test
    void stringValueRejectsNullContentAndIsAlwaysStringTyped() {
        assertThrows(IllegalArgumentException.class, () -> new StringValue(null));
        assertEquals("java.lang.String", new StringValue("x").getJavaType());
    }
}
