package teralizer.util;

import teralizer.jqwik.planning.DomainPlanner;
import teralizer.jqwik.planning.DomainPlanners;
import teralizer.jqwik.planning.TypeDomain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Single source of truth for type capability. Input-generation support is derived from the
 * registered {@link DomainPlanner}s, so adding a planner is the only thing needed to support a type.
 * Return-value support (a type usable as the symbolic output oracle) is a distinct query, kept
 * separate from input support even though the two domain sets currently coincide.
 *
 * <p>Names are canonicalized through {@link TypeDomain#from(String)}, which recognizes primitives
 * and qualified wrapper names; unrecognized/simple-wrapper/object names map to OBJECT (unsupported).
 */
public final class TypeCapability {
    private static final Set<TypeDomain> INPUT_DOMAINS = computeInputDomains();
    private static final Set<TypeDomain> RETURN_DOMAINS = EnumSet.copyOf(INPUT_DOMAINS);

    private TypeCapability() {
    }

    public static boolean supportsGeneratedInput(String type) {
        return INPUT_DOMAINS.contains(TypeDomain.from(type));
    }

    public static boolean supportsReturnValue(String type) {
        return RETURN_DOMAINS.contains(TypeDomain.from(type));
    }

    private static Set<TypeDomain> computeInputDomains() {
        EnumSet<TypeDomain> domains = EnumSet.noneOf(TypeDomain.class);
        for (TypeDomain domain : TypeDomain.values()) {
            for (DomainPlanner planner : DomainPlanners.REGISTERED) {
                if (planner.supports(domain)) {
                    domains.add(domain);
                    break;
                }
            }
        }
        return domains;
    }
}
