package teralizer.jqwik.planning;

public class RawJavaRecipe implements GenerationRecipe {
    private final String body;

    public RawJavaRecipe(String body) {
        this.body = body;
    }

    @Override
    public String emit() {
        return this.body;
    }
}
