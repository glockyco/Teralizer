package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.util.Configuration;

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

        FilterResult result = new MissingValueFilter(null, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MISSING_TESTED_FILE, result.getReasonCode());
        JsonObject detail = GSON.fromJson(result.getDetailJson(), JsonObject.class);
        Assert.assertEquals("MISSING_TESTED_METHOD", detail.getAsJsonArray("all_reason_codes").get(1).getAsString());
        Assert.assertEquals("MISSING_TESTED_PARAMS", detail.getAsJsonArray("all_reason_codes").get(2).getAsString());
    }

    @Example
    void missingValueReportsResolverReasonWhileKeepingColumnLevelDetail() {
        AssertionRecord record = new AssertionRecord();
        record.setTestedClassName("Cut");
        record.setTestedMethodQualifiedName(null);
        record.setTestedMethodParameters(null);

        FilterResult result = new MissingValueFilter(FilterReasonCodes.MUT_LIBRARY_DECLARATION, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MUT_LIBRARY_DECLARATION, result.getReasonCode());
        JsonObject detail = GSON.fromJson(result.getDetailJson(), JsonObject.class);
        Assert.assertEquals("MISSING_TESTED_FILE", detail.getAsJsonArray("all_reason_codes").get(0).getAsString());
        Assert.assertEquals("MISSING_TESTED_METHOD", detail.getAsJsonArray("all_reason_codes").get(1).getAsString());
        Assert.assertEquals("MISSING_TESTED_PARAMS", detail.getAsJsonArray("all_reason_codes").get(2).getAsString());
    }

    @Example
    void missingValueSurfacesMissingResolverTelemetryAsItsOwnCode() {
        AssertionRecord record = new AssertionRecord();
        record.setTestedClassName("Cut");
        record.setTestedMethodQualifiedName(null);
        record.setTestedMethodParameters(null);

        FilterResult result = new MissingValueFilter(FilterReasonCodes.MUT_RESOLUTION_NOT_RECORDED, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MUT_RESOLUTION_NOT_RECORDED, result.getReasonCode());
        JsonObject detail = GSON.fromJson(result.getDetailJson(), JsonObject.class);
        Assert.assertEquals("MISSING_TESTED_FILE", detail.getAsJsonArray("all_reason_codes").get(0).getAsString());
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

        FilterResult result = new UnsupportedAssertionFilter(null, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL, result.getReasonCode());
    }

    @Example
    void unsupportedAssertionFilterAcceptsEqualityIsomorphicAssertThat() throws Exception {
        FilterResult result = unsupportedAssertionResult(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.is(1));");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void unsupportedAssertionFilterKeepsAssertThatReasonCodeForOtherMatchers() throws Exception {
        FilterResult result = unsupportedAssertionResult(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.notNullValue());");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_ASSERT_THAT, result.getReasonCode());
    }

    @Example
    void unsupportedAssertionFilterAcceptsRecognizedTryFailCatch() throws Exception {
        FilterResult result = unsupportedAssertionResult(
            "try { new Subject().reject(1); org.junit.Assert.fail(); } catch (IllegalArgumentException e) { }",
            "fail");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void unsupportedAssertionFilterKeepsFailReasonCodeOutsideTryExceptionIdiom() throws Exception {
        FilterResult result = unsupportedAssertionResult("org.junit.Assert.fail();", "fail");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_FAIL, result.getReasonCode());
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
    void returnTypeFilterAcceptsExceptionRecipeOracleForTryFailCatch() throws Exception {
        AssertionRecord record = new AssertionRecord();
        record.setAssertionName("fail");
        record.setTestedMethodReturnType("int");
        record.setGeneralizationRecipe(exceptionRecipeJson("java.lang.IllegalArgumentException"));

        FilterResult result = new ReturnTypeFilter(record).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
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

        FilterResult result = new UnsupportedAssertionFilter(null, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_ASSERTION_FAIL, result.getReasonCode());
    }

    @Example
    void unsupportedTestTypeAnnotationSetsStableCode() {
        TestRecord record = new TestRecord();
        record.setTestAnnotationName("org.testng.annotations.Test");

        FilterResult result = new TestTypeFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_TEST_TYPE, result.getReasonCode());
    }

    @Example
    void unnamedPackageSetsStableCode() {
        TestRecord record = new TestRecord();
        // testPackageName is null from the default record constructor

        FilterResult result = new UnnamedPackageFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNNAMED_PACKAGE, result.getReasonCode());
    }

    @Example
    void unsupportedStringOperationSetsStableCode() {
        Launcher launcher = new Launcher();
        launcher.addInputResource(
            new VirtualFile("class C { public static char f(String s) { return s.charAt(0); } }"));
        launcher.buildModel();
        CtType<?> type = launcher.getModel().getAllTypes().iterator().next();
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodAbsolutePath(type.getMethodsByName("f").get(0).getPath().toString());

        FilterResult result = new StringOperationFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_STRING_OPERATION, result.getReasonCode());
    }

    @Example
    void assertionInLoopSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(
            "class T { void m() { for (int i = 0; i < 1; i++) { org.junit.Assert.assertEquals(1, 1); } } }"));
        launcher.buildModel();
        CtType<?> type = launcher.getModel().getAllTypes().iterator().next();
        List<CtInvocation<?>> invocations = type.getMethodsByName("m").get(0).getElements(new TypeFilter<>(CtInvocation.class));
        AssertionRecord record = new AssertionRecord();
        record.setAssertionAbsolutePath(invocations.get(0).getPath().toString());

        FilterResult result = new AssertionInLoopFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.ASSERTION_IN_LOOP, result.getReasonCode());
    }

    @Example
    void assertionInLoopMissingPathDefersWithStableCode() throws Exception {
        AssertionRecord record = new AssertionRecord();

        FilterResult result = new AssertionInLoopFilter(new Launcher(), record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MISSING_ASSERTION_PATH, result.getReasonCode());
    }

    @Example
    void assertionInMethodHelperSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(
            "class T { void testM() { assertHelper(); } "
                + "void assertHelper() { org.junit.Assert.assertEquals(1, 1); } }"));
        launcher.buildModel();
        CtType<?> type = launcher.getModel().getAllTypes().iterator().next();
        CtMethod<?> testMethod = type.getMethodsByName("testM").get(0);
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("T");
        record.setTestMethodName("testM");
        record.setTestMethodQualifiedName("T.testM");
        record.setTestMethodAbsolutePath(testMethod.getPath().toString());
        record.setTestMethodRelativePath(testMethod.getPath().relativePath(type).toString());

        FilterResult result = new AssertionInMethodFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.ASSERTION_IN_METHOD, result.getReasonCode());
    }

    @Example
    void testngIsRejectedAsAForeignFrameworkRatherThanAcceptedAsJUnit() {
        TestRecord record = new TestRecord();
        record.setTestAnnotationName(Configuration.TEST_MARKER_TESTNG);

        FilterResult result = new TestTypeFilter(record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_FOREIGN_FRAMEWORK, result.getReasonCode());
    }

    @Example
    void disabledTestSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(
            "public class T { @org.junit.Test @org.junit.Ignore public void t() {} }", "T.java"));
        launcher.buildModel();
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("T");
        record.setTestMethodName("t");
        record.setTestMethodQualifiedName("T.t");

        FilterResult result = new DisabledTestFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.DISABLED_TEST, result.getReasonCode());
    }

    @Example
    void liveTestPassesTheDisabledFilter() throws Exception {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(
            "public class T { @org.junit.Test public void t() {} }", "T.java"));
        launcher.buildModel();
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("T");
        record.setTestMethodName("t");

        FilterResult result = new DisabledTestFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void nestedClassSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile("class T { class Inner {} }"));
        launcher.buildModel();
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("T");

        FilterResult result = new NestedClassesFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.NESTED_CLASSES, result.getReasonCode());
    }

    @Example
    void staticInitializerSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile("class T { static { int x = 1; } }"));
        launcher.buildModel();
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("T");

        FilterResult result = new StaticInitializersFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.STATIC_INITIALIZERS_PRESENT, result.getReasonCode());
    }

    @Example
    void testedMethodCallInLoopSetsStableCode() throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(
            "class T { void m() { for (int i = 0; i < 1; i++) { helper(); } } void helper() {} }"));
        launcher.buildModel();
        CtType<?> type = launcher.getModel().getAllTypes().iterator().next();
        List<CtInvocation<?>> invocations = type.getMethodsByName("m").get(0).getElements(new TypeFilter<>(CtInvocation.class));
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodCallAbsolutePath(invocations.get(0).getPath().toString());

        FilterResult result = new TestedMethodInLoopFilter(launcher, record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.TESTED_METHOD_IN_LOOP, result.getReasonCode());
    }

    @Example
    void testedMethodInLoopMissingPathDefersWithStableCode() throws Exception {
        AssertionRecord record = new AssertionRecord();

        FilterResult result = new TestedMethodInLoopFilter(new Launcher(), record).check();

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.MISSING_TESTED_METHOD_CALL_PATH, result.getReasonCode());
    }

    @Example
    void noAssertionsRejectSetsStableCode() {
        DSLContext dsl = DSL.using(new MockConnection(ctx -> {
            DSLContext inner = DSL.using(SQLDialect.DEFAULT);
            Field<Integer> f = DSL.field("count", Integer.class);
            Result<Record1<Integer>> r = inner.newResult(f);
            r.add(inner.newRecord(f).values(0));
            return new MockResult[] {new MockResult(1, r)};
        }), SQLDialect.POSTGRES);
        TestRecord record = new TestRecord();
        record.setTestMethodQualifiedName("pkg.T.test");

        FilterResult result = new NoAssertionsFilter(dsl, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.NO_ASSERTIONS, result.getReasonCode());
    }

    @Example
    void nonPassingTestRejectSetsStableCode() {
        DSLContext dsl = DSL.using(new MockConnection(ctx -> {
            DSLContext inner = DSL.using(SQLDialect.DEFAULT);
            Field<String> f = DSL.field("test_method_name", String.class);
            Result<Record1<String>> r = inner.newResult(f);
            r.add(inner.newRecord(f).values("failingTest"));
            return new MockResult[] {new MockResult(1, r)};
        }), SQLDialect.POSTGRES);
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("pkg.T");

        FilterResult result = new NonPassingTestFilter(dsl, record).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.TEST_NOT_PASSING, result.getReasonCode());
    }
    @Example
    void nonPassingGeneralizationIgnoresFailingSiblingTestCase() {
        DSLContext dsl = DSL.using(new MockConnection(ctx -> {
            DSLContext inner = DSL.using(SQLDialect.DEFAULT);
            Field<String> f = DSL.field("test_case_name", String.class);
            Result<Record1<String>> r = inner.newResult(f);
            r.add(inner.newRecord(f).values("pkg.Generated.sibling"));
            return new MockResult[] {new MockResult(1, r)};
        }), SQLDialect.POSTGRES);
        GeneralizationRecord generalization = new GeneralizationRecord();
        generalization.setProjectId(1L);
        generalization.setPackageName("pkg");
        generalization.setClassName("Generated");
        generalization.setClassQualifiedName("pkg.Generated");
        generalization.setMethodQualifiedName("pkg.Generated.property");
        generalization.setVariant("IMPROVED");

        FilterResult result = new NonPassingTestFilter(dsl, new TestRecord(), generalization).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    private static String exceptionRecipeJson(String oracleExpressionType) {
        return "{"
            + "\"version\":3,"
            + "\"schema\":\"teralizer.generalization.recipe\","
            + "\"oracleExpressionPath\":\"#body#statement[index=0]\","
            + "\"oracleMethodPath\":\"#type[name=Subject]#method[signature=reject(int)]\","
            + "\"oracleType\":\"int\","
            + "\"oracleExpressionType\":\"" + oracleExpressionType + "\","
            + "\"inputSites\":[]"
            + "}";
    }

    private static FilterResult unsupportedAssertionResult(String assertionSource) throws Exception {
        return unsupportedAssertionResult(assertionSource, "assertThat");
    }

    private static FilterResult unsupportedAssertionResult(String assertionSource, String assertionName) throws Exception {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(
            "public class SubjectTest {\n"
                + "  public void t() { " + assertionSource + " }\n"
                + "}\n"
                + "class Subject { int id(int x) { return x; } void reject(int x) { throw new IllegalArgumentException(); } }\n",
            "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        CtInvocation<?> assertion = testMethod.getElements(new TypeFilter<>(CtInvocation.class)).stream()
            .filter(invocation -> assertionName.equals(invocation.getExecutable().getSimpleName()))
            .findFirst()
            .get();
        AssertionRecord record = new AssertionRecord();
        record.setAssertionName(assertionName);
        record.setAssertionAbsolutePath(assertion.getPath().toString());
        return new UnsupportedAssertionFilter(launcher, record).check();
    }

}
