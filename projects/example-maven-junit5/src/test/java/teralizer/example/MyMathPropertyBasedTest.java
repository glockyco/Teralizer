package teralizer.example;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

public class MyMathPropertyBasedTest {

    @Property
    public void testAdd(@ForAll int x, @ForAll int y) {
        int expected = x + y;
        int actual = new MyMath().add(x, y);
        assertEquals(expected, actual);
    }

    @Property
    public void testAbsPositive(@ForAll @IntRange(min = 0) int input) {
        int expected = input;
        int actual = new MyMath().abs(input);
        assertEquals(expected, actual);
    }

    @Property
    public void testAbsNegative(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int input) {
        int expected = -input;
        int actual = new MyMath().abs(input);
        assertEquals(expected, actual);
    }
}