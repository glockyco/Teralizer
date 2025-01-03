package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jooq.generated.tables.records.AssertionRecord;
import teralizer.domain.MethodParameter;

import java.lang.reflect.Type;
import java.util.List;

import static teralizer.processing.task.TestGeneralizationTask.SUPPORTED_TYPES;

public class ParameterTypeFilter extends AbstractFilter {

    private final Gson gson;
    private final AssertionRecord assertionRecord;

    public ParameterTypeFilter(Gson gson, AssertionRecord assertionRecord) {
        this.gson = gson;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        String testedMethodParamTypes = this.assertionRecord.getTestedMethodParameters();
        if (testedMethodParamTypes == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The test.tested_method_param_types column is null.");
        }

        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = this.gson.fromJson(testedMethodParamTypes, type);

        if (testedMethodParameters.stream().noneMatch(a -> SUPPORTED_TYPES.contains(a.getType()))) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The tested method has no parameters with generalizable types.");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
