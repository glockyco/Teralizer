package teralizer.verification.minvalueseeds;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinValueSeedsCutTest {
    @Test
    public void longMinValueSeedCompilesInTheGeneratedSupplier() {
        assertEquals(Long.MIN_VALUE, new MinValueSeedsCut().longIdentity(Long.MIN_VALUE));
    }

    @Test
    public void integerMinValueSeedCompilesInTheGeneratedSupplier() {
        assertEquals(Integer.MIN_VALUE, new MinValueSeedsCut().integerIdentity(Integer.MIN_VALUE));
    }

    @Test
    public void shortMinValueSeedCompilesInTheGeneratedSupplier() {
        assertEquals((int) Short.MIN_VALUE, new MinValueSeedsCut().shortIdentity(Short.MIN_VALUE));
    }
}
