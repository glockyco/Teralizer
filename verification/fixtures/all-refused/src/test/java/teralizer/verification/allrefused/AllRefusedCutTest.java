package teralizer.verification.allrefused;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AllRefusedCutTest {
    @Test
    public void everyGeneralizationIsLicenseRefused() {
        assertEquals(Boolean.TRUE, new AllRefusedCut().passThrough(true));
    }
}
