package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jooq.generated.tables.records.AssertionRecord;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.util.TypeCapability;

public class ReturnTypeFilter extends AbstractFilter {
    private static final Gson GSON = new Gson();

    private final AssertionRecord assertionRecord;

    public ReturnTypeFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        String returnType = this.assertionRecord.getTestedMethodReturnType();
        String nullReturnTypeReason = "The assertion.tested_method_return_type column is null.";
        boolean recipePresent = this.assertionRecord.getGeneralizationRecipe() != null;
        if (recipePresent) {
            GeneralizationRecipe recipe = GeneralizationRecipe.fromJson(GSON, this.assertionRecord.getGeneralizationRecipe());
            returnType = recipe.getOracleExpressionType();
            nullReturnTypeReason = "The generalization recipe oracleExpressionType is null.";
        }

        if (returnType == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, nullReturnTypeReason,
                FilterReasonCodes.MISSING_TESTED_METHOD, FilterReasonCodes.DEPENDS_ON_MISSING_MUT);
        }

        if (returnType.equals("void") || returnType.equals("java.lang.Void")) {
            String reason = recipePresent
                ? "Oracle expression has void expression type: " + returnType
                : "Tested method has void return type: " + this.assertionRecord.getTestedMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason, FilterReasonCodes.UNSUPPORTED_RETURN_TYPE,
                null, detail(returnType));
        }

        if (recipePresent && isExceptionOracleAssertion()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        if (!TypeCapability.supportsReturnValue(returnType)) {
            String reason = "Tested method has unsupported return type: " + this.assertionRecord.getTestedMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason, FilterReasonCodes.UNSUPPORTED_RETURN_TYPE,
                null, detail(returnType));
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }

    private boolean isExceptionOracleAssertion() {
        String assertionName = this.assertionRecord.getAssertionName();
        return "fail".equals(assertionName) || "assertThrows".equals(assertionName);
    }

    private static String detail(String returnType) {
        JsonObject object = new JsonObject();
        object.addProperty("return_type", returnType);
        return object.toString();
    }
}
