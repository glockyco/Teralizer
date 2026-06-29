package teralizer.processing.diagnostics;

import com.google.gson.Gson;

/**
 * Parsed form of a generated test's jqwik outcome sidecar
 * ({@code <generalization>.<variant>.outcome.json}). Field names match the JSON keys the
 * generated recorder writes, so Gson maps them directly.
 */
public class JqwikDiagnosticOutcome {

    public String executionId;
    public Long projectId;
    public Long generalizationId;
    public String variant;
    public String testCaseName;
    public String diagnosticsMode;
    public String rawStatus;
    public String finalStatus;
    public String diagnosticKind;
    public String throwableType;
    public String throwableMessage;
    public Integer tries;
    public Integer checks;
    public Integer distinctTuples;
    public Integer distinctNewTuples;
    public String seed;
    public String valueLogPath;

    public static JqwikDiagnosticOutcome fromJson(Gson gson, String json) {
        return gson.fromJson(json, JqwikDiagnosticOutcome.class);
    }
}
