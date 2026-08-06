package teralizer.processing.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import net.jqwik.api.Example;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.junit.Assert;
import teralizer.processing.reports.SurefireReportNames;

public class SurefireReportMatchingTest {
    private static final Path SUREFIRE_REPORT_FIXTURES = Paths.get("src/test/resources/surefire-reports");

    @Example
    public void fqn_shape_matches_exactly() {
        Assert.assertTrue(SurefireReportNames.matches(
            "org.x._FooTest_Generalized_bar_1_Test.bar",
            "org.x._FooTest_Generalized_bar_1_Test.bar"));
    }

    @Example
    public void display_name_shape_matches_as_package_less_suffix() {
        Assert.assertTrue(SurefireReportNames.matches(
            "org.hampelratte.svdrp.commands._LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds",
            "_LSTCTest_Generalized_testWithGroupsAndIds_1995_Test.testWithGroupsAndIds"));
    }

    @Example
    public void simple_name_collision_does_not_match() {
        Assert.assertFalse(SurefireReportNames.matches(
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
    public void fqn_report_is_selected_when_it_is_the_only_candidate() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-fqn-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        writeReport(reports.resolve("TEST-" + qualifiedClass + ".xml"), qualifiedClass, "bar", false);

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals("TEST-" + qualifiedClass + ".xml", selection.path.getFileName().toString());
        Assert.assertEquals(1, selection.testCaseReports.size());
    }

    @Example
    public void display_name_report_is_selected_when_it_is_the_only_candidate() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-display-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        writeReport(reports.resolve("TEST- FooTest Generalized bar 1 Test.xml"), " FooTest Generalized bar 1 Test", "bar", false);

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals("TEST- FooTest Generalized bar 1 Test.xml", selection.path.getFileName().toString());
        Assert.assertEquals(1, selection.testCaseReports.size());
    }

    @Example
    public void display_name_report_wins_when_fqn_candidate_shadows_it() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-shadowed-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        writeReport(reports.resolve("TEST-" + qualifiedClass + ".xml"), qualifiedClass, "other", false);
        writeReport(reports.resolve("TEST- FooTest Generalized bar 1 Test.xml"), " FooTest Generalized bar 1 Test", "bar", false);

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals("TEST- FooTest Generalized bar 1 Test.xml", selection.path.getFileName().toString());
        Assert.assertEquals("bar", selection.testCaseReports.get(0).getName());
    }

    @Example
    public void matching_duplicate_entries_are_returned_from_selected_report() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-duplicate-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        writeReport(reports.resolve("TEST-" + qualifiedClass + ".xml"), qualifiedClass, "bar", false);
        appendReportCase(reports.resolve("TEST-" + qualifiedClass + ".xml"), qualifiedClass, "bar");

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals(2, selection.testCaseReports.size());
        Assert.assertTrue(selection.testCaseReports.get(1).hasFailure());
    }

    @Example
    public void long_alternative_filename_is_found_by_content() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-long-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        writeReport(reports.resolve("TEST-report-name-truncated-by-surefire.xml"), " FooTest Generalized bar 1 Test", "bar", false);

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals("TEST-report-name-truncated-by-surefire.xml", selection.path.getFileName().toString());
        Assert.assertEquals(1, selection.testCaseReports.size());
    }

    @Example
    public void malformed_report_does_not_hide_a_matching_candidate() throws IOException {
        Path reports = Files.createTempDirectory("teralizer-malformed-report");
        String qualifiedClass = "org.x._FooTest_Generalized_bar_1_Test";
        Files.write(reports.resolve("TEST-broken.xml"), "<testsuite>".getBytes(StandardCharsets.UTF_8));
        writeReport(reports.resolve("TEST-" + qualifiedClass + ".xml"), qualifiedClass, "bar", false);

        JunitDataCollectionTask.TestReportSelection selection = JunitDataCollectionTask.identifyTestReportPath(
            reports, "_FooTest_Generalized_bar_1_Test", qualifiedClass, qualifiedClass + ".bar");

        Assert.assertEquals("TEST-" + qualifiedClass + ".xml", selection.path.getFileName().toString());
        Assert.assertEquals(1, selection.testCaseReports.size());
    }

    private static void writeReport(Path path, String className, String methodName, boolean failure) throws IOException {
        String failureXml = failure ? "<failure message=\"boom\" type=\"java.lang.AssertionError\">boom</failure>" : "";
        String xml = "<?xml version=\"1.0\"?><testsuite name=\"fixture\" time=\"0\"><testcase classname=\""
            + className + "\" name=\"" + methodName + "\" time=\"0\">" + failureXml + "</testcase></testsuite>";
        Files.write(path, xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void appendReportCase(Path path, String className, String methodName) throws IOException {
        String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String replacement = "<testcase classname=\"" + className + "\" name=\"" + methodName + "\" time=\"0\">"
            + "<failure message=\"boom\" type=\"java.lang.AssertionError\">boom</failure></testcase>";
        Files.write(path, xml.replace("</testsuite>", replacement + "</testsuite>").getBytes(StandardCharsets.UTF_8));
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
