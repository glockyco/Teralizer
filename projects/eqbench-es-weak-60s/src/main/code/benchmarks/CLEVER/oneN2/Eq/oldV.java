package benchmarks.CLEVER.oneN2.Eq;
public class oldV {
    public int lib(int x){
      if (x > 10)
        return 11;
      else
        return x;
    }
    public int client(int x){
      if (x > lib(x))
        return x;
      else
        return lib(x);
    }
}