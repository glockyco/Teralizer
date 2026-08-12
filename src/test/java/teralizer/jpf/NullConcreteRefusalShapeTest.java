package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Model;
import teralizer.generalization.WideningLicense;
import teralizer.generalization.WideningLicense.Verdict;
import teralizer.jpf.OutputSpecClassifier.OutputSpecClass;
import teralizer.transformer.JsonToModelTransformer;

/**
 * Pins which return shapes reach {@link WideningLicense} as {@link OutputSpecClass#NULL_CONCRETE},
 * and which of those the literal check then refuses.
 *
 * <p>Two shapes are worth stating outright. A field or array round trip keeps its expression, so
 * neither is a source of {@code NULL_CONCRETE}: jpf-core copies operand attributes onto the heap and
 * back. And {@code comparisonDirect} and {@code comparisonViaLocal} are the same Java, yet only the
 * first is widened, because the literal check reads the instruction executed immediately before the
 * return and a local store puts {@code ILOAD} there.
 */
class NullConcreteRefusalShapeTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "AttributeLossTarget";

    private static final class Outcome {
        private final OutputSpecClass specClass;
        private final boolean literal;
        private final Verdict verdict;

        Outcome(OutputSpecClass specClass, boolean literal, Verdict verdict) {
            this.specClass = specClass;
            this.literal = literal;
            this.verdict = verdict;
        }
    }

    private static Outcome run(Path workDir, String wrapper, String tested) {
        Path shapeDir;
        try {
            shapeDir = Files.createDirectories(workDir.resolve(wrapper));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            shapeDir,
            TARGET,
            TARGET + "." + wrapper + "(sym)",
            TARGET + "." + wrapper,
            PKG + "AttributeLossCut." + tested
        );
        String spec = capture.getOutputSpecificationJson();
        Model model = spec == null ? null : new JsonToModelTransformer().transform(spec);
        OutputSpecClass specClass = OutputSpecClassifier.classify(CapturedOutput.Kind.RETURNED_VALUE, model);
        Verdict verdict = WideningLicense.evaluate(
            specClass,
            capture.getOutputIsLiteral(),
            Collections.singleton("value"),
            Collections.singleton("value"),
            0,
            Boolean.FALSE
        );
        return new Outcome(specClass, capture.getOutputIsLiteral(), verdict);
    }

    private static void assertWiden(Outcome outcome, OutputSpecClass expectedClass) {
        assertEquals(expectedClass, outcome.specClass);
        assertEquals(null, outcome.verdict.getWideningRefusalCode(), "must not be refused");
    }

    private static void assertRefused(Outcome outcome) {
        assertEquals(OutputSpecClass.NULL_CONCRETE, outcome.specClass);
        assertEquals(false, outcome.literal, "refusal requires the literal check to fail");
        assertEquals(
            WideningLicense.NULL_CONCRETE_OUTPUT_NOT_LITERAL,
            outcome.verdict.getWideningRefusalCode()
        );
    }

    @Test
    void literalReturnIsRecoveredFromThePathCondition(@TempDir Path workDir) {
        assertWiden(run(workDir, "literalDirectWrapper", "literalDirect"), OutputSpecClass.NULL_CONCRETE);
    }

    @Test
    void comparisonReturnedDirectlyIsRecovered(@TempDir Path workDir) {
        assertWiden(run(workDir, "comparisonDirectWrapper", "comparisonDirect"), OutputSpecClass.NULL_CONCRETE);
    }

    @Test
    void sameComparisonParkedInALocalIsRefused(@TempDir Path workDir) {
        assertRefused(run(workDir, "comparisonViaLocalWrapper", "comparisonViaLocal"));
    }

    @Test
    void arithmeticKeepsItsExpression(@TempDir Path workDir) {
        assertWiden(run(workDir, "arithmeticWrapper", "arithmetic"), OutputSpecClass.SYMBOLIC);
    }

    @Test
    void arithmeticThroughALocalKeepsItsExpression(@TempDir Path workDir) {
        assertWiden(run(workDir, "arithmeticViaLocalWrapper", "arithmeticViaLocal"), OutputSpecClass.SYMBOLIC);
    }

    @Test
    void fieldRoundTripKeepsItsExpression(@TempDir Path workDir) {
        assertWiden(run(workDir, "fieldRoundTripWrapper", "fieldRoundTrip"), OutputSpecClass.SYMBOLIC);
    }

    @Test
    void arrayRoundTripKeepsItsExpression(@TempDir Path workDir) {
        assertWiden(run(workDir, "arrayElementWrapper", "arrayElement"), OutputSpecClass.SYMBOLIC);
    }

    @Test
    void loopAccumulatorIsConcreteAndRefused(@TempDir Path workDir) {
        assertRefused(run(workDir, "loopAccumulatorWrapper", "loopAccumulator"));
    }

    /**
     * An array of literals read at an index taken from the input. The index is symbolic but the
     * elements are constants that never carried an attribute, so there is no expression to return.
     * What decides the outcome is the stored value, not that the subscript was an array.
     */
    @Test
    void arrayOfLiteralsHasNoExpressionToReturn(@TempDir Path workDir) {
        assertRefused(run(workDir, "arrayIndexedByInputWrapper", "arrayIndexedByInput"));
    }
}
