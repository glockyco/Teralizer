package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jooq.generated.tables.records.AssertionRecord;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

import java.lang.reflect.Type;
import java.util.List;

import static teralizer.util.Configuration.SUPPORTED_TYPES;

public class ParameterTypeFilter extends AbstractFilter {

    private final Gson gson;
    private final AssertionRecord assertionRecord;

    public ParameterTypeFilter(Gson gson, AssertionRecord assertionRecord) {
        this.gson = gson;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        String testedMethodCallArgumentsString = this.assertionRecord.getTestedMethodCallArguments();
        if (testedMethodCallArgumentsString == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The test.tested_method_call_arguments column is null.");
        }

        Type argumentsType = new TypeToken<List<MethodArgument>>() {}.getType();
        List<MethodParameter> testedMethodCallArguments = this.gson.fromJson(testedMethodCallArgumentsString, argumentsType);

        if (testedMethodCallArguments.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The tested method has no parameters.");
        }

        String testedMethodParametersString = this.assertionRecord.getTestedMethodParameters();
        if (testedMethodParametersString == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The test.tested_method_parameters column is null.");
        }

        Type parametersType = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = this.gson.fromJson(testedMethodParametersString, parametersType);

        if (testedMethodParameters.stream().noneMatch(a -> SUPPORTED_TYPES.contains(a.getType()))) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The tested method has no parameters with generalizable types.");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
