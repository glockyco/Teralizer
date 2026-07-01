package teralizer.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import teralizer.domain.NullValue;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.StringValue;
import teralizer.domain.Value;

/**
 * Pins faithful JSON round-tripping of the typed {@link Value} hierarchy: the variant, declared
 * type, and exact payload survive serialization. The hard cases are the ones the stringly format
 * mishandled — exact {@code float} precision, non-finite reals (no JSON literal), a NUL inside a
 * string, and a typed null reference.
 */
class ValueJsonAdapterTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeHierarchyAdapter(Value.class, new ValueJsonAdapter())
        .create();

    private Value roundTrip(Value value) {
        return gson.fromJson(gson.toJson(value), Value.class);
    }

    @Test
    void roundTripsPrimitivesIntoExactWrappers() {
        PrimitiveValue intValue = (PrimitiveValue) roundTrip(new PrimitiveValue("int", 7));
        assertEquals("int", intValue.getJavaType());
        assertEquals(Integer.valueOf(7), intValue.getValue());

        assertEquals(Long.valueOf(7L), ((PrimitiveValue) roundTrip(new PrimitiveValue("long", 7L))).getValue());
        assertEquals(Float.valueOf(3.14f), ((PrimitiveValue) roundTrip(new PrimitiveValue("float", 3.14f))).getValue());
        assertEquals(Character.valueOf('A'), ((PrimitiveValue) roundTrip(new PrimitiveValue("char", 'A'))).getValue());
        assertEquals(Boolean.TRUE, ((PrimitiveValue) roundTrip(new PrimitiveValue("boolean", true))).getValue());
    }

    @Test
    void roundTripsNonFiniteReals() {
        assertEquals(Float.valueOf(Float.NaN),
            ((PrimitiveValue) roundTrip(new PrimitiveValue("float", Float.NaN))).getValue());
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY),
            ((PrimitiveValue) roundTrip(new PrimitiveValue("double", Double.NEGATIVE_INFINITY))).getValue());
    }

    @Test
    void roundTripsStringWithNulAndQuotes() {
        StringValue value = (StringValue) roundTrip(new StringValue("a\"b\u0000c"));
        assertEquals("a\"b\u0000c", value.getValue());
    }

    @Test
    void roundTripsTypedNullReference() {
        NullValue value = (NullValue) roundTrip(new NullValue("java.lang.Boolean"));
        assertEquals("java.lang.Boolean", value.getJavaType());
    }

    @Test
    void serializesTheKindTaggedForm() {
        assertEquals("{\"kind\":\"PRIMITIVE\",\"type\":\"int\",\"value\":7}",
            gson.toJson(new PrimitiveValue("int", 7)));
    }
}
