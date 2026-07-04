package teralizer.verification.stringsoundset;

public class StringSoundSetCut {
    public boolean equalsFoo(String value) {
        return value.equals("foo");
    }

    public boolean hasNonNegativeLength(String value) {
        return value.length() >= 0;
    }

    public boolean isEmptyValue(String value) {
        return value.isEmpty();
    }

    public int compareToFoo(String value) {
        return value.compareTo("foo");
    }
}
