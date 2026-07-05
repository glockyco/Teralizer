package teralizer.verification.charwhitespace;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CharWhitespaceCutTest {
    @Test
    public void whitespacePredicateBooleanIsLicensedByPathCondition() {
        assertTrue(new CharWhitespaceCut().branchesOnWhitespace('\n'));
    }
}
