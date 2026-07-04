package teralizer.verification.oldsurefire;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OldSurefireCutTest {
    @Test
    public void oldSurefireFixtureStillRunsGeneratedJqwikTests() {
        assertEquals(3, new OldSurefireCut().increment(2));
    }
}
