package teralizer.example;

public class MyMath {
    public int add(int x, int y) {
        return x + y;
    }

    public int abs(int x) {
        return x >= 0 ? x : -x;
    }

    public boolean isZero(int x) {
        return x == 0;
    }

    public boolean isEqual(int x, int y) {
        return x == y;
    }
}
