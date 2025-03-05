package teralizer.example;

import java.lang.ArithmeticException;

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

    public byte addByte(byte a, byte b) {
        return (byte) (a + b);
    }

    public short subtractShort(short a, short b) {
        return (short) (a - b);
    }

    public long multiplyLong(long a, long b) {
        return a * b;
    }

    public float divideFloat(float a, float b) throws ArithmeticException {
        if (b != 0) {
            return a / b;
        } else {
            throw new ArithmeticException("Cannot divide by zero");
        }
    }

    public int reducePositive(int x) throws IllegalArgumentException {
        if (x < 0) throw new IllegalArgumentException("Negative value");
        return x - 1;
    }

    public int castToInt(float a) {
        return (int) a;
    }
}
