import org.junit.Test;
import teralizer.example.MyMath;

import static org.junit.Assert.*;

public class MyUnnamedPackageTest {

    @Test
    public void test() {
        MyMath math = new MyMath();
        int actual = math.add(1, 2);
        assertEquals(3, actual);
    }
}
