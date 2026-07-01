package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class ModelToJavaTransformerStringLiteralTest {

    @Example
    void rendersStringConstantAsQuotedJavaLiteralWithEscaping() {
        // Raw value contains a double-quote and a backslash; both must be escaped in the literal.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        Constant constant = new Constant("say \"hello\" \\world", TypeDomain.STRING);
        Assert.assertEquals("\"say \\\"hello\\\" \\\\world\"", transformer.transform(constant));
    }

    @Example
    void rendersStringConstantControlCharsAsEscapeSequences() {
        // Tab and newline embedded in the raw value must appear as \t and \n escape sequences.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        Constant constant = new Constant("a\tb\n", TypeDomain.STRING);
        Assert.assertEquals("\"a\\tb\\n\"", transformer.transform(constant));
    }

    @Example
    void rendersStringConstantInsidePredicateAsQuotedLiteral() {
        // Typed string constants appearing as the right-hand side of an EQ comparison render quoted.
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        Operation model = new Operation(new Variable("s", TypeDomain.STRING), Operator.EQ, new Constant("ok", TypeDomain.STRING));
        Assert.assertEquals("(_p_.s == \"ok\")", transformer.transform(model));
    }
}
