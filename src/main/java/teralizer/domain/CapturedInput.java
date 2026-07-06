package teralizer.domain;

/**
 * One captured wrapper argument: the wrapper parameter's name paired with the concrete
 * {@link Value} observed at extraction time. The name is the mapping key downstream — the
 * wrapper signature is [_target_?][generalizable inputs][lifted locals][scope-bound
 * constructions], and only the generalizable inputs map onto tested-method parameters, so
 * positional guessing is never sound.
 */
public final class CapturedInput {

    private final String name;
    private final Value value;

    public CapturedInput(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public Value getValue() {
        return this.value;
    }
}
