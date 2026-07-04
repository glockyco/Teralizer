package teralizer.verification.expressionslice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExpressionSliceCutTest {
    @Test
    public void directInvocationStillGeneralizes() {
        assertEquals(7, ExpressionSliceCut.add(3, 4));
    }

    @Test
    public void operatorCompositeOverCallsIsAdmitted() {
        assertTrue(ExpressionSliceCut.intCompare(4, 1) > 0);
    }

    @Test
    public void compareToComparisonIsAdmitted() {
        assertTrue(new Pair(2).compareTo(new Pair(5)) < 0);
    }

    @Test
    public void projectEqualityMethodIsAdmitted() {
        assertFalse(new Pair(1, 4).equalsPair(new Pair(1, 5)));
    }

    @Test
    public void castWrappedCallStaysSymbolic() {
        assertEquals(12L, (long) ExpressionSliceCut.timesTwo(6));
    }

    @Test
    public void arithmeticCompositeStaysSymbolic() {
        assertEquals(14, ExpressionSliceCut.timesTwo(3) + ExpressionSliceCut.timesTwo(4));
    }

    @Test
    public void projectChainStaysSymbolic() {
        assertEquals(10, Box.of(5).twice().value());
    }

    @Test
    public void libraryInspectorChainIsRefused() {
        assertEquals(3, ExpressionSliceCut.buildList(3).size());
    }
}
