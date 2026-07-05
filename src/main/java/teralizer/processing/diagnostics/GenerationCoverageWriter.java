package teralizer.processing.diagnostics;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GenerationClauseRecord;
import org.jooq.generated.tables.records.GenerationParameterRecord;
import teralizer.domain.MethodParameter;
import teralizer.domain.ShapeFolder;
import teralizer.domain.TypeDomain;
import teralizer.jqwik.planning.ConstraintClause;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.ParameterGenerationPlan;
import teralizer.transformer.VariableNameCollector;

/**
 * Persists generation-coverage telemetry from an {@link InputGenerationPlan}: one
 * {@code generation_clause} row per clause with its canonical shape and consumed status, and one
 * {@code generation_parameter} row per parameter with its symbolic-spec presence and
 * representation. Written at generation time because the plan's consumed-clause view is the
 * authoritative source and is not reconstructible post-hoc.
 */
public final class GenerationCoverageWriter {

    private GenerationCoverageWriter() {
    }

    public static void write(
        DSLContext create,
        long generalizationId,
        InputGenerationPlan inputGenerationPlan,
        List<MethodParameter> parameters
    ) {
        Map<String, String> parameterTypes = parameters.stream()
            .collect(Collectors.toMap(MethodParameter::getName, MethodParameter::getType, (left, right) -> left, LinkedHashMap::new));
        Set<Integer> consumedClauseIds = inputGenerationPlan.getConsumedClauseIds();
        ShapeFolder shapeFolder = new ShapeFolder();

        for (ConstraintClause clause : inputGenerationPlan.getClauses()) {
            String parameterName = primaryParameterName(clause, parameterTypes.keySet());
            GenerationClauseRecord record = create.newRecord(Tables.GENERATION_CLAUSE);
            record.setGeneralizationId(generalizationId);
            record.setParameterName(parameterName);
            record.setTypeDomain(TypeDomain.from(parameterTypes.get(parameterName)).name());
            record.setShape(clause.getExpression().fold(shapeFolder));
            record.setConsumed(consumedClauseIds.contains(clause.getId()));
            record.store();
        }

        for (ParameterGenerationPlan parameterPlan : inputGenerationPlan.getParameterPlans()) {
            Set<Integer> parameterClauseIds =
                referencedClauseIds(inputGenerationPlan.getClauses(), parameterPlan.getParameter().getName());
            boolean symbolicSpecPresent = !parameterClauseIds.isEmpty();
            boolean encoded = !parameterPlan.getConsumedClauseIds().isEmpty();
            boolean residual = parameterClauseIds.stream().anyMatch(inputGenerationPlan.getResidualClauseIds()::contains);

            GenerationParameterRecord record = create.newRecord(Tables.GENERATION_PARAMETER);
            record.setGeneralizationId(generalizationId);
            record.setName(parameterPlan.getParameter().getName());
            record.setDeclaredType(parameterPlan.getParameter().getType());
            record.setTypeDomain(parameterPlan.getDomain().name());
            record.setSymbolicSpecPresent(symbolicSpecPresent);
            record.setRepresentation(parameterRepresentation(symbolicSpecPresent, encoded, residual));
            record.store();
        }
    }

    private static String primaryParameterName(ConstraintClause clause, Set<String> parameterNames) {
        Set<String> referenced = referencedVariableNames(clause);
        for (String parameterName : parameterNames) {
            if (referenced.contains(parameterName)) {
                return parameterName;
            }
        }
        return referenced.stream().findFirst().orElse("");
    }

    private static Set<Integer> referencedClauseIds(List<ConstraintClause> clauses, String parameterName) {
        Set<Integer> clauseIds = new LinkedHashSet<>();
        for (ConstraintClause clause : clauses) {
            if (referencedVariableNames(clause).contains(parameterName)) {
                clauseIds.add(clause.getId());
            }
        }
        return clauseIds;
    }

    private static Set<String> referencedVariableNames(ConstraintClause clause) {
        Set<String> names = new LinkedHashSet<>();
        if (clause.getExpression() != null) {
            clause.getExpression().accept(new VariableNameCollector(names));
        }
        return names;
    }

    private static String parameterRepresentation(boolean symbolicSpecPresent, boolean encoded, boolean residual) {
        if (!symbolicSpecPresent) {
            return "none";
        }
        if (encoded) {
            return "encoded";
        }
        if (residual) {
            return "residual";
        }
        return "none";
    }
}
