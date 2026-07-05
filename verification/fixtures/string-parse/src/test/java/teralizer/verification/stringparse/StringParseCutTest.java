package teralizer.verification.stringparse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringParseCutTest {
    @Test
    public void parseThenDoubleTakesParsingSeed() {
        assertEquals(84, StringParseCut.parseThenDouble("42"));
    }

    @Test
    public void parseOrDefaultCatchesFailingSeed() {
        assertEquals(-1, StringParseCut.parseOrDefault("nope"));
    }
}
