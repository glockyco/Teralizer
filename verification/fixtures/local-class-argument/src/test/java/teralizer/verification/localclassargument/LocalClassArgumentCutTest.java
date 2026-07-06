package teralizer.verification.localclassargument;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalClassArgumentCutTest {
    @Test
    public void localClassArgumentCrossesTheWrapperBoundary() {
        class Marker {
        }
        assertTrue(new LocalClassArgumentCut().isLarge(new Marker(), 42));
    }
}
