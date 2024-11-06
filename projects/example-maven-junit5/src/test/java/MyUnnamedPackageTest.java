import org.junit.jupiter.api.Test;
import teralizer.example.MyMath;

import static org.junit.jupiter.api.Assertions.*;

public class MyUnnamedPackageTest {

    @Test
    public void test() {
        MyMath math = new MyMath();
        int actual = math.add(1, 2);
        assertEquals(3, actual);
    }
}
