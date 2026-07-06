package teralizer.verification.hamcrest;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class HamcrestEqualityCutTest {
    @Test
    public void isMatcherComparesReturnedValue() {
        int expected = 3;

        assertThat(new HamcrestEqualityCut().shift(2), is(expected));
    }

    @Test
    public void equalToMatcherComparesReturnedValue() {
        int expected = 4;

        assertThat(new HamcrestEqualityCut().shift(3), equalTo(expected));
    }
}
