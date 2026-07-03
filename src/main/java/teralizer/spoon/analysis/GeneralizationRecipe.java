package teralizer.spoon.analysis;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

/**
 * Persisted contract between method-under-test analysis and the two pipeline stages that replay the
 * chosen oracle expression. Before this value existed, analysis, JPF instrumentation, and jqwik test
 * generation each reconstructed the same set of lifted inputs from CtPaths and class-name string
 * rewrites. The work was deterministic only if every caller resolved the same Spoon nodes and ran
 * the same derivation logic after cloning; no row recorded that agreement. A recipe records the one
 * derivation made immediately after MUT resolution, validates that every stored path can be
 * resolved while ANALYZE_TESTS still has the source model in hand, and provides the single path
 * rewriting seam used when a cloned test class replaces the original class name in Spoon CtPaths.
 */
public final class GeneralizationRecipe {
    public static final int CURRENT_VERSION = 1;
    private static final String SCHEMA = "teralizer.generalization.recipe";

    public enum InputKind {
        METHOD_ARG,
        CTOR_ARG,
        RECEIVER_CTOR_ARG
    }

    public enum PathRole {
        ORACLE_EXPRESSION,
        ORACLE_METHOD,
        INPUT_SITE
    }

    private final int version;
    private final String schema;
    private final String oracleExpressionPath;
    private final String oracleMethodPath;
    private final String oracleType;
    private final List<InputSite> inputSites;

    private GeneralizationRecipe(
        int version,
        String schema,
        String oracleExpressionPath,
        String oracleMethodPath,
        String oracleType,
        List<InputSite> inputSites
    ) {
        this.version = version;
        this.schema = schema;
        this.oracleExpressionPath = oracleExpressionPath;
        this.oracleMethodPath = oracleMethodPath;
        this.oracleType = oracleType;
        this.inputSites = Collections.unmodifiableList(new ArrayList<>(inputSites));
    }

    public static GeneralizationRecipe from(
        CtMethod<?> oracleMethod,
        CtInvocation<?> oracleExpression,
        List<GeneralizableInput> inputs
    ) {
        CtMethod<?> containingMethod = oracleExpression.getParent(CtMethod.class);
        if (containingMethod == null) {
            throw new ResolutionException(PathRole.ORACLE_EXPRESSION, "<parent method>", "Oracle expression has no enclosing method.");
        }

        String oracleExpressionPath = oracleExpression.getPath().relativePath(containingMethod).toString();
        String oracleMethodPath = oracleMethod.getPath().toString();
        String oracleType = oracleMethod.getType() == null ? null : oracleMethod.getType().getQualifiedName();
        List<InputSite> sites = new ArrayList<>();
        for (GeneralizableInput input : inputs) {
            sites.add(InputSite.from(input, containingMethod));
        }

        GeneralizationRecipe recipe = new GeneralizationRecipe(
            CURRENT_VERSION,
            SCHEMA,
            oracleExpressionPath,
            oracleMethodPath,
            oracleType,
            sites
        );
        recipe.resolveAgainst(containingMethod, oracleMethod.getFactory().getModel().getRootPackage());
        return recipe;
    }

    public static GeneralizationRecipe fromJson(Gson gson, String json) {
        GeneralizationRecipe recipe = gson.fromJson(json, GeneralizationRecipe.class);
        if (recipe == null) {
            throw new IllegalArgumentException("Generalization recipe JSON is null.");
        }
        if (recipe.version != CURRENT_VERSION || !SCHEMA.equals(recipe.schema)) {
            throw new IllegalArgumentException("Unsupported generalization recipe schema/version.");
        }
        List<InputSite> sites = recipe.inputSites == null ? Collections.emptyList() : recipe.inputSites;
        return new GeneralizationRecipe(
            recipe.version,
            recipe.schema,
            recipe.oracleExpressionPath,
            recipe.oracleMethodPath,
            recipe.oracleType,
            sites
        );
    }

    public String toJson(Gson gson) {
        return gson.toJson(this);
    }

    public Resolved resolveAgainst(CtMethod<?> containingMethod, CtElement modelRoot) {
        CtInvocation<?> oracleExpression = resolveOne(
            PathRole.ORACLE_EXPRESSION,
            this.oracleExpressionPath,
            containingMethod,
            CtInvocation.class
        );
        CtMethod<?> oracleMethod = resolveOne(PathRole.ORACLE_METHOD, this.oracleMethodPath, modelRoot, CtMethod.class);
        List<GeneralizableInput> inputs = new ArrayList<>();
        for (InputSite site : this.inputSites) {
            CtExpression<?> expression = resolveOne(PathRole.INPUT_SITE, site.path, containingMethod, CtExpression.class);
            inputs.add(GeneralizableInput.fromRecipe(
                site.methodArgumentIndex,
                site.constructorArgumentIndex,
                new MethodParameter(site.type, site.name),
                new MethodArgument(site.type, expression.toString()),
                expression
            ));
        }
        return new Resolved(oracleMethod, oracleExpression, inputs);
    }

    public GeneralizationRecipe rewriteForClone(String originalClassQualifiedName, String cloneClassQualifiedName) {
        return new GeneralizationRecipe(
            this.version,
            this.schema,
            rewriteCtPathForClone(this.oracleExpressionPath, originalClassQualifiedName, cloneClassQualifiedName),
            rewriteCtPathForClone(this.oracleMethodPath, originalClassQualifiedName, cloneClassQualifiedName),
            this.oracleType,
            this.inputSites
        );
    }

    public static String rewriteCtPathForClone(String path, String originalClassQualifiedName, String cloneClassQualifiedName) {
        if (path == null) {
            return null;
        }
        return path.replace(originalClassQualifiedName, cloneClassQualifiedName);
    }

    GeneralizationRecipe withOracleExpressionPath(String path) {
        return new GeneralizationRecipe(
            this.version,
            this.schema,
            path,
            this.oracleMethodPath,
            this.oracleType,
            this.inputSites
        );
    }

    private static <T extends CtElement> T resolveOne(PathRole role, String path, CtElement root, Class<T> expectedType) {
        if (path == null) {
            throw new ResolutionException(role, "<null>", "Recipe path is null.");
        }
        if (root == null) {
            throw new ResolutionException(role, path, "Resolution root is null.");
        }
        try {
            CtPath ctPath = new CtPathStringBuilder().fromString(path);
            List<CtElement> matches = ctPath.evaluateOn(root);
            if (matches.size() != 1) {
                throw new ResolutionException(role, path, "Expected one match but found " + matches.size() + ".");
            }
            CtElement match = matches.get(0);
            if (!expectedType.isInstance(match)) {
                throw new ResolutionException(
                    role,
                    path,
                    "Expected " + expectedType.getSimpleName() + " but resolved " + match.getClass().getSimpleName() + "."
                );
            }
            return expectedType.cast(match);
        } catch (ResolutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResolutionException(role, path, "Could not resolve recipe path.", e);
        }
    }

    public int getVersion() {
        return this.version;
    }

    public String getOracleExpressionPath() {
        return this.oracleExpressionPath;
    }

    public String getOracleMethodPath() {
        return this.oracleMethodPath;
    }

    public String getOracleType() {
        return this.oracleType;
    }

    public List<InputSite> getInputSites() {
        return this.inputSites;
    }

    public static final class InputSite {
        private final String path;
        private final String name;
        private final String type;
        private final InputKind kind;
        private final int methodArgumentIndex;
        private final int constructorArgumentIndex;

        private InputSite(
            String path,
            String name,
            String type,
            InputKind kind,
            int methodArgumentIndex,
            int constructorArgumentIndex
        ) {
            this.path = path;
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.methodArgumentIndex = methodArgumentIndex;
            this.constructorArgumentIndex = constructorArgumentIndex;
        }

        private static InputSite from(GeneralizableInput input, CtMethod<?> containingMethod) {
            InputKind kind;
            if (input.isReceiverConstructorArgument()) {
                kind = InputKind.RECEIVER_CTOR_ARG;
            } else if (input.isConstructorArgument()) {
                kind = InputKind.CTOR_ARG;
            } else {
                kind = InputKind.METHOD_ARG;
            }
            return new InputSite(
                input.getSourceExpression().getPath().relativePath(containingMethod).toString(),
                input.toMethodParameter().getName(),
                input.toMethodParameter().getType(),
                kind,
                input.getMethodArgumentIndex(),
                input.getConstructorArgumentIndex()
            );
        }

        public String getPath() {
            return this.path;
        }

        public String getName() {
            return this.name;
        }

        public String getType() {
            return this.type;
        }

        public InputKind getKind() {
            return this.kind;
        }

        public int getMethodArgumentIndex() {
            return this.methodArgumentIndex;
        }

        public int getConstructorArgumentIndex() {
            return this.constructorArgumentIndex;
        }
    }

    public static final class Resolved {
        private final CtMethod<?> oracleMethod;
        private final CtInvocation<?> oracleExpression;
        private final List<GeneralizableInput> inputs;

        private Resolved(CtMethod<?> oracleMethod, CtInvocation<?> oracleExpression, List<GeneralizableInput> inputs) {
            this.oracleMethod = oracleMethod;
            this.oracleExpression = oracleExpression;
            this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        }

        public CtMethod<?> getOracleMethod() {
            return this.oracleMethod;
        }

        public CtInvocation<?> getOracleExpression() {
            return this.oracleExpression;
        }

        public List<GeneralizableInput> getInputs() {
            return this.inputs;
        }
    }

    public static final class ResolutionException extends RuntimeException {
        private final PathRole role;
        private final String path;

        private ResolutionException(PathRole role, String path, String message) {
            super(message + " [role=" + role + ", path=" + path + "]");
            this.role = role;
            this.path = path;
        }

        private ResolutionException(PathRole role, String path, String message, Throwable cause) {
            super(message + " [role=" + role + ", path=" + path + "]", cause);
            this.role = role;
            this.path = path;
        }

        public PathRole getRole() {
            return this.role;
        }

        public String getPath() {
            return this.path;
        }
    }
}
