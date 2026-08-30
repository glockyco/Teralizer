package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import teralizer.domain.MethodParameter;
import teralizer.util.TypeCapability;

public class ParameterTypeFilter extends AbstractFilter {

    private final Gson gson;
    private final AssertionRecord assertionRecord;
    private final MutResolutionObservationRecord resolutionObservation;

    public ParameterTypeFilter(
        Gson gson,
        AssertionRecord assertionRecord,
        MutResolutionObservationRecord resolutionObservation
    ) {
        this.gson = gson;
        this.assertionRecord = assertionRecord;
        this.resolutionObservation = resolutionObservation;
    }

    @Override
    public FilterResult check() {
        if (this.resolutionObservation == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The MUT resolution observation is missing.",
                FilterReasonCodes.MUT_RESOLUTION_NOT_RECORDED, FilterReasonCodes.DEPENDS_ON_MISSING_MUT);
        }

        if (this.resolutionObservation.getCandidateParamCount() == null
            || this.resolutionObservation.getCandidateParamSupported() == null
            || this.resolutionObservation.getResolvedParameterTypes() == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The selected MUT declaration is unavailable.",
                FilterReasonCodes.MISSING_TESTED_PARAMS, FilterReasonCodes.DEPENDS_ON_MISSING_MUT);
        }

        String testedMethodParametersString = this.assertionRecord.getTestedMethodParameters();
        if (testedMethodParametersString == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The test.tested_method_parameters column is null.",
                FilterReasonCodes.MISSING_TESTED_PARAMS, FilterReasonCodes.DEPENDS_ON_MISSING_MUT);
        }

        Type parametersType = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = this.gson.fromJson(testedMethodParametersString, parametersType);

        if (testedMethodParameters.stream().noneMatch(a -> TypeCapability.supportsGeneratedInput(a.getType()))) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The tested method has no parameters with generalizable types.",
                FilterReasonCodes.NO_GENERALIZABLE_PARAMETERS);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
