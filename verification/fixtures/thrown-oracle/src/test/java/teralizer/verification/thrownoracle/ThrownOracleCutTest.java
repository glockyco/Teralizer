package teralizer.verification.thrownoracle;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ThrownOracleCutTest {
    @Test
    public void cleanEvidenceThrowIsLicensed() {
        assertThrows(IllegalArgumentException.class, () -> new ThrownOracleCut().cleanThrow(1));
    }

    @Test
    public void concretizedStringBranchThrowIsRefused() {
        assertThrows(IllegalStateException.class, () -> new ThrownOracleCut().concretizedThrow("pin", 1));
    }
}
