package teralizer.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringTest {

    @Test
    public void testStartsWith() {
        boolean expected = true;
        boolean actual = "uiae".startsWith("uiae");
        assertEquals(expected, actual);
    }

    @Test
    public void testEndsWith() {
        boolean expected = true;
        boolean actual = "uiae".endsWith("uiae");
        assertEquals(expected, actual);
    }

    @Test
    public void testContains() {
        boolean expected = true;
        boolean actual = "uiae".contains("uiae");
        assertEquals(expected, actual);
    }

    @Test
    public void testToUpperCase() {
        String expected = "UIAE";
        String actual = "uiae".toUpperCase();
        assertEquals(expected, actual);
    }

    @Test
    public void testToLowerCase() {
        String expected = "uiae";
        String actual = "UIAE".toLowerCase();
        assertEquals(expected, actual);
    }

    @Test
    public void testEquals() {
        boolean expected = true;
        boolean actual = "uiae".equals("uiae");
        assertEquals(expected, actual);
    }
}
