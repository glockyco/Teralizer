package teralizer.spoon.generalization;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.jpf.OutputSpecClassifier.OutputSpecClass;

public class WideningLicenseTest {
    @Example
    void symbolicOutputWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.SYMBOLIC,
            "long",
            names("x"),
            Collections.emptySet(),
            3
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void constantOutputWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.CONSTANT,
            "int",
            names("x"),
            Collections.emptySet(),
            1
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithEveryWidenedParameterNamedAndNoConcretizationEventsWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "boolean",
            names("left", "right"),
            names("left", "right", "temp"),
            0
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithEmptyPathConditionIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "boolean",
            names("flag"),
            Collections.emptySet(),
            0
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithConcretizationEventsIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "boolean",
            names("flag"),
            names("flag"),
            1
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcretePrimitiveNonBooleanIsRefusedEvenWithPathConditionEvidence() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "int",
            names("x"),
            names("x"),
            0
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcretePrimitiveNonBooleanIsRefusedWithoutPathConditionEvidence() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "int",
            names("x"),
            Collections.emptySet(),
            0
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void boxedBooleanIsTreatedAsBoolean() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "java.lang.Boolean",
            names("flag"),
            names("flag"),
            0
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void nullConcretizationEventColumnIsTreatedAsZero() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.NULL_CONCRETE,
            "boolean",
            names("flag"),
            names("flag"),
            null
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    private static Set<String> names(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }
}
