package bank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoanPolicyTest {
  @Test void fullApproval() {
    assertEquals(20, LoanPolicy.approvedAmount(90, 20));
  }
  @Test void partialApproval() {
    assertEquals(5, LoanPolicy.approvedAmount(60, 8));
  }
  @Test void rejection() {
    assertEquals(0, LoanPolicy.approvedAmount(30, 1));
  }
}
