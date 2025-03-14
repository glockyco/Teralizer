package benchmarks.CLEVER.pos.Eq;
public class newV {
	public int lib(int x) {
		if (x < 0){
			return -x;
		}else{
			return x;
		}
	}
	public int client(int x){
		if (x > 0) {
			return -lib(-x);
		}else{
			return lib(x);
		}
	}
}