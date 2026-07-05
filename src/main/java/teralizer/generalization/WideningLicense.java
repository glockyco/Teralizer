package teralizer.generalization;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import teralizer.jpf.OutputSpecClassifier.OutputSpecClass;
import teralizer.jqwik.planning.ConstraintClause;
import teralizer.transformer.VariableNameCollector;

/**
 * Decides whether one generated property may widen its input parameters while keeping the extracted
 * oracle coherent.
 *
 * <p>Input generation already preserves the extracted path predicate: planner recipes encode some
 * clauses by construction, and the rendered property still filters against the full residual
 * predicate. The missing question is whether the expected side can follow the widened inputs. When
 * the output model is {@link OutputSpecClass#SYMBOLIC}, the expected expression is rendered from
 * symbolic evidence and varies with the generated values. When it is {@link OutputSpecClass#CONSTANT},
 * SPF proved that the value is path-constant, so widening within the admitted path keeps the concrete
 * oracle valid. When it is {@link OutputSpecClass#EXCEPTION}, the oracle is reaching the throw
 * itself. That reachability is a control-flow property: every branch that decides the throw must
 * either leave a path-condition clause that generated inputs satisfy or be independent of widened
 * symbolic inputs. An empty path condition is an unconditional throw; otherwise every widened
 * parameter must be named by the path condition. Concretization events only preserve this argument
 * when telemetry rules out both divergence vectors: a concrete application branch after an event
 * and a native-origin throw whose reachability was decided at the boundary.
 *
 * <p>{@link OutputSpecClass#NULL_CONCRETE} has two siblings that look identical at the persisted
 * output-model boundary. A computed boolean result can be represented only by the path condition:
 * bytecode branches on the symbolic operands, and the captured return value has no separate return
 * attribute. That case is licensable only when the method's return type is {@code boolean} or
 * {@code java.lang.Boolean}, every widened parameter appears in at least one path-condition clause,
 * and no concretization event occurred. The clause requirement is evidence that the returned boolean
 * relation is pinned by the same predicate that admits generated inputs.
 *
 * <p>A pass-through boolean is the reason this class exists instead of treating all booleans as safe.
 * A method can load and return a stored symbolic flag without branching, leaving the path condition
 * empty even though the result varies with input. Empty path conditions therefore never license a
 * null-concrete oracle. Concretization events also refuse the license because native or modeled
 * boundaries can branch after dropping symbolic attributes, making path-condition evidence
 * incomplete for this inference.
 *
 * <p>The accepted residual risk is a boolean method whose path condition names a widened parameter
 * for some branch unrelated to the returned value while the return itself is pass-through. That is
 * still guarded by the later validation net, but it is not solved here: this gate is intentionally a
 * small generation-time policy that rejects claims without oracle evidence and leaves completeness
 * recovery to boxed output capture rather than weakening the license.
 */
public final class WideningLicense {
    public static final String ORACLE_NOT_WIDENABLE = "ORACLE_NOT_WIDENABLE";

    private static final Verdict WIDEN = new Verdict(true, null);
    private static final Verdict REFUSE = new Verdict(false, ORACLE_NOT_WIDENABLE);

    private WideningLicense() {
    }

    /**
     * Evaluate the widening license from the already-classified output shape, the tested method
     * return type, the set of parameter names that will be generated, the set of parameter names
     * mentioned anywhere in the flattened path-condition clauses, the persisted concretization
     * event count, and the post-event divergence risk flag. A null event count is an old-row absence
     * and is treated as no events. A null risk flag means the event risk is unknown.
     */
    public static Verdict evaluate(
        OutputSpecClass outputSpecClass,
        String testedMethodReturnType,
        Set<String> widenedParameterNames,
        Set<String> pathConditionParameterNames,
        Integer concretizationEvents,
        Boolean postConcretizationDivergenceRisk
    ) {
        Objects.requireNonNull(outputSpecClass, "outputSpecClass");
        Set<String> widened = widenedParameterNames == null ? Collections.emptySet() : widenedParameterNames;
        Set<String> pathNames = pathConditionParameterNames == null ? Collections.emptySet() : pathConditionParameterNames;

        boolean hasConcretizationEvents = concretizationEvents != null && concretizationEvents > 0;
        if (outputSpecClass == OutputSpecClass.SYMBOLIC || outputSpecClass == OutputSpecClass.CONSTANT) {
            return WIDEN;
        }
        if (outputSpecClass == OutputSpecClass.EXCEPTION) {
            if (hasConcretizationEvents && !Boolean.FALSE.equals(postConcretizationDivergenceRisk)) {
                return REFUSE;
            }
            return pathNames.isEmpty() || pathNames.containsAll(widened) ? WIDEN : REFUSE;
        }
        if (outputSpecClass != OutputSpecClass.NULL_CONCRETE) {
            return REFUSE;
        }
        if (!isBooleanReturn(testedMethodReturnType)) {
            return REFUSE;
        }
        if (hasConcretizationEvents) {
            return REFUSE;
        }
        if (widened.isEmpty() || pathNames.isEmpty()) {
            return REFUSE;
        }
        return pathNames.containsAll(widened) ? WIDEN : REFUSE;
    }

    /** Collects the path-condition parameter-name view consumed by {@link #evaluate}. */
    public static Set<String> referencedParameterNames(Collection<ConstraintClause> clauses) {
        if (clauses == null || clauses.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> referenced = new LinkedHashSet<>();
        for (ConstraintClause clause : clauses) {
            if (clause.getExpression() != null) {
                clause.getExpression().accept(new VariableNameCollector(referenced));
            }
        }
        return referenced;
    }

    private static boolean isBooleanReturn(String returnType) {
        return "boolean".equals(returnType) || "java.lang.Boolean".equals(returnType);
    }

    public static final class Verdict {
        private final boolean allowsWidening;
        private final String exclusionInfo;

        private Verdict(boolean allowsWidening, String exclusionInfo) {
            this.allowsWidening = allowsWidening;
            this.exclusionInfo = exclusionInfo;
        }

        public boolean allowsWidening() {
            return this.allowsWidening;
        }

        public String getExclusionInfo() {
            return this.exclusionInfo;
        }
    }
}
