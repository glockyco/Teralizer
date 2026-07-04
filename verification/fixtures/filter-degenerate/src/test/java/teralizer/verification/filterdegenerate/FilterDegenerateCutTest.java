package teralizer.verification.filterdegenerate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FilterDegenerateCutTest {
    @Test
    public void unplannedModuloClauseFallsBackToTheResidualFilter() {
        assertEquals(43, new FilterDegenerateCut().moduloResidue(42));
    }
}
