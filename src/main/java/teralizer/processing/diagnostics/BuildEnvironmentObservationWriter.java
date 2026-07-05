package teralizer.processing.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.BuildEnvironmentObservationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectType;
import teralizer.util.Configuration;

public final class BuildEnvironmentObservationWriter {

    private static final Pattern XML_SOURCE = Pattern.compile("<maven\\.compiler\\.source>([^<]+)</maven\\.compiler\\.source>|<source>([^<]+)</source>");
    private static final Pattern XML_TARGET = Pattern.compile("<maven\\.compiler\\.target>([^<]+)</maven\\.compiler\\.target>|<target>([^<]+)</target>");
    private static final Pattern XML_RELEASE = Pattern.compile("<maven\\.compiler\\.release>([^<]+)</maven\\.compiler\\.release>|<release>([^<]+)</release>");
    private static final Pattern GRADLE_SOURCE = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?([^'\"\\s]+)");
    private static final Pattern GRADLE_TARGET = Pattern.compile("targetCompatibility\\s*=\\s*['\"]?([^'\"\\s]+)");

    private BuildEnvironmentObservationWriter() {
    }

    public static void record(DSLContext create, ProjectRecord projectRecord, ProcessingStage stage) {
        BuildEnvironmentObservationRecord record = create.newRecord(Tables.BUILD_ENVIRONMENT_OBSERVATION);
        record.setProjectId(projectRecord.getId());
        record.setStage(stage.name());
        record.setBuildTool(buildTool(projectRecord.getType()));

        String buildFile = readBuildFile(projectRecord, stage);
        record.setCompilerSource(firstMatch(buildFile, projectRecord.getType() == ProjectType.GRADLE ? GRADLE_SOURCE : XML_SOURCE));
        record.setCompilerTarget(firstMatch(buildFile, projectRecord.getType() == ProjectType.GRADLE ? GRADLE_TARGET : XML_TARGET));
        record.setCompilerRelease(projectRecord.getType() == ProjectType.GRADLE ? null : firstMatch(buildFile, XML_RELEASE));

        GeneratedSourceFeatures generated = generatedSourceFeatures(projectRecord);
        record.setGeneratedSourceRequiredLevel(generated.requiredLevel);
        record.setGeneratedUsesLambdas(generated.usesLambdas);
        record.setGeneratedUsesMethodReferences(generated.usesMethodReferences);
        record.setGeneratedUsesDiamond(generated.usesDiamond);
        record.store();
    }

    private static String buildTool(ProjectType type) {
        switch (type) {
            case MAVEN:
                return "MAVEN";
            case GRADLE:
                return "GRADLE";
            default:
                return "UNKNOWN";
        }
    }

    private static String readBuildFile(ProjectRecord projectRecord, ProcessingStage stage) {
        Path path;
        switch (projectRecord.getType()) {
            case MAVEN:
                path = projectRecord.getRootPath().resolve(stage == ProcessingStage.BUILD_PROJECT_GENERALIZED
                    ? Configuration.MAVEN_GENERALIZED_BUILD_FILE
                    : Configuration.MAVEN_CUSTOM_BUILD_FILE);
                break;
            case GRADLE:
                path = projectRecord.getRootPath().resolve(Configuration.GRADLE_CUSTOM_BUILD_FILE);
                break;
            default:
                return "";
        }
        try {
            return Files.exists(path) ? new String(Files.readAllBytes(path)) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                return matcher.group(i);
            }
        }
        return null;
    }

    private static GeneratedSourceFeatures generatedSourceFeatures(ProjectRecord projectRecord) {
        GeneratedSourceFeatures features = new GeneratedSourceFeatures();
        try {
            if (projectRecord.getTestSourcePath() != null && Files.exists(projectRecord.getTestSourcePath())) {
                Files.walk(projectRecord.getTestSourcePath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("Generalized"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> applySourceFeatures(features, path));
            }
        } catch (IOException e) {
            return features;
        }
        features.requiredLevel = features.usesLambdas || features.usesMethodReferences ? Configuration.GENERATED_TEST_LANGUAGE_LEVEL : null;
        return features;
    }

    private static void applySourceFeatures(GeneratedSourceFeatures features, Path path) {
        try {
            String source = new String(Files.readAllBytes(path));
            features.usesLambdas |= source.contains(" -> ");
            features.usesMethodReferences |= source.contains("::");
            features.usesDiamond |= source.contains("<>");
        } catch (IOException ignored) {
        }
    }

    private static final class GeneratedSourceFeatures {
        private String requiredLevel;
        private boolean usesLambdas;
        private boolean usesMethodReferences;
        private boolean usesDiamond;
    }
}
