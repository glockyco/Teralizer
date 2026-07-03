package teralizer.processing.task;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import teralizer.spoon.analysis.MutResolution;
import teralizer.util.TypeCapability;

/**
 * Maps a resolver outcome onto an observation row without touching the database.
 *
 * <p>The pipeline records provenance for every assertion, including picks that are not safe to
 * generalize. Declaration-dependent signature fields are therefore filled only when Spoon has a
 * source-model method declaration; that keeps telemetry complete without disguising library or
 * unresolved targets as generalization-grade assertions.
 */
final class MutResolutionObservationMapper {

    private MutResolutionObservationMapper() {
    }

    static void map(MutResolution resolution, long projectId, long testId, long assertionId,
                    Gson gson, MutResolutionObservationRecord record) {
        record.setAssertionId(assertionId);
        record.setProjectId(projectId);
        record.setTestId(testId);

        record.setStatus(resolution.getStatus().name());
        record.setConfidenceTier(resolution.getTier().name());
        record.setDecidingSignal(resolution.getDecidingSignal().name());

        List<String> corroborators = new ArrayList<>();
        for (MutResolution.Corroborator corroborator : resolution.getCorroborators()) {
            corroborators.add(corroborator.name());
        }
        record.setCorroboratingSignals(corroborators.isEmpty() ? null : gson.toJson(corroborators));
        record.setNoPickReason(resolution.getNoPickReason() == null ? null : resolution.getNoPickReason().name());
        record.setCandidateCount(resolution.getCandidateCount());

        CtInvocation<?> pick = resolution.getPick();
        if (pick != null) {
            record.setResolvedCallSource(pick.toString());
            record.setResolvedMethodName(pick.getExecutable().getSimpleName());
            CtTypeReference<?> declaring = pick.getExecutable().getDeclaringType();
            record.setResolvedDeclaringType(declaring == null ? null : declaring.getQualifiedName());

            if (pick.getExecutable().getDeclaration() instanceof CtMethod<?>) {
                CtMethod<?> method = (CtMethod<?>) pick.getExecutable().getDeclaration();
                List<String> parameterTypes = new ArrayList<>();
                boolean anyParamSupported = false;
                for (CtParameter<?> parameter : method.getParameters()) {
                    String qualifiedName = parameter.getType().getQualifiedName();
                    parameterTypes.add(qualifiedName);
                    anyParamSupported |= TypeCapability.supportsGeneratedInput(qualifiedName);
                }
                String returnType = method.getType().getQualifiedName();
                record.setResolvedParameterTypes(gson.toJson(parameterTypes));
                record.setResolvedReturnType(returnType);
                record.setCandidateParamCount(method.getParameters().size());
                record.setCandidateParamSupported(anyParamSupported);
                record.setCandidateReturnSupported(TypeCapability.supportsReturnValue(returnType));
            }
        }

        record.setInspectorUnwrapped(resolution.isInspectorUnwrapped());
        record.setShallowInspectorPick(resolution.isShallowInspectorPick());
        record.setFocalType(resolution.getFocalType());
        record.setFocalTypeSource(resolution.getFocalSource().name());
        record.setFocalAgreement(resolution.getFocalAgreement());

        if (!resolution.getAlternatives().isEmpty()) {
            record.setCandidateDetails(gson.toJson(resolution.getAlternatives()));
        }

        record.setActualShape(resolution.getActualShape().name());
        record.setReceiverProvenance(resolution.getReceiverProvenance().name());
    }
}
