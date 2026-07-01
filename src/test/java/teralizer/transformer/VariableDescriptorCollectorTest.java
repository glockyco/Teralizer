package teralizer.transformer;

import java.util.Collections;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class VariableDescriptorCollectorTest {

    @Example
    void collectsVariableNamesWithDomainsAcrossNestedModels() {
        Model input = new Operation(
            new Variable("INT_1", TypeDomain.INTEGER),
            Operator.GT,
            new Constant(0L, TypeDomain.INTEGER));
        Model output = new Invocation(
            new Variable("STR_2", TypeDomain.STRING),
            null,
            "trim",
            Collections.emptyList());

        Map<String, TypeDomain> variables = VariableDescriptorCollector.collect(input, output);

        Assert.assertEquals(TypeDomain.INTEGER, variables.get("INT_1"));
        Assert.assertEquals(TypeDomain.STRING, variables.get("STR_2"));
    }
}
