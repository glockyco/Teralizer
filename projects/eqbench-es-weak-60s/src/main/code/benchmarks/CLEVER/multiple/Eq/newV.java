package benchmarks.CLEVER.multiple.Eq;
public class newV {
	public int lib(int x) {
		return x % 6;
	}
	public int client(int x){
		x = x*5*6;
		if (lib(x)==0){
			return 1;
		}else{
			return 0;
		}
	}
}