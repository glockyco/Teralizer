package teralizer.verification.parsepredicate;

public class ParsePredicateCut {
    public boolean isIntegerParseable(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isDoubleParseable(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public int parsedPlusOne(String value) {
        return Integer.parseInt(value) + 1;
    }
}
