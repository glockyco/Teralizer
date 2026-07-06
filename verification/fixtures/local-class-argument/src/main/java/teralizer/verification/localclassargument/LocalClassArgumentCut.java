package teralizer.verification.localclassargument;

public class LocalClassArgumentCut {
    public boolean isLarge(Object marker, int value) {
        if (marker == null) {
            return false;
        }
        return value > 10;
    }
}
