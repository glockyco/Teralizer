package teralizer.transformer;

import teralizer.domain.Constant;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Operation;
import teralizer.domain.Operator;

import java.util.Collections;

/**
 * Pins that a boolean variable referenced inside an SPF path condition renders as its numeric form
 * ({@code _p_.b ? 1 : 0}), since SPF models a boolean as an integer 0/1.
 */
public class ModelToJavaTransformerBooleanVariableTest {

    @Example
    void rendersBooleanVariablesAsNumericValuesInsideSpfPathConditions() {
        Operation model = new Operation(new Variable("b", TypeDomain.INTEGER), Operator.NE, new Constant((long) 0, TypeDomain.INTEGER));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("b", "boolean"));

        Assert.assertEquals("((_p_.b ? 1 : 0) != 0)", transformer.transform(model));
    }
}
