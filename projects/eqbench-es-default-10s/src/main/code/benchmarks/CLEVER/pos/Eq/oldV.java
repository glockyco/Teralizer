package benchmarks.CLEVER.pos.Eq;
public class oldV {
    public int lib(int x) {
      int counter = 0;
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