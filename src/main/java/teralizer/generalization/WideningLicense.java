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
 * output-model boundary. A bytecode literal return can be represented only by the path condition:
 * bytecode branches on the symbolic operands, and the captured return value has no separate return
 * attribute. That case is licensable when the listener confirms that the tested method returned a
 * bytecode literal, every widened parameter appears in at least one path-condition clause, and no
 * concretization event occurred. The clause requirement is evidence that the returned literal is
 * pinned by the same predicate that admits generated inputs.
 *
 * <p>A pass-through boolean reads and returns a stored flag without producing a bytecode literal.
 * Its value can vary with input even when the path condition is empty, so the listener does not mark
 * it as literal and this license refuses the null-concrete oracle. Concretization events also refuse
 * the license because native or modeled boundaries can branch after dropping symbolic attributes,
 * making path-condition evidence incomplete for this inference.

 * <p>What the refusals cost is measured rather than assumed. {@code docs/exclusion-model.md}
 * carries the branch-level distribution over the current corpus. Refusals are overwhelmingly
 * {@code NULL_CONCRETE}, which means the value on the operand stack at the return carried no SPF
 * expression. A value carries an expression when it descends from a symbolic input through
 * instructions that transfer attributes. The heap transfers them in both directions. jpf-core
 * copies the operand attribute onto a field in {@code PutHelper.setField}, and onto an array
 * element in {@code ArrayStoreInstruction}. It reads the attribute back in {@code GETFIELD} and
 * {@code ArrayLoadInstruction}. A field or an array therefore keeps whatever the stored value
 * carried.
 *
 * <p>Three shapes reach the return with a concrete value. A computed boolean returns a bytecode
 * literal, and a literal carries no attribute. The relation stays in the path condition, and this
 * class recovers it from there. A container of literals holds elements that never carried an
 * attribute, so a read returns a plain constant. A loop accumulator over an input-dependent trip
 * count is concrete on each path, and the path condition pins the input to one value.
 *
 * <p>The literal check reads the instruction that ran immediately before the return. A local store
 * puts a load in that position, so {@code return value > 0} is widened and
 * {@code boolean r = value > 0; return r} is refused. {@code NullConcreteRefusalShapeTest} holds
 * each of these shapes.
 *
 * <p>Concretization explains few refusals. Assertions that never reach a native boundary carry a
 * null output model almost as often as those that do. Symbolic models or native peers for the
 * concretized methods therefore recover a small share. Boxed output capture is implemented, and it
 * recovers a small share as well.
 *
 * <p>Do not read the paragraphs above as a cause distribution. They describe when the license is
 * granted, not why it is refused.
 */
public final class WideningLicense {
    public static final String ORACLE_NOT_WIDENABLE = "ORACLE_NOT_WIDENABLE";
    public static final String EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK = "EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK";
    public static final String EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS = "EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS";
    public static final String NULL_CONCRETE_OUTPUT_NOT_LITERAL = "NULL_CONCRETE_OUTPUT_NOT_LITERAL";
    public static final String NULL_CONCRETE_CONCRETIZATION_EVENTS = "NULL_CONCRETE_CONCRETIZATION_EVENTS";
    public static final String NULL_CONCRETE_PARAMETERS_EMPTY = "NULL_CONCRETE_PARAMETERS_EMPTY";
    public static final String NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS = "NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS";

    private static final Verdict WIDEN = new Verdict(true, null, null);
    private static final Verdict EXCEPTION_DIVERGENCE_REFUSAL = refusal(EXCEPTION_CONCRETIZATION_DIVERGENCE_RISK);
    private static final Verdict EXCEPTION_PATH_REFUSAL = refusal(EXCEPTION_PATH_CONDITION_NOT_COVERING_PARAMETERS);
    private static final Verdict NULL_CONCRETE_LITERAL_REFUSAL = refusal(NULL_CONCRETE_OUTPUT_NOT_LITERAL);
    private static final Verdict NULL_CONCRETE_CONCRETIZATION_REFUSAL = refusal(NULL_CONCRETE_CONCRETIZATION_EVENTS);
    private static final Verdict NULL_CONCRETE_PARAMETERS_REFUSAL = refusal(NULL_CONCRETE_PARAMETERS_EMPTY);
    private static final Verdict NULL_CONCRETE_PATH_REFUSAL = refusal(NULL_CONCRETE_PATH_CONDITION_NOT_COVERING_PARAMETERS);

    private static Verdict refusal(String code) {
        return new Verdict(false, ORACLE_NOT_WIDENABLE, code);
    }

    private WideningLicense() {
    }

    /**
     * Evaluate the widening license from the already-classified output shape, whether the tested
     * method returned a bytecode literal, the set of parameter names that will be
     * generated, the set of parameter names
     * mentioned anywhere in the flattened path-condition clauses, the persisted concretization
     * event count, and the post-event divergence risk flag. A null event count is an old-row absence
     * and is treated as no events. A null risk flag means the event risk is unknown.
     */
    public static Verdict evaluate(
        OutputSpecClass outputSpecClass,
        Boolean outputIsLiteral,
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
                return EXCEPTION_DIVERGENCE_REFUSAL;
            }
            return pathNames.isEmpty() || pathNames.containsAll(widened) ? WIDEN : EXCEPTION_PATH_REFUSAL;
        }
        if (!Boolean.TRUE.equals(outputIsLiteral)) {
            return NULL_CONCRETE_LITERAL_REFUSAL;
        }
        if (hasConcretizationEvents) {
            return NULL_CONCRETE_CONCRETIZATION_REFUSAL;
        }
        if (widened.isEmpty() || pathNames.isEmpty()) {
            return NULL_CONCRETE_PARAMETERS_REFUSAL;
        }
        return pathNames.containsAll(widened) ? WIDEN : NULL_CONCRETE_PATH_REFUSAL;
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

    public static final class Verdict {
        private final boolean allowsWidening;
        private final String exclusionInfo;
        private final String wideningRefusalCode;

        private Verdict(boolean allowsWidening, String exclusionInfo, String wideningRefusalCode) {
            this.allowsWidening = allowsWidening;
            this.exclusionInfo = exclusionInfo;
            this.wideningRefusalCode = wideningRefusalCode;
        }

        public boolean allowsWidening() {
            return this.allowsWidening;
        }

        public String getExclusionInfo() {
            return this.exclusionInfo;
        }

        public String getWideningRefusalCode() {
            return this.wideningRefusalCode;
        }
    }
}
