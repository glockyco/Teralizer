package teralizer.verification.literalreturns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiteralReturnsCutTest {
    @Test
    public void computedBooleanReturn() {
        assertTrue(new LiteralReturnsCut().computedBoolean(7));
    }

    @Test
    public void nonBooleanLiteralReturn() {
        assertEquals(42, new LiteralReturnsCut().literalInt(7));
    }

    @Test
    public void fieldReadBooleanReturn() {
        assertTrue(new LiteralReturnsCut().fieldBoolean(7));
    }

    @Test
    public void arithmeticReturn() {
        assertEquals(5, new LiteralReturnsCut().arithmetic(100));
    }
}
