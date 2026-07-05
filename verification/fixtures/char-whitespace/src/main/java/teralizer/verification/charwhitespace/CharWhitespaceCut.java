package teralizer.verification.charwhitespace;

public class CharWhitespaceCut {
    public boolean branchesOnWhitespace(char c) {
        if (Character.isWhitespace(c)) {
            return true;
        }
        return false;
    }
}
