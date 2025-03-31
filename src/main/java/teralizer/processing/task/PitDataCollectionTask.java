package teralizer.processing.task;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.PitCoverageReportRecord;
import org.jooq.generated.tables.records.PitMutationReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import teralizer.processing.MutationStatus;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.MavenDependencyManager;
import teralizer.repository.SQLiteRepository;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PitDataCollectionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public PitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public PitDataCollectionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Path pitDataDirectory = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/pit-data");
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        this.executeMutationTesting(create);
        this.collectCoverageData(create, pitDataDirectory);
        this.collectMutationData(create, pitDataDirectory);
    }

    private void executeMutationTesting(DSLContext create) throws Exception {
        List<String> targetClasses;
        switch (this.getStage()) {
            case COLLECT_PIT_DATA_ORIGINAL:
                targetClasses = SQLiteRepository.fetchCoveredClasses(create, ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL, this.getVariant(), this.getProjectId());
                break;
            case COLLECT_PIT_DATA_INITIAL:
            case COLLECT_PIT_DATA_GENERALIZED:
                // Use the same targetClasses for initial + generalized PIT data
                // collection to ensure the results are directly comparable.
                targetClasses = SQLiteRepository.fetchCoveredClasses(create, ProcessingStage.COLLECT_JACOCO_DATA_INITIAL, null, this.getProjectId());
                break;
            default:
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
        // Filter anonymous classes, which are stored like: "com.example.MyClass.new MyInterface() {...}"
        // Passing targetClasses to PIT without this filter causes 'Illegal repetition' errors due to "...".
        // @TODO: Convert names of anonymous classes so they can be passed to PIT without errors.
        List<String> targetClassesFiltered = targetClasses.stream().filter(c -> !c.contains("...")).collect(Collectors.toList());

        if (targetClassesFiltered.isEmpty()) {
            throw new RuntimeException("Failed mutation testing. All classes of the project are excluded.");
        }

        List<String> targetTests;
        switch (this.getStage()) {
            case COLLECT_PIT_DATA_ORIGINAL:
            case COLLECT_PIT_DATA_INITIAL:
                targetTests = SQLiteRepository.fetchIncludedTestClasses(create, this.getProjectId());
                break;
            case COLLECT_PIT_DATA_GENERALIZED:
                List<String> targetGeneralizations = SQLiteRepository.fetchIncludedGeneralizedClasses(create, this.getVariant(), this.getProjectId());
                if (targetGeneralizations.isEmpty()) {
                    throw new RuntimeException("Failed mutation testing. All generalized tests of the project are excluded.");
                }
                targetTests = SQLiteRepository.fetchIncludedTestClasses(create, this.getProjectId());
                targetTests.addAll(targetGeneralizations);
                break;
            default:
                throw new RuntimeException("Unsupported processing stage " + this.getStage() + ".");
        }

        if (targetTests.isEmpty()) {
            throw new RuntimeException("Failed mutation testing. All tests of the project are excluded.");
        }

        List<String> command;
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot execute mutation testing for project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot execute mutation testing for project " + this.projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot execute mutation testing for project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = this.buildGradleCommand(targetClassesFiltered, targetTests);
                break;
            case MAVEN:
                command = this.buildMavenCommand(targetClassesFiltered, targetTests);
                break;
            default:
                throw new RuntimeException("Cannot execute mutation testing for project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }
        this.consoleCommand.execute(this.projectRecord.getRootPath(), command);
    }

    private void collectCoverageData(DSLContext create, Path dataDirectory) throws DocumentException, IOException {
        Path reportPath = this.projectRecord.getMutationReportsPath().resolve("linecoverage.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect coverage data. Report file '" + reportPath + "' does not exist.");
        }

        // Preserve the full raw data in the data directory:
        String stageName = this.getStage().getStep() + "-" + this.getStage();
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String fileName = stageName + variantName + "." + reportPath.getFileName().toString();
        dataDirectory.toFile().mkdirs();
        Files.copy(reportPath, dataDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        // Read (relevant parts of) the data and write it to the DB:
        Map<String, Integer> testIds = this.fetchTestIds(create);
        Map<String, Integer> generalizationIds = this.fetchGeneralizationIds(create);

        Document document = new SAXReader().read(reportPath.toFile());
        Element coverageElement = document.getRootElement();

        List<PitCoverageReportRecord> records = new ArrayList<>();
        for (Element blockElement : coverageElement.elements("block")) {
            String coveredClassQualifiedName = blockElement.attributeValue("classname");
            int coveredClassLastDotIndex = coveredClassQualifiedName.lastIndexOf('.');
            String coveredPackageName = coveredClassQualifiedName.substring(0, coveredClassLastDotIndex);
            String coveredClassName = coveredClassQualifiedName.substring(coveredClassLastDotIndex + 1);

            String coveredMethodSignature = blockElement.attributeValue("method");
            String[] methodParts = coveredMethodSignature.split("\\(", 2);
            String coveredMethodName = methodParts[0];
            String coveredMethodDescription = "(" + methodParts[1];

            int coveredBlockNumber = Integer.parseInt(blockElement.attributeValue("number"));

            Element testsElement = blockElement.element("tests");
            for (Element testElement : testsElement.elements("test")) {
                String name = testElement.attributeValue("name");
                String[] parts = name.split("/");

                String testClassQualifiedName = parts[1].replaceAll("^\\[(runner|class):(.*?)\\]$", "$2");
                int testClassLastDotIndex = testClassQualifiedName.lastIndexOf('.');
                String testPackageName = testClassLastDotIndex == -1 ? "" : testClassQualifiedName.substring(0, testClassLastDotIndex);
                String testClassName = testClassLastDotIndex == -1 ? testClassQualifiedName : testClassQualifiedName.substring(testClassLastDotIndex + 1);
                String testMethodName = parts[2].replaceAll("^\\[(test|method|property):(.*?)\\((.*)$", "$2");
                String testMethodQualifiedName = testClassQualifiedName + "." + testMethodName;

                boolean isParameterizedTest = testMethodQualifiedName.endsWith("]");
                if (isParameterizedTest) {
                    continue;
                }

                Integer testId = testIds.getOrDefault(testMethodQualifiedName, null);
                Integer generalizationId = generalizationIds.getOrDefault(testMethodQualifiedName, null);

                PitCoverageReportRecord record = create.newRecord(Tables.PIT_COVERAGE_REPORT);

                record.setProjectId(this.getProjectId());
                record.setTestId(testId);
                record.setGeneralizationId(generalizationId);

                record.setStep(this.getStage().getStep());
                record.setStage(this.getStage());
                record.setVariant(this.getVariant());

                record.setCoveredPackageName(coveredPackageName);
                record.setCoveredClassName(coveredClassName);
                record.setCoveredMethodName(coveredMethodName);
                record.setCoveredMethodDescription(coveredMethodDescription);
                record.setCoveredBlockNumber(coveredBlockNumber);

                record.setTestPackageName(testPackageName);
                record.setTestClassName(testClassName);
                record.setTestMethodName(testMethodName);

                if (testId == null && generalizationId == null) {
                    throw new RuntimeException("Failed to map coverage record to a test / generalization:\n" + record);
                }

                records.add(record);
            }
        }
        create.batchInsert(records).execute();
    }

    private Map<String, Integer> fetchTestIds(DSLContext create) {
        return create.select(
                // If some tests are executed multiple times, take the ID of the first execution.
                DSL.min(Tables.TEST.ID),
                // The qualified name is the same across all group elements, so take any.
                Tables.TEST.TEST_METHOD_QUALIFIED_NAME
            )
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.getProjectId()))
            .groupBy(Tables.TEST.TEST_METHOD_QUALIFIED_NAME)
            .fetch().stream().collect(Collectors.toMap(Record2::component2, Record2::component1));
    }

    private Map<String, Integer> fetchGeneralizationIds(DSLContext create) {
        return create.select(
                // If some generalizations are executed multiple times, take the ID of the first execution.
                DSL.min(Tables.GENERALIZATION.ID),
                // The qualified name is the same across all group elements, so take any.
                DSL.min(Tables.GENERALIZATION.METHOD_QUALIFIED_NAME)
            )
            .from(Tables.GENERALIZATION)
            .where(Tables.GENERALIZATION.PROJECT_ID.eq(this.getProjectId()))
            .and(Tables.GENERALIZATION.VARIANT.eq(this.getVariant()))
            .groupBy(Tables.GENERALIZATION.METHOD_QUALIFIED_NAME)
            .fetch().stream().collect(Collectors.toMap(Record2::component2, Record2::component1));
    }

    private void collectMutationData(DSLContext create, Path dataDirectory) throws DocumentException, IOException {
        Path reportPath = this.projectRecord.getMutationReportsPath().resolve("mutations.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect mutation data. Report file '" + reportPath + "' does not exist.");
        }

        // Preserve the full raw data in the data directory:
        String stageName = this.getStage().getStep() + "-" + this.getStage();
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String fileName = stageName + variantName + "." + reportPath.getFileName().toString();
        dataDirectory.toFile().mkdirs();
        Files.copy(reportPath, dataDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        // Read (relevant parts of) the data and write it to the DB:
        Document document = new SAXReader().read(reportPath.toFile());
        Element mutationsElement = document.getRootElement();

        List<PitMutationReportRecord> records = new ArrayList<>();
        for (Element mutationElement : mutationsElement.elements("mutation")) {
            PitMutationReportRecord record = create.newRecord(Tables.PIT_MUTATION_REPORT);
            record.setProjectId(this.getProjectId());

            record.setStep(this.getStage().getStep());
            record.setStage(this.getStage());
            record.setVariant(this.getVariant());

            record.setIsDetected(Boolean.parseBoolean(mutationElement.attributeValue("detected")));
            record.setStatus(MutationStatus.valueOf(mutationElement.attributeValue("status")));
            record.setNumberOfTestsRun(Integer.parseInt(mutationElement.attributeValue("numberOfTestsRun")));

            record.setSourceFile(mutationElement.element("sourceFile").getText());
            record.setMutatedClass(mutationElement.element("mutatedClass").getText());
            record.setMutatedMethod(mutationElement.element("mutatedMethod").getText());
            record.setMethodDescription(mutationElement.element("methodDescription").getText());
            record.setLineNumber(Integer.parseInt(mutationElement.element("lineNumber").getText()));
            record.setMutator(mutationElement.element("mutator").getText());
            record.setDescription(mutationElement.element("description").getText());

            records.add(record);
        }

        create.batchInsert(records).execute();
    }

    private List<String> buildGradleCommand(List<String> targetClasses, List<String> targetTests) {
        List<String> command = new ArrayList<>(Arrays.asList("./gradlew", "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE, "--info", "pitest"));
        command.add("-Pmutators=" + Configuration.getPitestMutators());
        if (targetClasses != null) {
            // @TODO: Avoid "Argument list too long" errors if there are many targetClasses.
            command.add("-PtargetClasses=" + String.join(",", targetClasses));
        }
        if (targetTests != null) {
            // @TODO: Avoid "Argument list too long" errors if there are many targetTests.
            command.add("-PtargetTests=" + String.join(",", targetTests));
        }
        return command;
    }

    private List<String> buildMavenCommand(List<String> targetClasses, List<String> targetTests) throws IOException, DocumentException {
        // We are setting the targetClasses / targetTests via the POM file
        // (rather than -DtargetClasses=... / -DtargetTests=...)
        // to avoid "Argument list too long" errors.
        Path pomFilePath = this.projectRecord.getRootPath().resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        MavenDependencyManager.updatePitestTargets(pomFilePath, targetClasses, targetTests);

        Path commandDataPath = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/command-data");
        Files.createDirectories(commandDataPath);

        String stageName = this.stage.getStep() + "-" + this.stage;
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String executionName = "." + System.currentTimeMillis();
        String baseName = stageName + variantName + executionName;

        Path pomDataFilePath = commandDataPath.resolve(baseName + ".pom.xml");
        Files.copy(pomFilePath, pomDataFilePath);

        return new ArrayList<>(Arrays.asList(
            "mvn",
            "--file", Configuration.MAVEN_CUSTOM_BUILD_FILE,
            "pitest:mutationCoverage",
            "-Dmutators=" + Configuration.getPitestMutators()
        ));
    }
}
