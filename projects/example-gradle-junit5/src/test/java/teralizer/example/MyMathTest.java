package teralizer.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ArithmeticException;
import java.lang.IllegalArgumentException;

import org.junit.jupiter.api.function.Executable;

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

    @Test
    public void testAddByte() {
        byte x = 1;
        byte y = 1;
        int expected = 2;
        MyMath math = new MyMath();
        byte actual = math.addByte(x, y);
        assertEquals(expected, actual);
    }

    @Test
    public void testSubtractShort() {
        short x = 2;
        short y = 1;
        short expected = 1;
        MyMath math = new MyMath();
        short actual = math.subtractShort(x, y);
        assertEquals(expected, actual);
    }

    @Test
    public void testMultiplyLong() {
        long x = 2L;
        long y = 3L;
        long expected = 6L;
        MyMath math = new MyMath();
        long actual = math.multiplyLong(x, y);
        assertEquals(expected, actual);
    }

    @Test
    public void testDivideFloat() {
        float x = 6.0f;
        float y = 3.0f;
        float expected = 2.0f;
        MyMath math = new MyMath();
        float actual = math.divideFloat(x, y);
        assertEquals(expected, actual);
    }

    @Test
    public void testDivideFloatException() {
        float x = 6.0f;
        float y = 0.0f;
        MyMath math = new MyMath();
        assertThrows(ArithmeticException.class, () -> math.divideFloat(x, y));
    }

    @Test
    public void testReducePositive() {
        MyMath math = new MyMath();
        int expected = 4;

        int actual = math.reducePositive(5);

        assertEquals(expected, actual);
    }

    @Test
    public void testReduceNegative() {
        MyMath math = new MyMath();

        assertThrows(IllegalArgumentException.class, () -> math.reducePositive(-1));
    }

    @Test
    public void testReduceNegative1() {
        MyMath math = new MyMath();

        assertThrows(IllegalArgumentException.class, () -> {
            math.reducePositive(-1);
        });
    }

    @Test
    public void testReduceNegative2() {
        MyMath math = new MyMath();

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                math.reducePositive(-1);
            }
        });
    }

    @Test
    public void testReduceNegative3() {
        MyMath math = new MyMath();

        Executable test = new Executable() {
            @Override
            public void execute() throws Throwable {
                math.reducePositive(-1);
            }
        };

        assertThrows(IllegalArgumentException.class, test);
    }

    @Test
    public void testReduceNegative4() {
        MyMath math = new MyMath();

        Executable test = () -> math.reducePositive(-1);

        assertThrows(IllegalArgumentException.class, test);
    }

    @Test
    public void testCastToInt() {
        MyMath math = new MyMath();
        int expected = 4;

        int actual = math.castToInt(4.3f);

        assertEquals(expected, actual);
    }

    @Test
    public void testMultiple() {
        for (int i = 0; i < 10; i++) {
            int actual = new MyMath().abs(i);
            assertEquals(i, actual);
        }
    }
}
