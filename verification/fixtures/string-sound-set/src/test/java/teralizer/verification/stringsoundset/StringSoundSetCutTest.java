package teralizer.verification.stringsoundset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringSoundSetCutTest {
    @Test
    public void equalityAgainstAConstantIsSound() {
        assertTrue(new StringSoundSetCut().equalsFoo("foo"));
    }

    @Test
    public void lengthPredicateStaysResidualAndWidens() {
        assertTrue(new StringSoundSetCut().hasNonNegativeLength("bar"));
    }

    @Test
    public void isEmptyIsModeledAsAStringEquality() {
        assertTrue(new StringSoundSetCut().isEmptyValue(""));
    }

    @Test
    public void indexOfProducesAnUnsupportedDerivedSymbol() {
        assertEquals(-1, new StringSoundSetCut().indexOfX("foo"));
    }

    @Test
    public void unsupportedCompareToIsExcluded() {
        assertEquals(0, new StringSoundSetCut().compareToFoo("foo"));
    }
}
