package teralizer.verification.filterdegenerate;

public class FilterDegenerateCut {
    public int moduloResidue(int value) {
        if (value % 9973 == 42) {
            return value + 1;
        }
        return -1;
    }
}
