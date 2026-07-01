package teralizer.processing.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.junit.Assert;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

/**
 * Regression guard for the inline-constructor input case. When the tested method's
 * declared parameters are all object types but its call passes an inline constructor
 * whose arguments are generalizable primitives (e.g. {@code contains(new Interval(1,10), 5)}),
 * {@code TestAnalysisTask} stores the unwrapped constructor inputs via
 * {@code GeneralizableInput.derive(...)}, so {@link ParameterTypeFilter} already sees
 * generalizable {@code MethodParameter}s in {@code testedMethodParameters} and accepts.
 *
 * <p>This pins the contract so a future change that short-circuits on raw argument
 * count cannot silently regress the inline-constructor accept.
 */
public class ParameterTypeFilterConstructorInputTest {

    private static final Gson GSON = new Gson();
    private static final Type ARGUMENTS_TYPE = new TypeToken<List<MethodArgument>>() {}.getType();
    private static final Type PARAMETERS_TYPE = new TypeToken<List<MethodParameter>>() {}.getType();

    private static ParameterTypeFilter filter(List<MethodArgument> callArguments, List<MethodParameter> methodParameters) {
        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodCallArguments(GSON.toJson(callArguments, ARGUMENTS_TYPE));
        record.setTestedMethodParameters(GSON.toJson(methodParameters, PARAMETERS_TYPE));
        return new ParameterTypeFilter(GSON, record);
    }

    @Example
    void acceptsWhenUnwrappedConstructorInputsAreGeneralizable() {
        // The declared method takes (Interval, int), but derive() unwrapped the inline
        // Interval(1,10) constructor into two int parameters, so testedMethodParameters
        // carries three int inputs and the filter must accept.
        List<MethodArgument> callArguments = Arrays.asList(
            new MethodArgument("int", "1"),
            new MethodArgument("int", "10"),
            new MethodArgument("int", "5")
        );
        List<MethodParameter> methodParameters = Arrays.asList(
            new MethodParameter("int", "_ctor_interval_zero_lower"),
            new MethodParameter("int", "_ctor_interval_one_upper"),
            new MethodParameter("int", "value")
        );

        FilterResult result = filter(callArguments, methodParameters).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsWhenReceiverConstructorInputsAreGeneralizableWithZeroMethodArgs() {
        // new Interval(1.0,10.0).getSize(): zero method arguments, but derive() unwrapped
        // the receiver constructor into two double inputs. The filter must not reject on
        // the empty argument list — it sees two generalizable parameters and accepts.
        List<MethodArgument> callArguments = Arrays.asList(
            new MethodArgument("double", "1.0"),
            new MethodArgument("double", "10.0")
        );
        List<MethodParameter> methodParameters = Arrays.asList(
            new MethodParameter("double", "_ctor_receiver_zero_lower"),
            new MethodParameter("double", "_ctor_receiver_one_upper")
        );

        FilterResult result = filter(callArguments, methodParameters).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void rejectsWhenNoParameterIsGeneralizable() {
        List<MethodArgument> callArguments = Collections.singletonList(new MethodArgument("java.lang.Object", "x"));
        List<MethodParameter> methodParameters = Collections.singletonList(new MethodParameter("java.lang.Object", "value"));

        FilterResult result = filter(callArguments, methodParameters).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }
}
