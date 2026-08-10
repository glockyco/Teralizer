package teralizer.generalization;

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
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.SYMBOLIC, Boolean.FALSE, names("x"), Collections.emptySet(), 3, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void constantOutputWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.CONSTANT, Boolean.FALSE, names("x"), Collections.emptySet(), 1, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEmptyPathConditionWidensBecauseUnconditionalThrowAppliesToEveryInput() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.EXCEPTION, Boolean.FALSE, names("x"), Collections.emptySet(), 0, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEveryWidenedParameterNamedAndNoConcretizationEventsWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.EXCEPTION, Boolean.FALSE, names("left", "right"), names("left", "right", "temp"), 0, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEventsAndNoPostConcretizationRiskWidensWhenPathCoversWidenedParameters() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("left", "right"),
            names("left", "right", "temp"),
            7,
            Boolean.FALSE
        );

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEventsAndNoPostConcretizationRiskRefusesUncoveredWidenedParameters() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("left", "right"),
            names("left"),
            7,
            Boolean.FALSE
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEventsAndPostConcretizationRiskIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("x"),
            Collections.emptySet(),
            7,
            Boolean.TRUE
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithEventsAndUnknownPostConcretizationRiskIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("x"),
            Collections.emptySet(),
            7,
            null
        );

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void exceptionOutputWithoutEventsIgnoresPostConcretizationRisk() {
        WideningLicense.Verdict covered = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("x"),
            names("x"),
            0,
            Boolean.TRUE
        );
        WideningLicense.Verdict uncovered = WideningLicense.evaluate(
            OutputSpecClass.EXCEPTION,
            Boolean.FALSE,
            names("left", "right"),
            names("left"),
            0,
            Boolean.FALSE
        );

        Assert.assertTrue(covered.allowsWidening());
        Assert.assertNull(covered.getExclusionInfo());
        Assert.assertFalse(uncovered.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, uncovered.getExclusionInfo());
    }

    @Example
    void exceptionOutputMissingAWidenedParameterInPathConditionIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.EXCEPTION, Boolean.FALSE, names("left", "right"), names("left"), 0, null);

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithEveryWidenedParameterNamedAndNoConcretizationEventsWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("left", "right"), names("left", "right", "temp"), 0, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithEmptyPathConditionIsRefused() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("flag"), Collections.emptySet(), 0, null);

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteBooleanWithConcretizationEventsIsRefusedEvenWhenPostConcretizationRiskIsFalse() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("flag"), names("flag"), 1, Boolean.FALSE);

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteNonBooleanLiteralWithPathConditionEvidenceWidens() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("x"), names("x"), 0, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteFieldReadBooleanIsRefusedDespitePathConditionEvidence() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.FALSE, names("flag"), names("flag"), 0, null);

        Assert.assertFalse(verdict.allowsWidening());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, verdict.getExclusionInfo());
    }

    @Example
    void nullConcreteLiteralSignalAllowsAnyReturnType() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("flag"), names("flag"), 0, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    @Example
    void eachRefusalBranchHasItsOwnStableCode() {
        Assert.assertEquals(
            WideningLicense.EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK,
            WideningLicense.evaluate(OutputSpecClass.EXCEPTION, Boolean.FALSE, names("x"), Collections.emptySet(), 1, null).getWideningRefusalCode()
        );
        Assert.assertEquals(
            WideningLicense.EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS,
            WideningLicense.evaluate(OutputSpecClass.EXCEPTION, Boolean.FALSE, names("x"), names("other"), 0, null).getWideningRefusalCode()
        );
        Assert.assertEquals(
            WideningLicense.NULL_CONCRETE_OUTPUT_NOT_LITERAL,
            WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.FALSE, names("x"), names("x"), 0, null).getWideningRefusalCode()
        );
        Assert.assertEquals(
            WideningLicense.NULL_CONCRETE_CONCRETIZATION_EVENTS,
            WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("x"), names("x"), 1, null).getWideningRefusalCode()
        );
        Assert.assertEquals(
            WideningLicense.NULL_CONCRETE_PARAMETERS_EMPTY,
            WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, Collections.emptySet(), names("x"), 0, null).getWideningRefusalCode()
        );
        Assert.assertEquals(
            WideningLicense.NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS,
            WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("x", "y"), names("x"), 0, null).getWideningRefusalCode()
        );
    }

    @Example
    void nullConcretizationEventColumnIsTreatedAsZero() {
        WideningLicense.Verdict verdict = WideningLicense.evaluate(OutputSpecClass.NULL_CONCRETE, Boolean.TRUE, names("flag"), names("flag"), null, null);

        Assert.assertTrue(verdict.allowsWidening());
        Assert.assertNull(verdict.getExclusionInfo());
    }

    private static Set<String> names(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }
}
