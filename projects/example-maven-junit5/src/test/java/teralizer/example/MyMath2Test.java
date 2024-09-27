package teralizer.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MyMath2Test {

    @Test
    public void testAbsPositive() {
        int input = 5;
        int expected = 5;
        int actual = new MyMath().abs(input);
        assertEquals(expected, actual);
    }

    @Test
    public void testAbsNegative() {
        int input = -10;
        int expected = 10;
        int actual = new MyMath().abs(input);
        assertEquals(expected, actual);
    }
}
