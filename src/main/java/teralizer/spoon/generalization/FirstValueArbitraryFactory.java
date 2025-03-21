package teralizer.spoon.generalization;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

public class FirstValueArbitraryFactory {

    public static CtClass<?> createFirstValueArbitraryClass() {
        String classDefinition =
            "private static class FirstValueArbitrary<T> implements net.jqwik.api.Arbitrary<T> {\n" +
                "    private T firstValue;\n" +
                "    private net.jqwik.api.Arbitrary<T> delegate;\n" +
                "\n" +
                "    public FirstValueArbitrary(T firstValue, net.jqwik.api.Arbitrary<T> delegate) {\n" +
                "        this.firstValue = firstValue;\n" +
                "        this.delegate = delegate;\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public net.jqwik.api.RandomGenerator<T> generator(int genSize) {\n" +
                "        return delegate.generator(genSize);\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public net.jqwik.api.EdgeCases<T> edgeCases(int maxEdgeCases) {\n" +
                "        java.util.List<java.util.function.Supplier<net.jqwik.api.Shrinkable<T>>> suppliers = new java.util.ArrayList<>();\n" +
                "        suppliers.add(() -> net.jqwik.api.Shrinkable.unshrinkable(firstValue));\n" +
                "        suppliers.addAll(delegate.edgeCases().suppliers());\n" +
                "        return () -> suppliers;\n" +
                "    }\n" +
                "}";

        return Launcher.parseClass(classDefinition);
    }
}
