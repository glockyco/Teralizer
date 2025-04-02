package teralizer.processing.filter;

public abstract class AbstractFilter implements Filter {

    public String getName() {
        return this.getClass().getName();
    }
}
