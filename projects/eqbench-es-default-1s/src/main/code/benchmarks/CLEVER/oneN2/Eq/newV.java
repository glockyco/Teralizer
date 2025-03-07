package benchmarks.CLEVER.oneN2.Eq;
public class newV {
	public int lib(int x){
		if (x > 11)//change
			return 11;
		else
			return x-1;//change
	}
	public int client(int x){
		if (x > lib(x))
			return x;
		else
			return lib(x);
	}
}