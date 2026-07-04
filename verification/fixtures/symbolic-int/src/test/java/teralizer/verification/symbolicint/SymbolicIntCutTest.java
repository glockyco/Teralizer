package teralizer.verification.symbolicint;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SymbolicIntCutTest {
    @Test
    public void incrementsPositiveInput() {
        assertEquals(3, new SymbolicIntCut().increment(2));
    }
}
