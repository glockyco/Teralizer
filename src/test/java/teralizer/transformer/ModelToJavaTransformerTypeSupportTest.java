package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodArgument;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;

import java.util.Collections;

public class ModelToJavaTransformerTypeSupportTest {

    @Example
    void rendersBooleanConcreteValuesFromJpfAsJavaLiterals() {
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();

        Assert.assertEquals("true", transformer.transform(new MethodArgument("boolean", "1")));
        Assert.assertEquals("false", transformer.transform(new MethodArgument("boolean", "0")));
        Assert.assertEquals("true", transformer.transform(new MethodArgument("boolean", "true")));
        Assert.assertEquals("false", transformer.transform(new MethodArgument("java.lang.Boolean", "false")));
    }

    @Example
    void rendersCharConcreteValuesFromJpfAsJavaCharValues() {
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();

        Assert.assertEquals("(char) 65", transformer.transform(new MethodArgument("char", "65")));
        Assert.assertEquals("'A'", transformer.transform(new MethodArgument("char", "A")));
        Assert.assertEquals("'\\\\'", transformer.transform(new MethodArgument("char", "\\")));
    }

    @Example
    void rendersBooleanVariablesAsNumericValuesInsideSpfPathConditions() {
        Operation model = new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(0));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("b", "boolean"));

        Assert.assertEquals("((_p_.b ? 1 : 0) != 0)", transformer.transform(model));
    }
}
