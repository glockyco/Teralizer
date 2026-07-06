package teralizer.verification.voidthrowsoracle;

import static org.junit.Assert.fail;

import org.junit.Test;

public class VoidThrowsOracleCutTest {
    @Test
    public void voidMutThrowsOnTheSeedPath() {
        try {
            new VoidThrowsOracleCut().requireNegative(7);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }
}
