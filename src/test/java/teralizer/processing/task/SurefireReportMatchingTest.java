package teralizer.processing.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import net.jqwik.api.Example;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.junit.Assert;

public class SurefireReportMatchingTest {
    private static final Path SUREFIRE_REPORT_FIXTURES = Paths.get("src/test/resources/surefire-reports");

    @Example
    public void fqn_shape_matches_exactly() {
        Assert.assertTrue(JunitDataCollectionTask.matchesQualifiedName(
            "org.x._FooTest_Generalized_bar_1_Test.bar",
            "org.x._FooTest_Generalized_bar_1_Test.bar"));
    }

    @Example
    public void display_name_shape_matches_as_package_less_suffix() {
        Assert.assertTrue(JunitDataCollectionTask.matchesQualifiedName(
            "org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds",
            "_LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds"));
    }

    @Example
    public void simple_name_collision_does_not_match() {
        Assert.assertFalse(JunitDataCollectionTask.matchesQualifiedName(
            "org.x.OtherTest.bar",
            "Test.bar"));
    }

    @Example
    public void fqn_fixture_matches_known_method_through_surefire_parser() {
        List<ReportTestCase> reports = JunitDataCollectionTask.parseTestCaseReports(
            SUREFIRE_REPORT_FIXTURES.resolve("TEST-ch.bfh.unicrypt.helper.math._PermutationTest_Generalized_generalTest_1512_Test.xml"),
            "ch.bfh.unicrypt.helper.math._PermutationTest_Generalized_generalTest_1512_Test",
            "ch.bfh.unicrypt.helper.math._PermutationTest_Generalized_generalTest_1512_Test.generalTest");

        Assert.assertEquals(1, reports.size());
        Assert.assertEquals("generalTest", reports.get(0).getName());
    }

    @Example
    public void display_name_fixture_matches_known_method_through_surefire_parser() {
        List<ReportTestCase> reports = JunitDataCollectionTask.parseTestCaseReports(
            SUREFIRE_REPORT_FIXTURES.resolve("TEST-org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.xml"),
            "org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test",
            "org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds");

        Assert.assertEquals(1, reports.size());
        Assert.assertEquals("testWithGroupsAndIds", reports.get(0).getName());
    }

    @Example
    public void empty_report_directory_refuses_generalized_false_green() throws IOException {
        Path reportsPath = Files.createTempDirectory("teralizer-empty-surefire-reports");

        RuntimeException exception = Assert.assertThrows(
            RuntimeException.class,
            () -> TestExecutionTask.requireGeneralizedReportsPresent(reportsPath, true));

        Assert.assertEquals(
            "Test execution reported success but produced no reports for any generalized test class. " +
                "The project's test runner likely cannot run JUnit-platform tests (surefire < 2.22); " +
                "refusing to record a false pass.",
            exception.getMessage());
    }

    @Example
    public void empty_report_directory_is_allowed_when_no_generalized_classes_were_included() throws IOException {
        Path reportsPath = Files.createTempDirectory("teralizer-no-generalized-classes-surefire-reports");

        TestExecutionTask.requireGeneralizedReportsPresent(reportsPath, false);
    }

    @Example
    public void generalized_report_file_satisfies_false_green_guard() throws IOException {
        Path reportsPath = Files.createTempDirectory("teralizer-generalized-surefire-reports");
        Files.createFile(reportsPath.resolve("TEST-org.x._Foo_Generalized_bar_1_Test.xml"));

        TestExecutionTask.requireGeneralizedReportsPresent(reportsPath, true);
    }
}
