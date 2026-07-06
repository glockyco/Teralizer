package teralizer.verification.narrowcastsite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NarrowCastSiteCutTest {
    @Test
    public void castByteSiteKeepsItsFormalType() {
        assertEquals(5, new NarrowCastSiteCut().clampToByte(100000, (byte) 5));
    }
}
