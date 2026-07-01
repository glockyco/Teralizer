package teralizer.domain;

import net.jqwik.api.Example;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link ModelFolder} is a total bottom-up fold over the concrete {@link Model}
 * node set: every node has its own abstract hook, so a renderer that implements the
 * folder cannot silently drop a node kind (a missing case is a compile error once any
 * concrete folder exists). These tests pin the dispatch and the node-set contract.
 */
public class ModelFolderTest {

    /** Records the hook name each node dispatches to. */
    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(ConstantInteger c) { return "ConstantInteger"; }
        @Override public String fold(ConstantReal c) { return "ConstantReal"; }
        @Override public String fold(ConstantString c) { return "ConstantString"; }
        @Override public String fold(VariableInteger v) { return "VariableInteger"; }
        @Override public String fold(VariableReal v) { return "VariableReal"; }
        @Override public String fold(VariableString v) { return "VariableString"; }
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
        Assert.assertEquals("ConstantInteger", new ConstantInteger(3).fold(folder));
        Assert.assertEquals("ConstantReal", new ConstantReal(1.5).fold(folder));
        Assert.assertEquals("ConstantString", new ConstantString("s").fold(folder));
        Assert.assertEquals("VariableInteger", new VariableInteger("x").fold(folder));
        Assert.assertEquals("VariableReal", new VariableReal("y").fold(folder));
        Assert.assertEquals("VariableString", new VariableString("z").fold(folder));
        Assert.assertEquals("ArrayExpression", new ArrayExpression("a", "int").fold(folder));
        Assert.assertEquals("Error", new Error("t", "m").fold(folder));
        Assert.assertEquals("ExceptionModel", new ExceptionModel("n", "m").fold(folder));
        Assert.assertEquals("Operator[+]", Operator.PLUS.fold(folder));
    }

    @Example
    void compositeNodesFoldChildrenBeforeTheHook() {
        RecordingFolder folder = new RecordingFolder();
        Assert.assertEquals(
            "ArrayElementExpression[VariableInteger]",
            new ArrayElementExpression("a", "int", new VariableInteger("i")).fold(folder));
        Assert.assertEquals(
            "Invocation{startsWith}(VariableString,ConstantString)",
            new Invocation(new VariableString("z"), null, "startsWith", Arrays.asList(new ConstantString("s"))).fold(folder));
        Assert.assertEquals(
            "Not(Invocation{startsWith}(VariableString,ConstantString))",
            new Not(new Invocation(new VariableString("z"), null, "startsWith", Arrays.asList(new ConstantString("s")))).fold(folder));
        Assert.assertEquals(
            "Operation{+}(ConstantInteger,VariableInteger)",
            new Operation(new ConstantInteger(1), Operator.PLUS, new VariableInteger("x")).fold(folder));
    }

    @Example
    void operationWithNullOperandFoldsWithoutExploding() {
        RecordingFolder folder = new RecordingFolder();
        Assert.assertEquals(
            "Operation{+}(ConstantInteger,null)",
            new Operation(new ConstantInteger(4), Operator.PLUS, null).fold(folder));
    }

    /**
     * The folder must expose one hook per concrete {@link Model} node. A new node that
     * lacks a hook fails this guard, which is the runtime mirror of the compile-time
     * totality a concrete folder enjoys.
     */
    @Example
    void folderDeclaresOneHookPerConcreteNode() {
        Set<Class<?>> expectedNodes = new HashSet<>(Arrays.asList(
            ConstantInteger.class, ConstantReal.class, ConstantString.class,
            VariableInteger.class, VariableReal.class, VariableString.class,
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
