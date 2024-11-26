package teralizer.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MyMathTest {

    @Test
    public void testAdd() {
        int x = 1;
        int y = 1;
        int expected = 2;
        MyMath math = new MyMath();
        int actual = math.add(x, y);
        assertEquals(expected, actual);
    }

    @Test
    public void testIsZero() {
        int x = 0;
        boolean expected = true;
        MyMath math = new MyMath();
        boolean actual = math.isZero(x);
        assertEquals(expected, actual);
    }

    @Test
    public void testIsEqual() {
        int x = 0;
        int y = 0;
        boolean expected = true;
        MyMath math = new MyMath();
        boolean actual = math.isEqual(x, y);
        assertEquals(expected, actual);
    }
}
