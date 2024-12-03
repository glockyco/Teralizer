package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.domain.MethodParameter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class ParameterTypeFilter extends AbstractFilter {

    private static final List<String> SUPPORTED_TYPES = Arrays.asList("byte", "short", "int", "long", "float", "double");

    private final Gson gson;
    private final TestRecord testRecord;

    public ParameterTypeFilter(Gson gson, TestRecord testRecord) {
        this.gson = gson;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        String testedMethodParamTypes = this.testRecord.getTestedMethodParamTypes();
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
