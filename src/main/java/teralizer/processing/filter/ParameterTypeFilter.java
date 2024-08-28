package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.domain.MethodParameter;

import java.lang.reflect.Type;
import java.util.List;

public class ParameterTypeFilter extends AbstractFilter {

    private final Gson gson;

    public ParameterTypeFilter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public FilterResult check(TestRecord testRecord) {
        String testedMethodParamTypes = testRecord.getTestedMethodParamTypes();
        if (testedMethodParamTypes == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The test.tested_method_param_types column is null.");
        }

        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = this.gson.fromJson(testedMethodParamTypes, type);

        if (testedMethodParameters.stream().noneMatch(a -> a.getType().equals("int"))) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The tested method has no parameters with generalizable types.");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
