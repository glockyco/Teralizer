package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.junit.Assert;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

public class FilterTelemetryTest {

    private static final Gson GSON = new Gson();
    private static final Type ARGUMENTS_TYPE = new TypeToken<List<MethodArgument>>() {}.getType();
    private static final Type PARAMETERS_TYPE = new TypeToken<List<MethodParameter>>() {}.getType();

    @Example
    void missingValueUsesFirstStableReasonCodeAndRecordsShadowedCodes() {
        AssertionRecord record = new AssertionRecord();
        record.setTestedClassName("Cut");
        record.setTestedMethodQualifiedName(null);
        record.setTestedMethodParameters(null);

        FilterResult result = new MissingValueFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MISSING_TESTED_FILE, result.getReasonCode());
        JsonObject detail = GSON.fromJson(result.getDetailJson(), JsonObject.class);
        Assert.assertEquals("MISSING_TESTED_METHOD", detail.getAsJsonArray("all_reason_codes").get(1).getAsString());
        Assert.assertEquals("MISSING_TESTED_PARAMS", detail.getAsJsonArray("all_reason_codes").get(2).getAsString());
    }

    @Example
    void deferredParameterDiscoveryDeclaresMissingMutDependency() {
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodCallArguments(null);

        FilterResult result = new ParameterTypeFilter(GSON, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MISSING_TESTED_PARAMS, result.getReasonCode());
        Assert.assertEquals(FilterReasonCodes.DEPENDS_ON_MISSING_MUT, result.getDependsOn());
    }

    @Example
    void unsupportedAssertionNamesMapToSpecificStableCodes() {
        AssertionRecord record = new AssertionRecord();
        record.setAssertionName("assertNotNull");

        FilterResult result = new UnsupportedAssertionFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL, result.getReasonCode());
    }

    @Example
    void unsupportedReturnTypeRecordsReturnTypeDetail() throws Exception {
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodReturnType("java.lang.Object");
        record.setTestedMethodQualifiedName("org.example.Cut.value");

        FilterResult result = new ReturnTypeFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_RETURN_TYPE, result.getReasonCode());
        JsonObject detail = GSON.fromJson(result.getDetailJson(), JsonObject.class);
        Assert.assertEquals("java.lang.Object", detail.get("return_type").getAsString());
    }

    @Example
    void excludedParentTestRecordsDependency() {
        TestRecord test = new TestRecord();
        test.setIsIncluded(false);

        FilterResult result = new ExcludedTestFilter(test).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.EXCLUDED_PARENT_TEST, result.getReasonCode());
        Assert.assertEquals(FilterReasonCodes.DEPENDS_ON_EXCLUDED_TEST, result.getDependsOn());
    }

    @Example
    void noGeneralizableParametersUseStableCode() {
        AssertionRecord record = new AssertionRecord();
        List<MethodArgument> callArguments = Collections.singletonList(new MethodArgument("java.lang.Object", "x"));
        List<MethodParameter> methodParameters = Collections.singletonList(new MethodParameter("java.lang.Object", "value"));
        record.setTestedMethodCallArguments(GSON.toJson(callArguments, ARGUMENTS_TYPE));
        record.setTestedMethodParameters(GSON.toJson(methodParameters, PARAMETERS_TYPE));

        FilterResult result = new ParameterTypeFilter(GSON, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.NO_GENERALIZABLE_PARAMETERS, result.getReasonCode());
    }

    @Example
    void unsupportedAssertionFailHasSpecificStableCode() {
        AssertionRecord record = new AssertionRecord();
        record.setAssertionName("fail");

        FilterResult result = new UnsupportedAssertionFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_FAIL, result.getReasonCode());
    }
}
