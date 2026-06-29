package teralizer.processing.diagnostics;

import com.google.gson.Gson;
import net.jqwik.api.Example;
import org.junit.Assert;

public class JqwikDiagnosticOutcomeTest {

    // Pins the contract between the keys the generated recorder writes and the fields
    // collection reads. The JSON mirrors jqwik-value-recorder.vm's writeOutcome output.
    @Example
    void parsesLimitedOutcomeSidecar() {
        String json = "{\n"
            + "  \"executionId\": \"exec-7\",\n"
            + "  \"projectId\": 3,\n"
            + "  \"generalizationId\": 14,\n"
            + "  \"variant\": \"NAIVE_1000_TRIES\",\n"
            + "  \"testCaseName\": \"isAsciiPrintable\",\n"
            + "  \"diagnosticsMode\": \"PERSISTED\",\n"
            + "  \"rawStatus\": \"FAILED\",\n"
            + "  \"finalStatus\": \"SUCCESSFUL\",\n"
            + "  \"diagnosticKind\": \"LIMITED_TOO_MANY_FILTER_MISSES\",\n"
            + "  \"throwableType\": \"net.jqwik.api.TooManyFilterMissesException\",\n"
            + "  \"throwableMessage\": \"missed more than 10000 times\",\n"
            + "  \"tries\": 179,\n"
            + "  \"checks\": 178,\n"
            + "  \"distinctTuples\": 32,\n"
            + "  \"distinctNewTuples\": 31,\n"
            + "  \"seed\": \"0\",\n"
            + "  \"valueLogPath\": \"jqwik-data/executions/exec-7/14.NAIVE_1000_TRIES.values.tsv\"\n"
            + "}\n";

        JqwikDiagnosticOutcome outcome = JqwikDiagnosticOutcome.fromJson(new Gson(), json);

        Assert.assertEquals("exec-7", outcome.executionId);
        Assert.assertEquals(Long.valueOf(14), outcome.generalizationId);
        Assert.assertEquals("isAsciiPrintable", outcome.testCaseName);
        Assert.assertEquals("FAILED", outcome.rawStatus);
        Assert.assertEquals("SUCCESSFUL", outcome.finalStatus);
        Assert.assertEquals("LIMITED_TOO_MANY_FILTER_MISSES", outcome.diagnosticKind);
        Assert.assertEquals("net.jqwik.api.TooManyFilterMissesException", outcome.throwableType);
        Assert.assertEquals(Integer.valueOf(179), outcome.tries);
        Assert.assertEquals(Integer.valueOf(178), outcome.checks);
        Assert.assertEquals(Integer.valueOf(32), outcome.distinctTuples);
        Assert.assertEquals(Integer.valueOf(31), outcome.distinctNewTuples);
        Assert.assertEquals("0", outcome.seed);
        Assert.assertEquals("jqwik-data/executions/exec-7/14.NAIVE_1000_TRIES.values.tsv", outcome.valueLogPath);
    }
}
