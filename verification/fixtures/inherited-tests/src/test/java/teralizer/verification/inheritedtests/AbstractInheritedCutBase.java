package teralizer.verification.inheritedtests;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public abstract class AbstractInheritedCutBase {
    protected InheritedCut cut;

    @Before
    public void setUp() {
        cut = createCut();
    }

    @Test
    public void inheritedIncrementGeneralizes() {
        assertEquals(3, cut.increment(2));
    }

    protected InheritedCut createCut() {
        return new InheritedCut();
    }
}
