package teralizer.spoon.generalization;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

public class FirstValueArbitraryFactory {

    public static CtClass<?> createFirstValueArbitraryClass() {
        String classDefinition =
            "private static class FirstValueArbitrary<T> implements net.jqwik.api.Arbitrary<T> {\n" +
                "    private T firstValue;\n" +
                "\n" +
                "    private net.jqwik.api.Arbitrary<T> delegate;\n" +
                "\n" +
                "    public FirstValueArbitrary(T firstValue, net.jqwik.api.Arbitrary<T> delegate) {\n" +
                "        this.firstValue = firstValue;\n" +
                "        this.delegate = delegate;\n" +
                "    }\n" +
                "\n" +
                "    public net.jqwik.api.RandomGenerator<T> generator(int genSize) {\n" +
                "        return delegate.generator(genSize);\n" +
                "    }\n" +
                "\n" +
                "    public net.jqwik.api.EdgeCases<T> edgeCases(int maxEdgeCases) {\n" +
                "        final java.util.List<java.util.function.Supplier<net.jqwik.api.Shrinkable<T>>> suppliers = new java.util.ArrayList<java.util.function.Supplier<net.jqwik.api.Shrinkable<T>>>();\n" +
                "        suppliers.add(new java.util.function.Supplier<net.jqwik.api.Shrinkable<T>>() {\n" +
                "            public net.jqwik.api.Shrinkable<T> get() {\n" +
                "                return new net.jqwik.engine.properties.shrinking.Unshrinkable<T>(new java.util.function.Supplier<T>() {\n" +
                "                    public T get() {\n" +
                "                        return firstValue;\n" +
                "                    }\n" +
                "                }, net.jqwik.api.ShrinkingDistance.MIN);\n" +
                "            }\n" +
                "        });\n" +
                "        suppliers.addAll(delegate.edgeCases().suppliers());\n" +
                "        return new net.jqwik.api.EdgeCases<T>() {\n" +
                "            public java.util.List<java.util.function.Supplier<net.jqwik.api.Shrinkable<T>>> suppliers() {\n" +
                "                return suppliers;\n" +
                "            }\n" +
                "            public java.util.Iterator<net.jqwik.api.Shrinkable<T>> iterator() {\n" +
                "                java.util.List<net.jqwik.api.Shrinkable<T>> shrinkables = new java.util.ArrayList<net.jqwik.api.Shrinkable<T>>();\n" +
                "                for (java.util.function.Supplier<net.jqwik.api.Shrinkable<T>> supplier : suppliers()) {\n" +
                "                    shrinkables.add(supplier.get());\n" +
                "                }\n" +
                "                return shrinkables.iterator();\n" +
                "            }\n" +
                "        };\n" +
                "    }\n" +
                "}";

        return Launcher.parseClass(classDefinition);
    }
}
