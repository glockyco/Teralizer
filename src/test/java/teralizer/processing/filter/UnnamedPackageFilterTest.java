package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.TestRecord;
import org.junit.Assert;

/**
 * The filter runs at the test stage, where the only package name on the record is the test's own.
 * A tested class sits behind an assertion, which this stage has not reached.
 */
class UnnamedPackageFilterTest {

    private static FilterResult check(String testPackageName) {
        TestRecord record = new TestRecord();
        record.setTestPackageName(testPackageName);
        return new UnnamedPackageFilter(record).check();
    }

    @Example
    void aNamedPackageIsAccepted() {
        Assert.assertEquals(FilterDecision.ACCEPT, check("com.example").getDecision());
    }

    @Example
    void anEmptyPackageIsRejected() {
        FilterResult result = check("");
        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNNAMED_PACKAGE, result.getReasonCode());
    }

    @Example
    void aMissingPackageIsRejected() {
        Assert.assertEquals(FilterDecision.REJECT, check(null).getDecision());
    }

    @Example
    void theReasonNamesTheColumnItRead() {
        Assert.assertEquals("test.test_package_name is empty", check("").getReason());
    }
}
