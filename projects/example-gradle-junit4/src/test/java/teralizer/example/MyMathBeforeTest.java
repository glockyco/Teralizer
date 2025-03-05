package teralizer.example;

import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;

import static org.junit.Assert.*;

public class MyMathBeforeTest {
    static MyMath math;

    @Before
    public void setup() {
        // Setup MyMath
        math = new MyMath();
    }

    @BeforeClass
    public static void init() {
        math = new MyMath();
    }

    @Test
    public void testAdd() {
        int x = 1;
        int y = 1;
        int expected = 2;
        int actual = math.add(x, y);
        assertEquals(expected, actual);
    }
}
