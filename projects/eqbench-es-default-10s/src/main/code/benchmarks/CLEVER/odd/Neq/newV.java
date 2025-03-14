package benchmarks.CLEVER.odd.Neq;
public class newV {
    public int lib(int x) {
        int counter = 0;
        while (x % 2 == 0){
            x = x/2;
            counter++;
        }
        return counter+1;
    }
    public int client(int x){
        if (lib(x)==0){
            return 1;
        }else{
            return 0;
        }
    }
}