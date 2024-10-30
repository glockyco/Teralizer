package teralizer.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class MyStringTest {

    @Test
    public void testFail() {
        assertTrue(false);
    }

    @Test
    public void testContains() {
        boolean expected = true;
        boolean actual = MyString.contains("uiae", "uiae");
        assertEquals(expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testStartsWith() {
        boolean expected = true;
        boolean actual = MyString.startsWith("uiae", "uiae");
        assertEquals(expected, actual);
    }

    @Test
    public void testEndsWith() {
        boolean expected = true;
        boolean actual = MyString.endsWith("uiae", "uiae");
        assertEquals(expected, actual);
    }

//    @Test
//    public void testToUppercase() {
//        String input = "uiae";
//        String expected = "UIAE";
//        String actual = MyString.toUpperCase(input);
//        assertEquals(expected, actual);
//    }
}
