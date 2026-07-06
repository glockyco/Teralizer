package teralizer.verification.narrowcastsite;

public class NarrowCastSiteCut {
    public byte clampToByte(int value, byte fallback) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            return fallback;
        }
        return (byte) value;
    }
}
