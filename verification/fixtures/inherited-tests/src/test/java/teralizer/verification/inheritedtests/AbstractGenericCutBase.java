package teralizer.verification.inheritedtests;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public abstract class AbstractGenericCutBase<T> {
    protected T value;

    @Test
    public void inheritedGenericIsExcluded() {
        T local = value;
        assertNull(local);
    }
}
