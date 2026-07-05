package teralizer.verification.allrefused;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AllRefusedCutTest {
    @Test
    public void everyGeneralizationIsLicenseRefused() {
        assertEquals(1, new AllRefusedCut().branchSelectedInt(7));
    }
}
