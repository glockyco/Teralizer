package teralizer.spike.r1viability;

public final class Box {
    private final int v;

    private Box(int v) {
        this.v = v;
    }

    public static Box of(int v) {
        return new Box(v);
    }

    public Box twice() {
        return new Box(v * 2);
    }

    public int value() {
        return v;
    }
}
