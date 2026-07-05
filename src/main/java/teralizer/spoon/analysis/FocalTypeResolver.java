package teralizer.spoon.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/** Per-project focal type inference and model indexing. */
public final class FocalTypeResolver {
    private final Map<CtModel, TypeIndex> typeIndexes = new HashMap<>();
    private final Map<CtType<?>, Focal> focalCache = new HashMap<>();

    /**
     * Infers the focal (class-under-test) type from two independent conventions: the test class
     * name with its Test/Tests/IT/ITCase/TestCase affix stripped (FooTest -> Foo, preferring the
     * same package), and the mirrored src/test/java -> src/main/java source path when a real file
     * position exists (virtual models have none). Both agreeing is the strongest source
     * (PATH_AND_NAME); either alone is medium. The focal class never gates or vetoes a dataflow
     * pick — it only scopes the class-relative ranking preference and the membership corroborator.
     */
    Focal resolveFocalType(CtMethod<?> testMethod) {
        if (testMethod == null || testMethod.getDeclaringType() == null) {
            return noFocal();
        }
        CtType<?> testType = testMethod.getDeclaringType();
        Focal cached = this.focalCache.get(testType);
        if (cached != null) {
            return cached;
        }

        String focalSimpleName = stripTestAffix(testType.getSimpleName());
        CtType<?> nameDerived = focalSimpleName == null
            ? null
            : findTypeBySimpleName(testMethod, focalSimpleName, packageName(testType));
        String mirroredPath = realMirrorPath(testType);
        CtType<?> pathDerived = mirroredPath == null ? null : findTypeByPath(testMethod, mirroredPath);
        Focal focal;
        if (nameDerived != null) {
            MutResolution.FocalSource source = pathsEqual(mirroredPath, sourcePath(nameDerived))
                ? MutResolution.FocalSource.PATH_AND_NAME
                : MutResolution.FocalSource.NAME_ONLY;
            focal = new Focal(nameDerived.getQualifiedName(), source);
        } else if (pathDerived != null) {
            focal = new Focal(pathDerived.getQualifiedName(), MutResolution.FocalSource.PATH_ONLY);
        } else {
            focal = noFocal();
        }
        this.focalCache.put(testType, focal);
        return focal;
    }

    CtType<?> findTypeBySimpleName(
        CtMethod<?> testMethod,
        String simpleName,
        String preferredPackage
    ) {
        TypeIndex index = typeIndex(testMethod.getFactory().getModel());
        List<CtType<?>> candidates = index.bySimpleName.get(simpleName);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (CtType<?> type : candidates) {
            if (preferredPackage.equals(packageName(type))) {
                return type;
            }
        }
        return candidates.get(0);
    }

    CtType<?> findTypeByPath(CtMethod<?> testMethod, String mirroredPath) {
        TypeIndex index = typeIndex(testMethod.getFactory().getModel());
        return index.byNormalizedPath.get(normalizePath(mirroredPath));
    }

    private TypeIndex typeIndex(CtModel model) {
        TypeIndex index = this.typeIndexes.get(model);
        if (index == null) {
            index = new TypeIndex(model);
            this.typeIndexes.put(model, index);
        }
        return index;
    }

    static Focal noFocal() {
        return new Focal(null, MutResolution.FocalSource.NONE);
    }

    static String realMirrorPath(CtType<?> testType) {
        SourcePosition position = testType.getPosition();
        if (position == null || !position.isValidPosition() || position.getFile() == null
                || !position.getFile().isFile()) {
            return null;
        }
        return mirrorTestPath(position.getFile().getPath());
    }

    static String sourcePath(CtType<?> type) {
        SourcePosition position = type.getPosition();
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return null;
        }
        return position.getFile().getPath();
    }

    static boolean pathsEqual(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizePath(left).equals(normalizePath(right));
    }

    static String normalizePath(String path) {
        return new File(path).getAbsoluteFile().toURI().normalize().getPath();
    }

    static String packageName(CtType<?> type) {
        if (type == null || type.getPackage() == null || type.getPackage().getQualifiedName() == null) {
            return "";
        }
        return type.getPackage().getQualifiedName();
    }

    /**
     * Mirror a test-source path to its production twin (Methods2Test path matching):
     * src/test/java/<pkg>/FooTest.java -> src/main/java/<pkg>/Foo.java. Returns null when the path
     * is not under src/test or the file name carries no Test/Tests/IT/ITCase/TestCase prefix/suffix.
     */
    static String mirrorTestPath(String testPath) {
        if (testPath == null || !testPath.contains("src/test/java/")) {
            return null;
        }
        int slash = testPath.lastIndexOf('/');
        String dir = testPath.substring(0, slash + 1).replace("src/test/java/", "src/main/java/");
        String file = testPath.substring(slash + 1);
        if (!file.endsWith(".java")) {
            return null;
        }
        String base = file.substring(0, file.length() - ".java".length());
        String stripped = stripTestAffix(base);
        return stripped == null ? null : dir + stripped + ".java";
    }

    /** FooTest/FooTests/FooIT/FooITCase/FooTestCase/TestFoo -> Foo; null when no affix present. */
    static String stripTestAffix(String simpleName) {
        String[] suffixes = { "TestCase", "ITCase", "Tests", "Test", "IT" };
        for (String suffix : suffixes) {
            if (simpleName.endsWith(suffix) && simpleName.length() > suffix.length()) {
                return simpleName.substring(0, simpleName.length() - suffix.length());
            }
        }
        if (simpleName.startsWith("Test") && simpleName.length() > 4) {
            return simpleName.substring(4);
        }
        return null;
    }

    /**
     * Indexes focal-type lookup once per Spoon model instead of linearly scanning every type for
     * every assertion. The index records model encounter order because first-match fallback and
     * first path match are observable determinism contracts, while the per-simple-name list still
     * allows the same-package preference to win before falling back to the encounter-order head.
     */
    static final class TypeIndex {
        final Map<String, List<CtType<?>>> bySimpleName = new LinkedHashMap<>();
        final Map<String, CtType<?>> byNormalizedPath = new LinkedHashMap<>();

        TypeIndex(CtModel model) {
            for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
                List<CtType<?>> namedTypes = this.bySimpleName.get(type.getSimpleName());
                if (namedTypes == null) {
                    namedTypes = new ArrayList<>();
                    this.bySimpleName.put(type.getSimpleName(), namedTypes);
                }
                namedTypes.add(type);

                String sourcePath = sourcePath(type);
                if (sourcePath != null) {
                    String normalizedPath = normalizePath(sourcePath);
                    if (!this.byNormalizedPath.containsKey(normalizedPath)) {
                        this.byNormalizedPath.put(normalizedPath, type);
                    }
                }
            }
        }
    }

    static final class Focal {
        final String qualifiedName;
        final MutResolution.FocalSource source;

        Focal(String qualifiedName, MutResolution.FocalSource source) {
            this.qualifiedName = qualifiedName;
            this.source = source;
        }
    }
}
