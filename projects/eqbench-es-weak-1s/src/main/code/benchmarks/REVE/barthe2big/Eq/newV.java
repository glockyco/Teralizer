package benchmarks.REVE.barthe2big.Eq;
public class newV {
	public int f(int n) {
		int i = 1;
		int x = 1;
		while (i <= n) {
			x = x * 5;
			i++;
		}
		i = 1;
		while (i <= n) {
			x = x + i;
			i++;
		}
		return x;
	}
}