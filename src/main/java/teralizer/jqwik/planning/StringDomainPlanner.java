package teralizer.jqwik.planning;

import teralizer.domain.ConstantString;
import teralizer.domain.Expression;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Value;
import teralizer.domain.VariableString;
import teralizer.transformer.ModelToJavaTransformer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plans generation for a symbolic {@link String} parameter. Builds an arbitrary that <em>satisfies</em>
 * the captured positive String constraints so generation is practical rather than leaving the
 * residual filter to reject nearly every random string:
 *
 * <ul>
 *   <li>an equality ({@code s.equals("x")}) collapses the domain to {@code Arbitraries.of("x")};</li>
 *   <li>{@code startsWith}/{@code endsWith}/{@code contains} on a literal fragment are enforced by a
 *       {@code .map} that composes the fragment around a bounded random core.</li>
 * </ul>
 *
 * <p>Generated strings are always non-null and length-bounded. A clause id is marked consumed only
 * when the emitted arbitrary <em>structurally</em> enforces it, so anything not construction-enforced
 * (negations, non-literal operands, and any fragment left unenforced by the equality branch) stays
 * with the unconditional full-predicate residual filter the factory always applies. String returns
 * are not yet an output oracle (see {@link #supportsReturn}).
 */
public class StringDomainPlanner implements DomainPlanner {

    private static final int MAX_LENGTH = 16;

    @Override
    public boolean supports(TypeDomain domain) {
        return domain == TypeDomain.STRING;
    }

    @Override
    public boolean supportsReturn(TypeDomain domain) {
        // String inputs are generatable, but a symbolic String return oracle is not captured yet,
        // so String is not usable as a return type until return capture lands.
        return false;
    }

    @Override
    public ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context) {
        String name = parameter.getName();
        Optional<Value> argument = context.getArguments().containsKey(name)
            ? Optional.of(context.getArguments().get(name))
            : Optional.empty();

        DerivedStringConstraints derived = deriveConstraints(context.getClauses(), name);

        String body;
        Set<Integer> consumed = new LinkedHashSet<>();
        if (derived.equality != null) {
            // A single fixed value structurally enforces only the equality; any coexisting fragment
            // constraint is left to the residual filter (a real path never combines them
            // unsatisfiably, and the filter stays correct if one somehow did).
            body = "return net.jqwik.api.Arbitraries.of(" + derived.equality + ")";
            consumed.add(derived.equalityId);
        } else {
            List<String> parts = new ArrayList<>();
            if (derived.prefix != null) {
                parts.add(derived.prefix);
                consumed.add(derived.prefixId);
            }
            parts.add("_x_");
            if (derived.contains != null) {
                parts.add(derived.contains);
                consumed.add(derived.containsId);
            }
            if (derived.suffix != null) {
                parts.add(derived.suffix);
                consumed.add(derived.suffixId);
            }
            String base = "net.jqwik.api.Arbitraries.strings().ascii().ofMaxLength(" + MAX_LENGTH + ")";
            body = parts.size() == 1
                ? "return " + base
                : "return " + base + ".map(_x_ -> " + String.join(" + ", parts) + ")";
        }

        String originalValue = argument
            .map(arg -> "(" + arg.getJavaType() + ") (" + new ModelToJavaTransformer().transform(arg) + ")")
            .orElse(null);
        return new ParameterGenerationPlan(parameter, TypeDomain.STRING, new RawJavaRecipe(body), originalValue, consumed);
    }

    /**
     * Extracts the positive literal String constraints on {@code name} from the rendered clauses,
     * keeping each contributing clause id. Only a {@code param op "literal"} shape (variable on the
     * left, string literal on the right — the SPF capture order) is construction-satisfiable; the
     * literal is rendered through the shared transformer so escaping matches everywhere else.
     */
    private static DerivedStringConstraints deriveConstraints(List<ConstraintClause> clauses, String name) {
        DerivedStringConstraints derived = new DerivedStringConstraints();
        ModelToJavaTransformer transformer = new ModelToJavaTransformer();
        for (ConstraintClause clause : clauses) {
            Model expression = clause.getExpression();
            if (!(expression instanceof Operation)) {
                continue;
            }
            Operation operation = (Operation) expression;
            if (!(operation.left instanceof VariableString)
                || !((VariableString) operation.left).name.equals(name)
                || !(operation.right instanceof ConstantString)) {
                continue;
            }
            String literal = transformer.transform((Expression) operation.right);
            switch (operation.op) {
                case EQUALS:
                    derived.equality = literal;
                    derived.equalityId = clause.getId();
                    break;
                case STARTSWITH:
                    derived.prefix = literal;
                    derived.prefixId = clause.getId();
                    break;
                case ENDSWITH:
                    derived.suffix = literal;
                    derived.suffixId = clause.getId();
                    break;
                case CONTAINS:
                    derived.contains = literal;
                    derived.containsId = clause.getId();
                    break;
                default:
                    break;
            }
        }
        return derived;
    }

    private static final class DerivedStringConstraints {
        private String equality;
        private int equalityId;
        private String prefix;
        private int prefixId;
        private String suffix;
        private int suffixId;
        private String contains;
        private int containsId;
    }
}
