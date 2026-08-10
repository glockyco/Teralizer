package teralizer.spoon.analysis;

import java.util.Arrays;
import java.util.List;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.util.SpfSymbolicConfig;

/**
 * Chooses the SPF symbolic-analysis backend for a tested method by detecting the
 * operations it performs, rather than matching method names against an allowlist.
 *
 * <p>Teralizer collects the specification along the concrete path. Under the
 * rational-real default backend, bit-level floating-point operations such as
 * {@code Double.doubleToRawLongBits} cannot be represented symbolically and
 * concretize into the spec. A method performing such an operation needs the
 * IEEE bit-vector + FP backend ({@link SpfSymbolicConfig#rawBits()}) to keep the
 * operation symbolic. Every other method uses {@link SpfSymbolicConfig#defaultProfile()}.
 *
 * <p>Detection is conservative and direct-body only. A raw-bits operation reached
 * transitively through a callee is not detected here and falls back to the default
 * backend, where the operation concretizes. An ordinary numeric method is never
 * mis-classified as raw-bits.
 *
 * <p>Scope: raw-bits floating-point conversions only. Integer bitwise and shift
 * operators also require a bit-vector backend, but at a different profile
 * ({@code z3bitvector} at 32-bit, FP off). They belong to that separate profile,
 * gated on evidence that bitwise-operating MUTs are worth supporting.
 */
public final class SpfSymbolicConfigSelector {

    /** Raw-bits FP conversions: each forces an IEEE bit-vector representation SPF's rational-real backend lacks. */
    private static final List<String> RAW_BITS_METHODS = Arrays.asList(
        "doubleToRawLongBits",
        "doubleToLongBits",
        "longBitsToDouble",
        "floatToIntBits",
        "floatToRawIntBits",
        "intBitsToFloat"
    );

    private SpfSymbolicConfigSelector() {
    }

    public static SpfSymbolicConfig select(CtMethod<?> testedMethod) {
        if (testedMethod.getBody() == null) {
            return defaults();
        }
        boolean usesRawBits = testedMethod.getBody()
            .getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .anyMatch(SpfSymbolicConfigSelector::isRawBitsConversion);
        SpfSymbolicConfig base = usesRawBits ? SpfSymbolicConfig.rawBits() : SpfSymbolicConfig.defaultProfile();
        return base.withStrings(hasStringParameter(testedMethod) || returnsString(testedMethod));
    }

    /**
     * The profile a method with no distinguishing feature runs under. Callers that need production's
     * settings without a method to analyze use this rather than restating them, which is how the
     * numeric backend came to be written out by hand in three test sites.
     */
    public static SpfSymbolicConfig defaults() {
        return SpfSymbolicConfig.defaultProfile();
    }

    private static boolean returnsString(CtMethod<?> testedMethod) {
        return testedMethod.getType() != null
            && "java.lang.String".equals(testedMethod.getType().getQualifiedName());
    }

    private static boolean hasStringParameter(CtMethod<?> testedMethod) {
        return testedMethod.getParameters().stream()
            .anyMatch(parameter -> "java.lang.String".equals(parameter.getType().getQualifiedName()));
    }

    private static boolean isRawBitsConversion(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !RAW_BITS_METHODS.contains(executable.getSimpleName())) {
            return false;
        }
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null) {
            return false;
        }
        String qualifiedName = declaringType.getQualifiedName();
        return "java.lang.Double".equals(qualifiedName) || "java.lang.Float".equals(qualifiedName);
    }
}
