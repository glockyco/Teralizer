package teralizer.verification.boxedreturns;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BoxedReturnsCutTest {
    @Test
    public void integerValueOfKeepsTheComputedReturnSymbolic() {
        assertEquals(Integer.valueOf(131), new BoxedReturnsCut().boxedInteger(130));
    }

    @Test
    public void longValueOfKeepsTheComputedReturnSymbolic() {
        assertEquals(Long.valueOf(131L), new BoxedReturnsCut().boxedLong(130L));
    }

    @Test
    public void comparisonBooleanValueOfRecordsTheObservedReturnShape() {
        assertEquals(Boolean.TRUE, new BoxedReturnsCut().boxedBoolean(1));
    }
}
