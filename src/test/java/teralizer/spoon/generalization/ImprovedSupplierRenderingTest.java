package teralizer.spoon.generalization;

import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import teralizer.domain.Constant;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
import teralizer.jqwik.planning.ConstraintClause;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;

public class ImprovedSupplierRenderingTest {

    @Example
    void omitsFilterWhenNoResidualInputPredicateExists() {
        MethodParameter x = new MethodParameter("double", "x");
        // No model → no clauses → hasClauses() == false → no filter emitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            Collections.emptyMap(),
            null
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertFalse(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFilterWhenInputPredicateExists() {
        MethodParameter x = new MethodParameter("double", "x");
        // x > 0.0 produces a clause → hasClauses() == true → filter emitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            new Operation(
                new Variable("x", TypeDomain.REAL),
                Operator.GT,
                new Constant(0.0, TypeDomain.REAL)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFullFilterWhenPlannerReportsResidualClauses() {
        MethodParameter x = new MethodParameter("double", "x");
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            new Operation(
                new Variable("x", TypeDomain.REAL),
                Operator.GT,
                new Constant(0.0, TypeDomain.REAL)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return (_p_.x > 0.0);"));
    }

    @Example
    void omitsFilterWhenPlannerHasNoResidualClauses() {
        MethodParameter parameter = new MethodParameter("double", "x");
        // No model → no clauses → hasClauses() == false → filter omitted
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(parameter),
            Collections.emptyMap(),
            null
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(parameter),
            null,
            plan
        );

        Assert.assertFalse(supplierClass.toString().contains(".filter("));
    }

    @Example
    void keepsFullFilterForPlannerGeneratedAffineBounds() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            java.util.Arrays.asList(a, b),
            new Operation(
                new Operation(new Variable("a", TypeDomain.INTEGER), Operator.PLUS, new Variable("b", TypeDomain.INTEGER)),
                Operator.LT,
                new Constant((long) 10, TypeDomain.INTEGER)
            )
        );

        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            java.util.Arrays.asList(a, b),
            null,
            plan
        );

        Assert.assertTrue(supplierClass.toString().contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (int) (9 - a))"));
        Assert.assertTrue(supplierClass.toString().contains(".filter("));
        Assert.assertTrue(supplierClass.toString().contains("return ((_p_.a + _p_.b) < 10);"));
    }

    @Example
    void doubleBoundsUseDynamicScaleInsteadOfGlobalMaximumScale() {
        MethodParameter a = new MethodParameter("double", "a");
        MethodParameter b = new MethodParameter("double", "b");
        // b > a: NumericDomainPlanner sees a variable lower bound on b → emits dynamic-scale recipe
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            java.util.Arrays.asList(a, b),
            Collections.singletonMap("b", new PrimitiveValue("double", 2.0)),
            new Operation(
                new Variable("b", TypeDomain.REAL),
                Operator.GT,
                new Variable("a", TypeDomain.REAL)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            java.util.Arrays.asList(a, b),
            null,
            plan
        );

        String body = supplierClass.toString();
        Assert.assertFalse(body.contains("ofScale(325)"));
        Assert.assertTrue(body.contains("java.math.BigDecimal.valueOf"));
        Assert.assertTrue(body.contains("bScale"));
        Assert.assertTrue(body.contains(".ofScale(bScale).between"));
    }

    @Example
    void injectsFirstValueWhenArgumentPresent() {
        MethodParameter x = new MethodParameter("int", "x");
        // The original concrete input must be injected so the generalized test still exercises it.
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            Collections.singletonMap("x", new PrimitiveValue("int", 7)),
            null
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        String code = supplierClass.toString();
        Assert.assertTrue("seed must wrap the whole tuple (FirstValueArbitrary<TestParameters>), not the parameter",
            code.contains("FirstValueArbitrary<TestParameters>(new TestParameters("));
        Assert.assertFalse("must not wrap individual parameters (per-param injection collapses inner diversity under flatMap)",
            code.contains("FirstValueArbitrary<Integer"));
    }

    @Example
    void wrapsCombinedTupleNotIndividualParametersForMultipleParameters() {
        MethodParameter a = new MethodParameter("int", "a");
        MethodParameter b = new MethodParameter("int", "b");
        java.util.Map<String, Value> arguments = new java.util.HashMap<>();
        arguments.put("a", new PrimitiveValue("int", 7));
        arguments.put("b", new PrimitiveValue("int", 9));
        InputGenerationPlan plan = new InputGenerationPlanner().plan(java.util.Arrays.asList(a, b), arguments, null);
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(), java.util.Arrays.asList(a, b), null, plan);
        String code = supplierClass.toString();
        // Tuple-level: the exact original combination is the seed, wrapped once around the whole tuple.
        Assert.assertTrue("must inject the original tuple at the tuple level",
            code.contains("FirstValueArbitrary<TestParameters>(new TestParameters("));
        // The flatMap-collapse bug: per-parameter FirstValueArbitrary pins inner params to their firstValue.
        Assert.assertFalse("must not wrap individual parameters",
            code.contains("FirstValueArbitrary<Integer"));
    }

    @Example
    void wrapsFilteredArbitrarySoDedupSeesOnlyExecutableTuples() {
        MethodParameter x = new MethodParameter("int", "x");
        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            Collections.singletonList(x),
            Collections.singletonMap("x", new PrimitiveValue("int", 2)),
            new Operation(
                new Variable("x", TypeDomain.INTEGER),
                Operator.GT,
                new Constant((long) 0, TypeDomain.INTEGER)
            )
        );
        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        String code = supplierClass.toString();
        Assert.assertTrue("seed wrapper must contain the residual filter, not sit below it",
            firstValueWrapperArgumentContainsFilter(code));
    }

    @Example
    void keepsFullFilterEvenWhenAllClausesConsumedByConstruction() {
        MethodParameter x = new MethodParameter("int", "x");
        // Simulate post-aggregation state: the clause x < 5 is consumed by the numeric planner,
        // but the filter must still use the full predicate (filter stays unconditional).
        ConstraintClause clause = new ConstraintClause(0, null, "(_p_.x < 5)");
        teralizer.jqwik.planning.ParameterGenerationPlan paramPlan =
            new teralizer.jqwik.planning.ParameterGenerationPlan(
                x,
                teralizer.domain.TypeDomain.INTEGER,
                new teralizer.jqwik.planning.RawJavaRecipe("return net.jqwik.api.Arbitraries.integers().between(0, 4)"),
                null,
                java.util.Collections.singleton(0)
            );
        InputGenerationPlan plan = new InputGenerationPlan(
            java.util.Collections.singletonList(paramPlan),
            java.util.Collections.singletonList(clause),
            java.util.Collections.singleton(0)
        );

        CtClass<?> supplierClass = ImprovedTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(x),
            null,
            plan
        );

        Assert.assertTrue("filter must be emitted even when all clauses are consumed",
            supplierClass.toString().contains(".filter("));
        Assert.assertTrue("filter must use the full predicate including consumed clauses",
            supplierClass.toString().contains("return (_p_.x < 5);"));
    }
    private static boolean firstValueWrapperArgumentContainsFilter(String code) {
        int start = code.indexOf("new FirstValueArbitrary<TestParameters>");
        if (start < 0) {
            return false;
        }
        int open = code.indexOf('(', start);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return code.substring(open, i).contains(".filter(");
                }
            }
        }
        return false;
    }

}
