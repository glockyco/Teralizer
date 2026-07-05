package teralizer.processing.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.JqwikExecutionRunRecord;
import org.jooq.generated.tables.records.JqwikPropertyExecutionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;

/**
 * Imports one generalization's jqwik outcome sidecar into {@code jqwik_property_execution},
 * keyed against the latest registered execution run. A missing sidecar is recorded explicitly
 * as {@code DIAGNOSTIC_MISSING} so a FULL outcome is never inferred from an absent row.
 */
public final class JqwikDiagnosticsImporter {

    private JqwikDiagnosticsImporter() {
    }

    public static void importOutcome(
        DSLContext create,
        Gson gson,
        ProjectRecord projectRecord,
        long projectId,
        long generalizationId,
        String generalizationMethodName,
        ProcessingStage stage,
        String variant
    ) {
        JqwikExecutionRunRecord runRecord = create
            .selectFrom(Tables.JQWIK_EXECUTION_RUN)
            .where(Tables.JQWIK_EXECUTION_RUN.PROJECT_ID.eq(projectId))
            .and(Tables.JQWIK_EXECUTION_RUN.VARIANT.eq(variant))
            .and(Tables.JQWIK_EXECUTION_RUN.EXECUTION_KIND.eq("JUNIT"))
            .orderBy(Tables.JQWIK_EXECUTION_RUN.ID.desc())
            .limit(1)
            .fetchOne();

        if (runRecord == null) {
            // No execution run was registered, so there is nothing to key diagnostics against.
            return;
        }

        Long junitTestReportId = create
            .select(Tables.JUNIT_TEST_REPORT.ID)
            .from(Tables.JUNIT_TEST_REPORT)
            .where(Tables.JUNIT_TEST_REPORT.GENERALIZATION_ID.eq(generalizationId))
            .and(Tables.JUNIT_TEST_REPORT.STAGE.eq(stage))
            .orderBy(Tables.JUNIT_TEST_REPORT.ID.desc())
            .limit(1)
            .fetchOne(Tables.JUNIT_TEST_REPORT.ID);

        Path outcomePath = resolveSidecarPath(projectRecord, projectId, generalizationId, variant, runRecord.getExecutionId());

        JqwikPropertyExecutionRecord record = create.newRecord(Tables.JQWIK_PROPERTY_EXECUTION);
        record.setJqwikExecutionRunId(runRecord.getId());
        record.setProjectId(projectId);
        record.setGeneralizationId(generalizationId);
        record.setJunitTestReportId(junitTestReportId);
        record.setDiagnosticSidecarPath(outcomePath.toString());

        if (Files.exists(outcomePath)) {
            try {
                String json = new String(Files.readAllBytes(outcomePath), StandardCharsets.UTF_8);
                JqwikDiagnosticOutcome outcome = JqwikDiagnosticOutcome.fromJson(gson, json);
                record.setTestCaseName(stripNul(outcome.testCaseName != null ? outcome.testCaseName : generalizationMethodName));
                record.setDiagnosticKind(stripNul(outcome.diagnosticKind));
                record.setRawStatus(stripNul(outcome.rawStatus));
                record.setFinalStatus(stripNul(outcome.finalStatus));
                record.setThrowableType(stripNul(outcome.throwableType));
                record.setThrowableMessage(stripNul(outcome.throwableMessage));
                record.setTries(outcome.tries);
                record.setChecks(outcome.checks);
                record.setDistinctTuples(outcome.distinctTuples);
                record.setDistinctNewTuples(outcome.distinctNewTuples);
                record.setSeed(stripNul(outcome.seed));
                record.setSelectedValueLogPath(outcomePath.resolveSibling(generalizationId + "." + variant + ".values.tsv").toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Absence of a sidecar is recorded explicitly so FULL is never inferred from a missing row.
            record.setTestCaseName(generalizationMethodName);
            record.setDiagnosticKind("DIAGNOSTIC_MISSING");
            record.setRawStatus("UNKNOWN");
            record.setFinalStatus("UNKNOWN");
        }

        record.store();
    }

    private static String stripNul(String value) {
        // Postgres TEXT columns reject NUL (0x00); a generated char/string value (e.g. CharUtils
        // isAscii over char 0) can carry it into a throwable message. Strip it before insert.
        return value == null ? null : value.replace("\u0000", "");
    }

    private static Path resolveSidecarPath(
        ProjectRecord projectRecord,
        long projectId,
        long generalizationId,
        String variant,
        String executionId
    ) {
        Path relativePath = projectRecord.getDataPath()
            .resolve("project-id-" + projectId)
            .resolve("jqwik-data")
            .resolve("executions")
            .resolve(executionId)
            .resolve(generalizationId + "." + variant + ".outcome.json");

        // The recorder runs with the project root as its working directory, so a data path that
        // is relative resolves against the root there; mirror that here when locating the file.
        Path rootedPath = projectRecord.getRootPath().resolve(relativePath);
        return Files.exists(rootedPath) ? rootedPath : relativePath;
    }
}
