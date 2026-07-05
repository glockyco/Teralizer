package teralizer.processing.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import org.jooq.tools.json.JSONArray;
import teralizer.processing.MutationStatus;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.MavenDependencyManager;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.repository.SQLiteRepository;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;

public class PitDataCollectionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    // Different types of test "names" observed in PIT coverage / mutation reports:
    // - org.example.MyTest.[engine:junit-jupiter]/[class:org.example.MyTest]/[method:testFooBar()]
    // - org.example.MyTest.[engine:junit-jupiter]/[class:org.example.MyTest]/[test-template:testFoo(double, double)]/[test-template-invocation:#13]
    // - org.example.MyTest.[engine:junit-vintage]/[runner:org.example.MyTest]
    // - org.example.MyTest.[engine:junit-vintage]/[runner:org.example.MyTest]/[test:testBar(org.example.MyMathTest)]
    // - org.example.MyTest.[engine:jqwik]/[class:org.example.MyTest]/[property:testBazz(org.example.MyTest$TestParameters)]
    // Note that some of these only occur with specific (i) JUnit 4 vs. 5 and (ii) Maven vs. Gradle combinations.
    // => Take care when modifying the RegEx pattern to ensure matching still works for all types of supported projects.

    private static final Pattern TEST_NAME_PATTERN = Pattern.compile(
        // Qualified class name followed by engine info:
        "([\\w$.]+)\\.[^/]+/" +
        // Section identifier followed by qualified class name:
        "\\[(?:class|runner):([\\w$.]+)\\]" +
        // Optional: Section identifier followed by simple method name and parameter types:
        "(?:/\\[(?:method|property|test|test-template):([\\w$]+)\\(.*?\\)\\])?" +
        // Optional: Section identifier followed by invocation count (for repeated / parameterized tests only):
        "(?:/\\[test-template-invocation:.*\\])?",
        Pattern.UNICODE_CHARACTER_CLASS
    );

    public PitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public PitDataCollectionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;

        this.consoleCommand = new ConsoleCommand(
            stage,
            variant,
            projectRecord.getId(),
            projectRecord.getDataPath(),
            Configuration.getPitestMaxExecutionTime(),
            TimeUnit.SECONDS
        );
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (!Configuration.isPitestEnabled()) {
            reportInfo.accept("Mutation testing disabled (teralizer.pitest.enabled = false); skipping PIT"
                + " execution and data collection for " + this.getStage()
                + (this.getVariant() == null ? "" : " / " + this.getVariant()) + ".");
            if (this.stage == ProcessingStage.COLLECT_PIT_DATA_GENERALIZED) {
                DSLContext create = context.get(TaskContext.DSL_CONTEXT);
                GeneralizationLifecycleWriter.recordProjectStageSucceeded(create, this.stage, this.getProjectId(), this.getVariant());
            }
            return;
        }

        Path pitDataDirectory = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/pit-data");
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        this.executeMutationTesting(create);

        Map<String, Long> testIds = this.fetchTestIds(create);
        Map<String, Long> generalizationIds = this.fetchGeneralizationIds(create);

        this.collectCoverageData(create, pitDataDirectory, testIds, generalizationIds);
        this.collectMutationData(create, pitDataDirectory, testIds, generalizationIds);
        if (this.stage == ProcessingStage.COLLECT_PIT_DATA_GENERALIZED) {
            GeneralizationLifecycleWriter.recordProjectStageSucceeded(create, this.stage, this.getProjectId(), this.getVariant());
        }
    }

    private Map<String, Long> fetchTestIds(DSLContext create) {
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

    private Map<String, Long> fetchGeneralizationIds(DSLContext create) {
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

    private void collectCoverageData(
        DSLContext create,
        Path dataDirectory,
        Map<String, Long> testIds,
        Map<String, Long> generalizationIds
    ) throws DocumentException, IOException {
        Path reportPath = this.projectRecord.getMutationReportsPath().resolve("linecoverage.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect coverage data. Report file '" + reportPath + "' does not exist.");
        }

        // Preserve the full raw data in the data directory:
        String stageName = this.getStage().getStep() + "-" + this.getStage();
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String fileName = stageName + variantName + "." + reportPath.getFileName().toString();
        Files.createDirectories(dataDirectory);
        Files.copy(reportPath, dataDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        // Read (relevant parts of) the data and write it to the DB:
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

                TestNameInfo testNameInfo = this.processTestName(name);

                Long testId;
                Long generalizationId;
                if (testNameInfo.getMethodQualifiedName() == null) {
                    testId = null;
                    generalizationId = null;
                } else {
                    testId = testIds.getOrDefault(testNameInfo.getMethodQualifiedName(), null);
                    generalizationId = generalizationIds.getOrDefault(testNameInfo.getMethodQualifiedName(), null);
                    if (testId == null && generalizationId == null) {
                        throw new RuntimeException("Failed to map coverage record to a test / generalization." +
                            "\nPIT name: " + name +
                            "\nQualified method name: " + testNameInfo.getMethodQualifiedName()
                        );
                    }
                }

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

                record.setTestPackageName(testNameInfo.getPackageName());
                record.setTestClassName(testNameInfo.getClassName());
                record.setTestMethodName(testNameInfo.getMethodName());

                records.add(record);
            }
        }
        create.batchInsert(records).execute();
    }

    private void collectMutationData(
        DSLContext create,
        Path dataDirectory,
        Map<String, Long> testIds,
        Map<String, Long> generalizationIds
    ) throws DocumentException, IOException {
        Path reportPath = this.projectRecord.getMutationReportsPath().resolve("mutations.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect mutation data. Report file '" + reportPath + "' does not exist.");
        }

        // Preserve the full raw data in the data directory:
        String stageName = this.getStage().getStep() + "-" + this.getStage();
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String fileName = stageName + variantName + "." + reportPath.getFileName().toString();
        Files.createDirectories(dataDirectory);
        Files.copy(reportPath, dataDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        // Read (relevant parts of) the data and write it to the DB:
        Document document = new SAXReader().read(reportPath.toFile());
        Element mutationsElement = document.getRootElement();

        List<PitMutationReportRecord> records = new ArrayList<>();
        for (Element mutationElement : mutationsElement.elements("mutation")) {
            String mutatedClassQualifiedName = mutationElement.element("mutatedClass").getText();
            int mutatedClassLastDotIndex = mutatedClassQualifiedName.lastIndexOf('.');
            String mutatedPackageName = mutatedClassQualifiedName.substring(0, mutatedClassLastDotIndex);
            String mutatedClassName = mutatedClassQualifiedName.substring(mutatedClassLastDotIndex + 1);

            PitMutationReportRecord record = create.newRecord(Tables.PIT_MUTATION_REPORT);
            record.setProjectId(this.getProjectId());

            record.setStep(this.getStage().getStep());
            record.setStage(this.getStage());
            record.setVariant(this.getVariant());

            record.setIsDetected(Boolean.parseBoolean(mutationElement.attributeValue("detected")));
            record.setStatus(MutationStatus.valueOf(mutationElement.attributeValue("status")));
            record.setNumberOfTestsRun(Integer.parseInt(mutationElement.attributeValue("numberOfTestsRun")));

            record.setSourceFile(mutationElement.element("sourceFile").getText());
            record.setMutatedPackage(mutatedPackageName);
            record.setMutatedClass(mutatedClassName);
            record.setMutatedMethod(mutationElement.element("mutatedMethod").getText());
            record.setMethodDescription(mutationElement.element("methodDescription").getText());
            record.setLineNumber(Integer.parseInt(mutationElement.element("lineNumber").getText()));
            record.setMutator(mutationElement.element("mutator").getText());
            record.setDescription(mutationElement.element("description").getText());

            Element indexesElement = mutationElement.element("indexes");
            List<Integer> indexes = indexesElement == null ? new ArrayList<>() :
                indexesElement.elements("index").stream()
                    .map(e -> Integer.parseInt(e.getText()))
                    .sorted().collect(Collectors.toList());
            record.setIndexes(JSONArray.toJSONString(indexes));

            Element blocksElement = mutationElement.element("blocks");
            List<Integer> blocks = blocksElement == null ? new ArrayList<>() :
                blocksElement.elements("block").stream()
                    .map(e -> Integer.parseInt(e.getText()))
                    .sorted().collect(Collectors.toList());
            record.setBlocks(JSONArray.toJSONString(blocks));

            Element killingTestElement = mutationElement.element("killingTest");
            if (killingTestElement != null) {
                String killingTestName = killingTestElement.getText();

                if (killingTestName != null && !killingTestName.isEmpty()) {
                    TestNameInfo testNameInfo = this.processTestName(killingTestName);

                    Long testId;
                    Long generalizationId;
                    if (testNameInfo.getMethodQualifiedName() == null) {
                        testId = null;
                        generalizationId = null;
                    } else {
                        testId = testIds.getOrDefault(testNameInfo.getMethodQualifiedName(), null);
                        generalizationId = generalizationIds.getOrDefault(testNameInfo.getMethodQualifiedName(), null);
                        if (testId == null && generalizationId == null) {
                            throw new RuntimeException("Failed to map mutation record to a test / generalization." +
                                "\nPIT name: " + killingTestName +
                                "\nQualified method name: " + testNameInfo.getMethodQualifiedName()
                            );
                        }
                    }

                    record.setKillingTestId(testId);
                    record.setKillingGeneralizationId(generalizationId);

                    record.setKillingPackageName(testNameInfo.getPackageName());
                    record.setKillingClassName(testNameInfo.getClassName());
                    record.setKillingMethodName(testNameInfo.getMethodName());
                }
            }

            records.add(record);
        }

        create.batchInsert(records).execute();
    }

    private TestNameInfo processTestName(String testName) {
        Matcher matcher = TEST_NAME_PATTERN.matcher(testName);

        if (matcher.matches()) {
            // Extract the qualified class name and (optionally) method name
            String testClassQualifiedName = matcher.group(2);
            String testMethodName = matcher.group(3); // can be null

            // Create the fully qualified method name
            String testMethodQualifiedName = testMethodName == null ? null : testClassQualifiedName + "." + testMethodName;

            // Extract package and class name in one step
            int lastDotIndex = testClassQualifiedName.lastIndexOf('.');
            String testPackageName = lastDotIndex > 0 ? testClassQualifiedName.substring(0, lastDotIndex) : "";
            String testClassName = lastDotIndex > 0 ? testClassQualifiedName.substring(lastDotIndex + 1) : testClassQualifiedName;

            return new TestNameInfo(
                testClassQualifiedName,
                testMethodQualifiedName,
                testPackageName,
                testClassName,
                testMethodName
            );
        } else {
            throw new RuntimeException("Unexpected test name format: " + testName);
        }
    }

    private static class TestNameInfo {

        private final String classQualifiedName;
        private final String methodQualifiedName;
        private final String packageName;
        private final String className;
        private final String methodName;

        public TestNameInfo(
            String classQualifiedName,
            String methodQualifiedName,
            String packageName,
            String className,
            String methodName
        ) {
            this.classQualifiedName = classQualifiedName;
            this.methodQualifiedName = methodQualifiedName;
            this.packageName = packageName;
            this.className = className;
            this.methodName = methodName;
        }

        public String getClassQualifiedName() {
            return this.classQualifiedName;
        }

        public String getMethodQualifiedName() {
            return this.methodQualifiedName;
        }

        public String getPackageName() {
            return this.packageName;
        }

        public String getClassName() {
            return this.className;
        }

        public String getMethodName() {
            return this.methodName;
        }
    }
}
