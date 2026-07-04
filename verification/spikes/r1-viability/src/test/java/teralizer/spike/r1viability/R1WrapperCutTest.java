package teralizer.spike.r1viability;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class R1WrapperCutTest {
    @Test
    public void directAddsLiftedInputs() {
        assertEquals(7, R1WrapperCut.direct(3, 4));
    }

    @Test
    public void chainProjectInspectorReturnsBoxValue() {
        assertEquals(9, R1WrapperCut.chainProjectInspector(9));
    }

    @Test
    public void chainLibrarySizeReturnsBuiltListSize() {
        assertEquals(3, R1WrapperCut.chainLibrarySize(3));
    }

    @Test
    public void operatorCompositeCallsComparesLiftedInputs() {
        assertTrue(R1WrapperCut.operatorCompositeCalls(4, 1));
    }

    @Test
    public void compareToComparisonChecksPairOrdering() {
        assertTrue(R1WrapperCut.compareToComparison(2));
    }

    @Test
    public void ctorOnlyEqualityUsesConstructedPairValues() {
        assertFalse(R1WrapperCut.ctorOnlyEquality(1, 4));
    }

    @Test
    public void castWrappedCallCastsHelperResult() {
        assertEquals(12L, R1WrapperCut.castWrappedCall(6));
    }

    @Test
    public void arithmeticCompositeAddsWrappedCalls() {
        assertEquals(14, R1WrapperCut.arithmeticComposite(3, 4));
    }

    @Test
    public void chainTwoHopsReturnsTwiceValue() {
        assertEquals(10, R1WrapperCut.chainTwoHops(5));
    }
}
