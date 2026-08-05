package teralizer.processing.task;

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
    void leavesEngineOnlyNameUnlinkedInsteadOfThrowing() {
        PitDataCollectionTask.ResolvedTestName resolved = PitDataCollectionTask.resolveTestName(
            "net.byteseek.compiler.matcher.SequenceMatcherCompilerTest.[engine:jqwik]",
            new HashMap<>(),
            new HashMap<>()
        );

        Assert.assertNull(resolved.testId());
        Assert.assertNull(resolved.generalizationId());
    }
}
