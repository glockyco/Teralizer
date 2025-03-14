package benchmarks.CLEVER.getSign2.Neq;
public class newV {
	public int lib(int x) {
		if (x <= 0)
		   return -1;
		else
		   return 1;
	}
	public int client(int x){
		return lib(x);
	}
}