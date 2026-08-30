package teralizer.processing.task;

import com.google.gson.Gson;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import org.junit.Assert;
import teralizer.spoon.analysis.MethodUnderTestResolverTest;
import teralizer.spoon.analysis.MutResolution;

public class MutResolutionObservationMapperTest {

    @Example
    void mapsResolvedPick() {
        MutResolution r = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, new Subject().gcd(6, 9)); }\n"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(r, 11L, 22L, 33L, new Gson(), record);

        Assert.assertEquals(Long.valueOf(33L), record.getAssertionId());
        Assert.assertEquals(Long.valueOf(11L), record.getProjectId());
        Assert.assertEquals(Long.valueOf(22L), record.getTestId());
        Assert.assertEquals("RESOLVED", record.getStatus());
        Assert.assertEquals("T1_PROVEN", record.getConfidenceTier());
        Assert.assertEquals("DIRECT_ACTUAL_CALL", record.getDecidingSignal());
        Assert.assertEquals("gcd", record.getResolvedMethodName());
        Assert.assertEquals(Integer.valueOf(1), record.getCandidateCount());
        Assert.assertNull(record.getNoPickReason());
        Assert.assertEquals("[\"int\",\"int\"]", record.getResolvedParameterTypes());
        Assert.assertEquals(Integer.valueOf(2), record.getCandidateParamCount());
        Assert.assertTrue(record.getCandidateParamSupported());
        Assert.assertEquals("int", record.getResolvedReturnType());
    }

    @Example
    void mapsZeroParameterDeclaration() {
        MutResolution resolution = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(1, new Subject().size()); }\n"
            + "}",
            "public class Subject { public int size() { return 1; } }"
        );

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(resolution, 1L, 2L, 3L, new Gson(), record);

        Assert.assertEquals(Integer.valueOf(0), record.getCandidateParamCount());
        Assert.assertFalse(record.getCandidateParamSupported());
        Assert.assertEquals("[]", record.getResolvedParameterTypes());
    }

    @Example
    void mapsUnsupportedParameterDeclaration() {
        MutResolution resolution = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(1, new Subject().id(new Object())); }\n"
            + "}",
            "public class Subject { public int id(Object value) { return 1; } }"
        );

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(resolution, 1L, 2L, 3L, new Gson(), record);

        Assert.assertEquals(Integer.valueOf(1), record.getCandidateParamCount());
        Assert.assertFalse(record.getCandidateParamSupported());
        Assert.assertEquals("[\"java.lang.Object\"]", record.getResolvedParameterTypes());
    }

    @Example
    void mapsNone() {
        MutResolution r = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = 1 + 2; org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(r, 1L, 2L, 3L, new Gson(), record);

        Assert.assertEquals("NONE", record.getStatus());
        Assert.assertEquals("T5_NONE", record.getConfidenceTier());
        Assert.assertEquals("NO_VISIBLE_CALL", record.getNoPickReason());
        Assert.assertNull(record.getResolvedMethodName());
        Assert.assertNull(record.getResolvedParameterTypes());
        Assert.assertNull(record.getCandidateParamCount());
        Assert.assertNull(record.getCandidateParamSupported());
        Assert.assertEquals("VARIABLE", record.getActualShape());
        Assert.assertEquals("NONE", record.getReceiverProvenance());
    }
}
