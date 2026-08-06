package teralizer.processing.task;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import net.jqwik.api.Example;
import org.junit.Assert;

public class PitDataCollectionTaskTest {

    @Example
    void mavenCommandOverridesProjectPitTelemetryConfiguration() {
        Assert.assertEquals(
            Arrays.asList(
                "mvn",
                "--file", "pom.teralizer.generalized.xml",
                "pitest:mutationCoverage",
                "-Dmutators=DEFAULTS",
                "-DoutputFormats=XML",
                "-DexportLineCoverage=true",
                "-Dverbose=false",
                "-DextraFeatures=-macos_focus"
            ),
            PitDataCollectionTask.mavenPitestCommand("pom.teralizer.generalized.xml", "DEFAULTS")
        );
    }

    @Example
    void resolvesOriginalTestIdFromJupiterName() {
        HashMap<String, Long> testIds = new HashMap<>();
        testIds.put("org.example.MyTest.testFoo", 11L);
        HashMap<String, Long> generalizationIds = new HashMap<>();

        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "org.example.MyTest.[engine:junit-jupiter]/[class:org.example.MyTest]/[method:testFoo()]",
            testIds,
            generalizationIds
        );

        Assert.assertEquals(Long.valueOf(11L), resolved.testId());
        Assert.assertNull(resolved.generalizationId());
    }

    @Example
    void resolvesGeneralizationIdFromJqwikName() {
        HashMap<String, Long> testIds = new HashMap<>();
        HashMap<String, Long> generalizationIds = new HashMap<>();
        generalizationIds.put("org.example._MyTest_Generalized_testFoo_1_Test.prop", 22L);

        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "org.example._MyTest_Generalized_testFoo_1_Test.[engine:jqwik]/"
                + "[class:org.example._MyTest_Generalized_testFoo_1_Test]/"
                + "[property:prop(org.example._MyTest_Generalized_testFoo_1_Test$P)]",
            testIds,
            generalizationIds
        );

        Assert.assertNull(resolved.testId());
        Assert.assertEquals(Long.valueOf(22L), resolved.generalizationId());
    }

    @Example
    void leavesUnmappedVintageHelperUnlinked() {
        HashMap<String, Long> testIds = new HashMap<>();
        HashMap<String, Long> generalizationIds = new HashMap<>();

        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "org.example._MyTest_Generalized_testFoo_1_Test.[engine:junit-vintage]/"
                + "[runner:org.example._MyTest_Generalized_testFoo_1_Test]/"
                + "[test:helperSanity(org.example._MyTest_Generalized_testFoo_1_Test)]",
            testIds,
            generalizationIds
        );

        Assert.assertNull(resolved.testId());
        Assert.assertNull(resolved.generalizationId());
    }

    @Example
    void leavesVintageInitializationErrorUnlinked() {
        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "org.example.StreamingTypeAdaptersTest.[engine:junit-vintage]/"
                + "[runner:org.example.StreamingTypeAdaptersTest]/"
                + "[test:initializationError(org.junit.runner.manipulation.Filter)]",
            new HashMap<>(),
            new HashMap<>()
        );

        Assert.assertNull(resolved.testId());
        Assert.assertNull(resolved.generalizationId());
        Assert.assertTrue(resolved.isUnattributed());
    }

    @Example
    void leavesEngineOnlyNameUnlinkedInsteadOfThrowing() {
        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "net.byteseek.compiler.matcher.SequenceMatcherCompilerTest.[engine:jqwik]",
            new HashMap<>(),
            new HashMap<>()
        );

        Assert.assertNull(resolved.testId());
        Assert.assertNull(resolved.generalizationId());
        Assert.assertTrue(resolved.isUnattributed());
    }

    @Example
    void resolvesJqwikDisplayNameWithLeadingSpace() {
        HashMap<String, Long> testIds = new HashMap<>();
        HashMap<String, Long> generalizationIds = new HashMap<>();
        generalizationIds.put("org.example.ConstantTest_Generalized_testHasChar_13_Test.testHasChar", 23L);

        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            " ConstantTest Generalized testHasChar 13 Test.[engine:jqwik]/"
                + "[class:org.example.ConstantTest_Generalized_testHasChar_13_Test]/"
                + "[property:testHasChar(org.example.ConstantTest_Generalized_testHasChar_13_Test$TestParameters)]",
            testIds,
            generalizationIds
        );

        Assert.assertNull(resolved.testId());
        Assert.assertEquals(Long.valueOf(23L), resolved.generalizationId());
    }

    @Example
    void parsesQuotedCsvFieldsWithoutShiftingColumns() throws Exception {
        Path path = Files.createTempFile("jacoco", ".csv");
        try {
            Files.write(path, Arrays.asList(
                "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED",
                "\"project,one\",pkg,Class,0"
            ), StandardCharsets.UTF_8);

            CsvReportParser.CsvReport report = CsvReportParser.parse(path);

            Assert.assertEquals(Arrays.asList("GROUP", "PACKAGE", "CLASS", "INSTRUCTION_MISSED"), report.header());
            Assert.assertEquals(Arrays.asList("project,one", "pkg", "Class", "0"), report.rows().get(0));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Example
    void distinguishesHeaderOnlyCsvFromMalformedRow() throws Exception {
        Path headerOnly = Files.createTempFile("report", ".csv");
        Path malformed = Files.createTempFile("report", ".csv");
        try {
            Files.write(headerOnly, Arrays.asList("A,B"), StandardCharsets.UTF_8);
            Files.write(malformed, Arrays.asList("A,B", "value"), StandardCharsets.UTF_8);

            Assert.assertTrue(CsvReportParser.parse(headerOnly).rows().isEmpty());
            RuntimeException failure = Assert.assertThrows(
                RuntimeException.class,
                () -> CsvReportParser.parse(malformed)
            );
            Assert.assertTrue(failure.getMessage().contains("Malformed CSV report"));
        } finally {
            Files.deleteIfExists(headerOnly);
            Files.deleteIfExists(malformed);
        }
    }
}
