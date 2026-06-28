package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantString;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableString;

public class ModelToJavaTransformerStringLiteralTest {

    @Example
    void rendersConstantStringAsQuotedJavaLiteralWithEscaping() {
        // Raw value contains a double-quote and a backslash; both must be escaped in the literal.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        ConstantString constant = new ConstantString("say \"hello\" \\world");
        Assert.assertEquals("\"say \\\"hello\\\" \\\\world\"", transformer.transform(constant));
    }

    @Example
    void rendersConstantStringControlCharsAsEscapeSequences() {
        // Tab and newline embedded in the raw value must appear as \t and \n escape sequences.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        ConstantString constant = new ConstantString("a\tb\n");
        Assert.assertEquals("\"a\\tb\\n\"", transformer.transform(constant));
    }

    @Example
    void rendersConstantStringInsidePredicateAsQuotedLiteral() {
        // ConstantString appearing as the right-hand side of an EQ comparison renders quoted.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        Operation model = new Operation(new VariableString("s"), Operator.EQ, new ConstantString("ok"));
        Assert.assertEquals("(_p_.s == \"ok\")", transformer.transform(model));
    }
}
