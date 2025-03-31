package teralizer.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @RepeatedTest(5)
    public void testAddRepeated() {
        int x = 2;
        int y = 3;
        int expected = 5;
        int actual = math.add(x, y);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0",
        "1, 1, 2",
        "5, 3, 8",
        "-1, 1, 0",
        "10, -5, 5"
    })
    public void testAddParameterized(int x, int y, int expected) {
        int actual = math.add(x, y);
        assertEquals(expected, actual);
    }
}
