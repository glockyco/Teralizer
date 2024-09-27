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
}
