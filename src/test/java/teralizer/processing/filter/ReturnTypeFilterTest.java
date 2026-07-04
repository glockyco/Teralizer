package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.junit.Assert;

public class ReturnTypeFilterTest {

    private static FilterResult check(String testedMethodReturnType, String recipeJson) throws Exception {
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodQualifiedName("subject.Subject.method");
        record.setTestedMethodReturnType(testedMethodReturnType);
        record.setGeneralizationRecipe(recipeJson);
        return new ReturnTypeFilter(record).check();
    }

    private static String recipeJson(String oracleExpressionType) {
        String expressionTypeJson = oracleExpressionType == null ? "null" : "\"" + oracleExpressionType + "\"";
        return "{"
            + "\"version\":2,"
            + "\"schema\":\"teralizer.generalization.recipe\","
            + "\"oracleExpressionPath\":\"#expression\","
            + "\"oracleMethodPath\":\"#method\","
            + "\"oracleType\":\"int\","
            + "\"oracleExpressionType\":" + expressionTypeJson + ","
            + "\"inputSites\":[]"
            + "}";
    }

    @Example
    void acceptsSupportedRecipeExpressionTypeWhenColumnReturnTypeDiffers() throws Exception {
        FilterResult result = check("int", recipeJson("boolean"));

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void rejectsVoidRecipeExpressionTypeAndNamesExpressionType() throws Exception {
        FilterResult result = check("int", recipeJson("void"));

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertTrue(result.getReason().contains("void"));
        Assert.assertTrue(result.getReason().contains("expression type"));
    }

    @Example
    void defersWhenRecipeExpressionTypeIsNull() throws Exception {
        FilterResult result = check("int", recipeJson(null));

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals("The generalization recipe oracleExpressionType is null.", result.getReason());
    }

    @Example
    void defersWhenRecipeIsAbsentAndColumnReturnTypeIsNull() throws Exception {
        FilterResult result = check(null, null);

        Assert.assertEquals(FilterDecision.DEFER, result.getDecision());
        Assert.assertEquals("The assertion.tested_method_return_type column is null.", result.getReason());
    }

    @Example
    void rejectsWhenRecipeIsAbsentAndColumnReturnTypeIsVoidWithHeadReason() throws Exception {
        FilterResult result = check("void", null);

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals("Tested method has void return type: subject.Subject.method", result.getReason());
    }

    @Example
    void acceptsWhenRecipeIsAbsentAndColumnReturnTypeIsSupported() throws Exception {
        FilterResult result = check("int", null);

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }
}
