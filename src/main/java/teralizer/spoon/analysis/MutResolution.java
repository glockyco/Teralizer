package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.code.CtInvocation;

/**
 * Result of method-under-test resolution for one assertion. Always present: the resolver never
 * abstains silently; "no candidate" is an explicit status.
 */
public final class MutResolution {

    public enum Status { RESOLVED, CHARACTERIZATION_ONLY, NONE }

    public enum Tier { T1_PROVEN, T2_CORROBORATED, T3_SINGLE_WEAK, T4_GUESS, T5_NONE }

    public enum Signal {
        DIRECT_ACTUAL_CALL,
        LOCAL_VARIABLE_PRODUCER,
        FIELD_PRODUCER,
        SUBEXPRESSION_PRODUCER,
        INSPECTOR_UNWRAP,
        UNIQUE_PRODUCER_ELIMINATION,
        ASSERT_THROWS_LAMBDA,
        RANKED_GUESS,
        NONE
    }

    public enum Corroborator { NAME_MATCH, FOCAL_CLASS_MEMBER }

    public enum NoPickReason {
        LIBRARY_DECLARATION,
        UNRESOLVED_SOURCE_DECLARATION,
        UNPATHABLE_SOURCE_DECLARATION,
        NO_VISIBLE_CALL,
        UNSUPPORTED_ASSERTION_SHAPE
    }

    public enum FocalSource { PATH_AND_NAME, NAME_ONLY, PATH_ONLY, NONE }

    public enum ActualShape {
        LITERAL, VARIABLE, FIELD_ACCESS, SINGLE_CALL, CHAINED_CALLS_END0ARG,
        CHAINED_CALLS_ENDNARG, CTOR_ONLY, CTOR_RECEIVER_CALL, OPERATOR_COMPOSITE,
        ARRAY_INDEX, LAMBDA_OR_METHODREF, NONE
    }

    public enum ReceiverProvenance {
        INLINE_CTOR, LOCAL_CTOR, LOCAL_CTOR_MUTATED, LOCAL_OTHER, FIELD, PARAM_OR_STATIC, NONE
    }

    /** A losing candidate, recorded for T4 provenance. */
    public static final class Candidate {
        public final String methodName;
        public final String declaringType;
        public final String callSource;

        public Candidate(String methodName, String declaringType, String callSource) {
            this.methodName = methodName;
            this.declaringType = declaringType;
            this.callSource = callSource;
        }
    }

    private final Status status;
    private final Tier tier;
    private final Signal decidingSignal;
    private final Set<Corroborator> corroborators;
    private final NoPickReason noPickReason;
    private final CtInvocation<?> pick;
    private final List<Candidate> alternatives;
    private final int candidateCount;
    private final boolean inspectorUnwrapped;
    private final boolean shallowInspectorPick;
    private final String focalType;
    private final FocalSource focalSource;
    private final Boolean focalAgreement;
    private final ActualShape actualShape;
    private final ReceiverProvenance receiverProvenance;

    MutResolution(
        Status status,
        Tier tier,
        Signal decidingSignal,
        Set<Corroborator> corroborators,
        NoPickReason noPickReason,
        CtInvocation<?> pick,
        List<Candidate> alternatives,
        int candidateCount,
        boolean inspectorUnwrapped,
        boolean shallowInspectorPick,
        String focalType,
        FocalSource focalSource,
        Boolean focalAgreement,
        ActualShape actualShape,
        ReceiverProvenance receiverProvenance
    ) {
        this.status = status;
        this.tier = tier;
        this.decidingSignal = decidingSignal;
        this.corroborators = corroborators == null ? EnumSet.noneOf(Corroborator.class) : corroborators;
        this.noPickReason = noPickReason;
        this.pick = pick;
        this.alternatives = alternatives == null ? new ArrayList<Candidate>() : alternatives;
        this.candidateCount = candidateCount;
        this.inspectorUnwrapped = inspectorUnwrapped;
        this.shallowInspectorPick = shallowInspectorPick;
        this.focalType = focalType;
        this.focalSource = focalSource;
        this.focalAgreement = focalAgreement;
        this.actualShape = actualShape;
        this.receiverProvenance = receiverProvenance;
    }

    MutResolution withTopology(ActualShape shape, ReceiverProvenance provenance) {
        return new MutResolution(this.status, this.tier, this.decidingSignal, this.corroborators,
            this.noPickReason, this.pick, this.alternatives, this.candidateCount,
            this.inspectorUnwrapped, this.shallowInspectorPick, this.focalType, this.focalSource,
            this.focalAgreement, shape, provenance);
    }

    public Status getStatus() { return this.status; }
    public Tier getTier() { return this.tier; }
    public Signal getDecidingSignal() { return this.decidingSignal; }
    public Set<Corroborator> getCorroborators() { return Collections.unmodifiableSet(this.corroborators); }
    public NoPickReason getNoPickReason() { return this.noPickReason; }
    /** The picked test-side call; null iff status == NONE. */
    public CtInvocation<?> getPick() { return this.pick; }
    public List<Candidate> getAlternatives() { return Collections.unmodifiableList(this.alternatives); }
    public int getCandidateCount() { return this.candidateCount; }
    public boolean isInspectorUnwrapped() { return this.inspectorUnwrapped; }
    public boolean isShallowInspectorPick() { return this.shallowInspectorPick; }
    public String getFocalType() { return this.focalType; }
    public FocalSource getFocalSource() { return this.focalSource; }
    public Boolean getFocalAgreement() { return this.focalAgreement; }
    public ActualShape getActualShape() { return this.actualShape == null ? ActualShape.NONE : this.actualShape; }
    public ReceiverProvenance getReceiverProvenance() { return this.receiverProvenance == null ? ReceiverProvenance.NONE : this.receiverProvenance; }
}
