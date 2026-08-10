package teralizer.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPF symbolic-analysis backend settings for one probe: the decision procedure
 * ({@code symbolic.dp}), the floating-point theory toggle ({@code symbolic.fp}), the bit-vector
 * width ({@code symbolic.bvlength}), and whether strings are symbolized
 * ({@code symbolic.strings}).
 *
 * <p>Teralizer runs SPF in single-path constraint-collection mode and does not
 * use SPF for solver-driven path exploration or input generation. These settings
 * control how operations are represented symbolically while the specification is
 * collected along the concrete path. Under the rational-real {@link #defaultProfile()},
 * bit-level operations such as {@code Double.doubleToRawLongBits} cannot be
 * represented and concretize into the collected spec. The {@link #rawBits()}
 * profile (IEEE bit-vector + FP theory) keeps them symbolic.
 */
public final class SpfSymbolicConfig {
    private final String dp;
    private final boolean fp;
    private final int bvLength;
    private final boolean strings;

    private SpfSymbolicConfig(String dp, boolean fp, int bvLength, boolean strings) {
        this.dp = dp;
        this.fp = fp;
        this.bvLength = bvLength;
        this.strings = strings;
    }

    /** Rational-real backend ({@code z3}) — the standard profile for numeric/boolean MUTs. */
    public static SpfSymbolicConfig defaultProfile() {
        return new SpfSymbolicConfig("z3", false, 32, false);
    }

    /** IEEE bit-vector + FP backend ({@code z3bitvector}, 64-bit) — keeps raw-bits operations symbolic. */
    public static SpfSymbolicConfig rawBits() {
        return new SpfSymbolicConfig("z3bitvector", true, 64, false);
    }

    /** This profile with symbolic strings toggled — strings are orthogonal to the numeric backend. */
    public SpfSymbolicConfig withStrings(boolean strings) {
        return new SpfSymbolicConfig(this.dp, this.fp, this.bvLength, strings);
    }

    /**
     * The {@code jpf-config.vm} bindings this profile owns. The template renders under
     * {@code runtime.references.strict}, so a binding it names and no caller supplies is a render
     * failure rather than a silent default. Every site that fills the template takes them from
     * here, so adding a setting cannot leave one of them behind.
     */
    public Map<String, Object> templateBindings() {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("symbolicDp", this.dp);
        bindings.put("symbolicFp", this.fp);
        bindings.put("symbolicBvLength", this.bvLength);
        bindings.put("symbolicStrings", this.strings);
        return bindings;
    }

    public String getDp() {
        return this.dp;
    }

    public boolean isFp() {
        return this.fp;
    }

    public int getBvLength() {
        return this.bvLength;
    }

    public boolean isStrings() {
        return this.strings;
    }
}
