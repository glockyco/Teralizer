package benchmarks.CLEVER.pos.Neq;
public class newV {
    public int lib(int x) {
      int counter = 1;
      while (x < 0) {
        x++;
        counter++;
      }
      return counter;
    }
    public int client(int x){
      if (x > 0) {
        return -lib(-x);
      }else{
        return lib(x);
      }
    }
}