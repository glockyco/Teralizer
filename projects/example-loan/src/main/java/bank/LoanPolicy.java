package bank;

public class LoanPolicy {
  static final int FULL = 60, PARTIAL = 5;

  public static int approvedAmount(int score, int amount) {
    if (amount <= 0) throw new IllegalArgumentException();
    if (score >= 80) return Math.min(amount, FULL);
    if (score >= 50) return Math.min(amount, PARTIAL);
    return 0;
  }
}
