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
    void rendersIntegralConcreteValuesAsDecimalLiteralsNotCharCodes() {
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();

        // int/short/byte are integral, not char: they must render as their decimal value, including
        // negatives and values beyond Character.MAX_VALUE. Routing them through the char renderer
        // crashes on negatives and silently truncates large values via a (char) cast.
        Assert.assertEquals("7", transformer.transform(new MethodArgument("int", "7")));
        Assert.assertEquals("-5", transformer.transform(new MethodArgument("int", "-5")));
        Assert.assertEquals("70000", transformer.transform(new MethodArgument("int", "70000")));
        Assert.assertEquals("-128", transformer.transform(new MethodArgument("byte", "-128")));
        Assert.assertEquals("-32768", transformer.transform(new MethodArgument("short", "-32768")));
    }

    @Example
    void rendersBooleanVariablesAsNumericValuesInsideSpfPathConditions() {
        Operation model = new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(0));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("b", "boolean"));

        Assert.assertEquals("((_p_.b ? 1 : 0) != 0)", transformer.transform(model));
    }
}
