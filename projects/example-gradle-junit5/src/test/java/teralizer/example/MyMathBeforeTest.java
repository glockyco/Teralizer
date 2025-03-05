package teralizer.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MathParamResolver.class)
public class MyMathBeforeTest {
    static MyMath math;

    @BeforeEach
    void setup(String a) {
        // Setup MyMath
        math = new MyMath();
    }

    @BeforeAll
    static void init() {
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
