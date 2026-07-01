package teralizer.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Example;
import org.junit.Assert;

/**
 * A {@link ModelFolder} is a total bottom-up fold over the concrete {@link Model}
 * node set: every node has its own abstract hook, so a renderer that implements the
 * folder cannot silently drop a node kind (a missing case is a compile error once any
 * concrete folder exists). These tests pin the dispatch and the node-set contract.
 */
public class ModelFolderTest {

    /** Records the hook name each node dispatches to. */
    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(Constant c) { return "Constant"; }
        @Override public String fold(Variable v) { return "Variable"; }
        @Override public String fold(ArrayExpression e) { return "ArrayExpression"; }
        @Override public String fold(ArrayElementExpression e, String selector) {
            return "ArrayElementExpression[" + selector + "]";
        }
        @Override public String fold(Invocation invocation, String receiver, List<String> args) {
            return "Invocation{" + invocation.method + "}(" + receiver + "," + String.join(",", args) + ")";
        }
        @Override public String fold(Not not, String operand) {
            return "Not(" + operand + ")";
        }
        @Override public String fold(Operation op, String left, String right) {
            return "Operation{" + op.op + "}(" + left + "," + right + ")";
        }
        @Override public String fold(Operator op) { return "Operator[" + op + "]"; }
        @Override public String fold(Error e) { return "Error"; }
        @Override public String fold(ExceptionModel e) { return "ExceptionModel"; }
    }

    @Example
    void leafNodesDispatchToTheirOwnHook() {
        RecordingFolder folder = new RecordingFolder();
        Assert.assertEquals("Constant", new Constant(3L, TypeDomain.INTEGER).fold(folder));
        Assert.assertEquals("Constant", new Constant(1.5, TypeDomain.REAL).fold(folder));
        Assert.assertEquals("Constant", new Constant("s", TypeDomain.STRING).fold(folder));
        Assert.assertEquals("Variable", new Variable("x", TypeDomain.INTEGER).fold(folder));
        Assert.assertEquals("Variable", new Variable("y", TypeDomain.REAL).fold(folder));
        Assert.assertEquals("Variable", new Variable("z", TypeDomain.STRING).fold(folder));
        Assert.assertEquals("ArrayExpression", new ArrayExpression("a", "int").fold(folder));
        Assert.assertEquals("Error", new Error("t", "m").fold(folder));
        Assert.assertEquals("ExceptionModel", new ExceptionModel("n", "m").fold(folder));
        Assert.assertEquals("Operator[+]", Operator.PLUS.fold(folder));
    }

    @Example
    void compositeNodesFoldChildrenBeforeTheHook() {
        RecordingFolder folder = new RecordingFolder();
        Assert.assertEquals(
            "ArrayElementExpression[Variable]",
            new ArrayElementExpression("a", "int", new Variable("i", TypeDomain.INTEGER)).fold(folder));
        Assert.assertEquals(
            "Invocation{startsWith}(Variable,Constant)",
            new Invocation(new Variable("z", TypeDomain.STRING), null, "startsWith", Arrays.asList(new Constant("s", TypeDomain.STRING))).fold(folder));
        Assert.assertEquals(
            "Not(Invocation{startsWith}(Variable,Constant))",
            new Not(new Invocation(new Variable("z", TypeDomain.STRING), null, "startsWith", Arrays.asList(new Constant("s", TypeDomain.STRING)))).fold(folder));
        Assert.assertEquals(
            "Operation{+}(Constant,Variable)",
            new Operation(new Constant((long) 1, TypeDomain.INTEGER), Operator.PLUS, new Variable("x", TypeDomain.INTEGER)).fold(folder));
    }

    @Example
    void operationRequiresTwoOperands() {
        try {
            new Operation(new Constant((long) 4, TypeDomain.INTEGER), Operator.PLUS, null);
            Assert.fail("operation must be binary after calls and negation moved to Invocation/Not");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("binary"));
        }
    }

    /**
     * The folder must expose one hook per concrete {@link Model} node. A new node that
     * lacks a hook fails this guard, which is the runtime mirror of the compile-time
     * totality a concrete folder enjoys.
     */
    @Example
    void folderDeclaresOneHookPerConcreteNode() {
        Set<Class<?>> expectedNodes = new HashSet<>(Arrays.asList(
            Constant.class, Variable.class,
            ArrayExpression.class, ArrayElementExpression.class,
            Invocation.class, Not.class,
            Operation.class, Operator.class, Error.class, ExceptionModel.class));

        Set<Class<?>> hookParamTypes = Arrays.stream(ModelFolder.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("fold"))
            .map(m -> m.getParameterTypes()[0])
            .collect(Collectors.toSet());

        Assert.assertEquals("ModelFolder hooks drifted from the concrete node set",
            expectedNodes, hookParamTypes);
    }
}
