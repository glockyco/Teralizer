package teralizer.spoon.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.util.TypeCapability;

public final class InstrumentedClassBuilder {

    public CtClass<?> build(Factory factory, GeneralizationRecipe clonedRecipe, Names names) {
        CtClass<?> instrumentedClass = SpoonUtils.cloneClass(
            factory,
            factory.Class().get(names.getSourceTestClassQualifiedName()),
            names.getSourceTestPackageName(),
            names.getInstrumentedPackageName(),
            names.getSourceTestClassName(),
            names.getInstrumentedClassName(),
            names.getSourceTestClassQualifiedName(),
            names.getInstrumentedClassQualifiedName()
        );

        CtPath testMethodPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                names.getSourceTestMethodRelativePath(),
                names.getSourceTestClassQualifiedName(),
                names.getInstrumentedClassQualifiedName()));
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(instrumentedClass).get(0);
        SpoonUtils.deleteOtherTestMethodsInClass(instrumentedClass, testMethod);

        GeneralizationRecipe.Resolved recipe = clonedRecipe.resolveAgainst(testMethod, factory.getModel().getRootPackage());
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        CtMethod<?> instrumentedMethod = createInstrumentedMethod(
            factory,
            instrumentedClass,
            recipe,
            names.getInstrumentedMethodName(),
            names.getTestedClassQualifiedName(),
            names.getOracleExpressionType()
        );
        CtInvocation<?> instrumentedMethodCall = createInstrumentedMethodCall(
            factory,
            instrumentedClass,
            instrumentedMethod,
            recipe
        );
        oracleExpression.replace(instrumentedMethodCall);

        CtPath targetAssertionPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                names.getSourceAssertionRelativePath(),
                names.getSourceTestClassQualifiedName(),
                names.getInstrumentedClassQualifiedName()));
        CtInvocation<?> targetAssertion = (CtInvocation<?>) targetAssertionPath.evaluateOn(testMethod).get(0);
        SpoonUtils.deleteOtherAssertionsInMethod(testMethod, targetAssertion);

        return instrumentedClass;
    }

    public CtMethod<?> createInstrumentedMethod(
        Factory factory,
        CtClass<?> instrumentedClass,
        GeneralizationRecipe.Resolved recipe,
        String instrumentedMethodName,
        String testedClassQualifiedName,
        String oracleExpressionType
    ) {
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        List<GeneralizableInput> generalizableInputs = recipe.getInputs();
        CtExpression<?> rewrittenExpression = oracleExpression.clone();
        recipe.replaceInputSitesWithParameterReads(
            rewrittenExpression,
            factory,
            input -> input.toMethodParameter().getName()
        );

        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);
        boolean needsTarget = isPlainCallRecipe(recipe) && !testedMethod.isStatic() && !hasReceiverConstructorInputs;
        List<CtParameter<?>> instrumentedParameters = new ArrayList<>();
        if (needsTarget) {
            CtInvocation<?> oracleCall = findOracleInvocation(oracleExpression, testedMethod);
            CtTypeReference<?> targetType = oracleCall.getTarget() instanceof CtThisAccess
                ? instrumentedClass.getReference()
                : oracleCall.getTarget().getType();
            CtTypeReference<?> resolvedType = resolveTargetType(factory, targetType, testedClassQualifiedName);
            instrumentedParameters.add(factory.createParameter(null, resolvedType, "_target_"));
            findOracleInvocation(rewrittenExpression, testedMethod)
                .setTarget(factory.createCodeSnippetExpression("_target_"));
        }
        for (GeneralizableInput input : generalizableInputs) {
            CtTypeReference<?> type = factory.Type().createReference(input.toMethodParameter().getType());
            type.setSimplyQualified(false);
            type.setImplicit(false);
            instrumentedParameters.add(factory.createParameter(null, type, input.toMethodParameter().getName()));
        }

        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(rewrittenExpression, oracleExpression, generalizableInputs);
        Map<CtVariableReference<?>, String> liftedNames = liftedParameterNames(liftedLocals.keySet());
        for (Map.Entry<CtVariableReference<?>, CtTypeReference<?>> lifted : liftedLocals.entrySet()) {
            CtTypeReference<?> type = lifted.getValue().clone();
            type.setSimplyQualified(false);
            type.setImplicit(false);
            instrumentedParameters.add(factory.createParameter(null, type, liftedNames.get(lifted.getKey())));
        }
        liftLocalReads(rewrittenExpression, liftedNames, factory);

        CtReturn returnStatement = factory.Core().createReturn();
        returnStatement.setReturnedExpression(rewrittenExpression);
        CtBlock<?> instrumentedBody = factory.createBlock();
        instrumentedBody.addStatement(returnStatement);

        CtTypeReference<?> returnType = factory.Type().createReference(oracleExpressionType);
        returnType.setSimplyQualified(false);
        returnType.setImplicit(false);

        Set<CtTypeReference<? extends Throwable>> thrownTypes = collectThrownTypes(testedMethod, oracleExpression);

        return factory.createMethod(
            instrumentedClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            returnType,
            instrumentedMethodName,
            instrumentedParameters,
            thrownTypes,
            instrumentedBody
        );
    }

    public CtInvocation<?> createInstrumentedMethodCall(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> instrumentedMethod,
        GeneralizationRecipe.Resolved recipe
    ) {
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        List<GeneralizableInput> generalizableInputs = recipe.getInputs();
        CtInvocation<?> instrumentedMethodCall = factory.createInvocation(factory.createThisAccess(instrumentedClass.getReference()), instrumentedMethod.getReference());
        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);
        boolean needsTarget = isPlainCallRecipe(recipe) && !testedMethod.isStatic() && !hasReceiverConstructorInputs;
        if (needsTarget) {
            CtExpression<?> target = findOracleInvocation(oracleExpression, testedMethod).getTarget();
            if (target instanceof CtThisAccess) {
                instrumentedMethodCall.addArgument(factory.createThisAccess(target.getType(), false));
            } else {
                instrumentedMethodCall.addArgument(target);
            }
        }
        for (GeneralizableInput input : generalizableInputs) {
            instrumentedMethodCall.addArgument(input.getSourceExpression());
        }
        CtExpression<?> rewrittenExpression = oracleExpression.clone();
        recipe.replaceInputSitesWithParameterReads(
            rewrittenExpression,
            factory,
            input -> input.toMethodParameter().getName()
        );
        if (needsTarget) {
            findOracleInvocation(rewrittenExpression, testedMethod).setTarget(factory.createCodeSnippetExpression("_target_"));
        }
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(rewrittenExpression, oracleExpression, generalizableInputs);
        for (CtVariableReference<?> reference : liftedLocals.keySet()) {
            instrumentedMethodCall.addArgument(factory.createCodeSnippetExpression(reference.getSimpleName()));
        }
        return instrumentedMethodCall;
    }

    public static Set<CtTypeReference<? extends Throwable>> collectThrownTypes(CtMethod<?> testedMethod, CtInvocation<?> testedMethodCall) {
        return collectThrownTypes(testedMethod, (CtElement) testedMethodCall);
    }

    private static Set<CtTypeReference<? extends Throwable>> collectThrownTypes(CtMethod<?> testedMethod, CtElement wrapperBodyExpression) {
        Set<CtTypeReference<? extends Throwable>> thrownTypes = new HashSet<>(testedMethod.getThrownTypes());
        for (CtAbstractInvocation<?> invocation : wrapperBodyExpression.getElements(new TypeFilter<>(CtAbstractInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                continue;
            }
            CtExecutable<?> declaration = executable.getExecutableDeclaration();
            if (declaration != null) {
                thrownTypes.addAll(declaration.getThrownTypes());
            }
        }
        thrownTypes.forEach(t -> {
            t.setSimplyQualified(false);
            t.setImplicit(false);
        });
        return thrownTypes;
    }

    public static CtTypeReference<?> resolveTargetType(
        Factory factory, CtTypeReference<?> receiverType, String testedClassQualifiedName
    ) {
        if (!(receiverType instanceof CtTypeParameterReference)) {
            return receiverType;
        }
        CtTypeReference<?> concreteType = factory.Type().createReference(testedClassQualifiedName);
        concreteType.setSimplyQualified(false);
        concreteType.setImplicit(false);
        return concreteType;
    }

    private static CtInvocation<?> findOracleInvocation(CtExpression<?> expression, CtMethod<?> oracleMethod) {
        if (expression instanceof CtInvocation<?>
            && ((CtInvocation<?>) expression).getExecutable().getSimpleName().equals(oracleMethod.getSimpleName())) {
            return (CtInvocation<?>) expression;
        }
        return expression.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals(oracleMethod.getSimpleName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not find oracle invocation in expression " + expression));
    }

    public static LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> collectLiftableLocals(
        CtExpression<?> rewrittenExpression,
        CtExpression<?> originalExpression,
        List<GeneralizableInput> generalizableInputs
    ) {
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted = new LinkedHashMap<>();
        collectOutOfScopeReads(
            rewrittenExpression,
            originalExpression,
            generalizableInputs.stream()
                .map(input -> input.toMethodParameter().getName())
                .collect(Collectors.toSet()),
            lifted
        );
        return lifted;
    }

    private static void collectOutOfScopeReads(
        CtExpression<?> expression,
        CtExpression<?> originalExpression,
        Set<String> parameterNames,
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted
    ) {
        for (CtVariableRead<?> read : expression.getElements(new TypeFilter<>(CtVariableRead.class))) {
            if (read instanceof CtFieldRead) {
                continue;
            }
            CtVariableReference<?> reference = read.getVariable();
            if (parameterNames.contains(reference.getSimpleName())) {
                continue;
            }
            CtElement declaration = reference.getDeclaration();
            if (declaration != null && declaration.hasParent(originalExpression)) {
                continue;
            }
            CtTypeReference<?> type = reference.getType();
            if (type == null || type instanceof CtTypeParameterReference) {
                throw new RuntimeException(
                    "Cannot lift test-local variable '" + reference.getSimpleName()
                        + "' (unresolvable type) referenced by oracle expression " + originalExpression);
            }
            lifted.putIfAbsent(reference, type);
        }
    }

    static Map<CtVariableReference<?>, String> liftedParameterNames(Set<CtVariableReference<?>> references) {
        Map<CtVariableReference<?>, String> names = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (CtVariableReference<?> reference : references) {
            String base = "_local_" + reference.getSimpleName();
            int ordinal = seen.merge(reference.getSimpleName(), 1, Integer::sum);
            names.put(reference, ordinal == 1 ? base : base + "_" + ordinal);
        }
        return names;
    }

    private static void liftLocalReads(
        CtExpression<?> clonedExpression,
        Map<CtVariableReference<?>, String> liftedNames,
        Factory factory
    ) {
        Map<String, String> bySimpleName = new HashMap<>();
        for (Map.Entry<CtVariableReference<?>, String> entry : liftedNames.entrySet()) {
            bySimpleName.put(entry.getKey().getSimpleName(), entry.getValue());
        }
        for (CtVariableRead<?> read : clonedExpression.getElements(new TypeFilter<>(CtVariableRead.class))) {
            if (read instanceof CtFieldRead) {
                continue;
            }
            String liftedName = bySimpleName.get(read.getVariable().getSimpleName());
            if (liftedName != null) {
                read.replace(factory.createCodeSnippetExpression(liftedName));
            }
        }
    }

    private static boolean isPlainCallRecipe(GeneralizationRecipe.Resolved recipe) {
        return recipe.getInputs().stream().noneMatch(GeneralizableInput::isExpressionSite);
    }
    public static String symbolicMarker(CtParameter<?> parameter) {
        String name = parameter.getSimpleName();
        if ("_target_".equals(name) || name.startsWith("_local_")) {
            return "con";
        }
        return TypeCapability.supportsGeneratedInput(parameter.getType().getSimpleName()) ? "sym" : "con";
    }


    public static final class Names {
        private final String sourceTestPackageName;
        private final String instrumentedPackageName;
        private final String sourceTestClassName;
        private final String instrumentedClassName;
        private final String sourceTestClassQualifiedName;
        private final String instrumentedClassQualifiedName;
        private final String sourceTestMethodRelativePath;
        private final String sourceAssertionRelativePath;
        private final String instrumentedMethodName;
        private final String testedClassQualifiedName;
        private final String oracleExpressionType;

        public Names(
            String sourceTestPackageName,
            String instrumentedPackageName,
            String sourceTestClassName,
            String instrumentedClassName,
            String sourceTestClassQualifiedName,
            String instrumentedClassQualifiedName,
            String sourceTestMethodRelativePath,
            String sourceAssertionRelativePath,
            String instrumentedMethodName,
            String testedClassQualifiedName,
            String oracleExpressionType
        ) {
            this.sourceTestPackageName = sourceTestPackageName;
            this.instrumentedPackageName = instrumentedPackageName;
            this.sourceTestClassName = sourceTestClassName;
            this.instrumentedClassName = instrumentedClassName;
            this.sourceTestClassQualifiedName = sourceTestClassQualifiedName;
            this.instrumentedClassQualifiedName = instrumentedClassQualifiedName;
            this.sourceTestMethodRelativePath = sourceTestMethodRelativePath;
            this.sourceAssertionRelativePath = sourceAssertionRelativePath;
            this.instrumentedMethodName = instrumentedMethodName;
            this.testedClassQualifiedName = testedClassQualifiedName;
            this.oracleExpressionType = oracleExpressionType;
        }

        public String getSourceTestPackageName() {
            return this.sourceTestPackageName;
        }

        public String getInstrumentedPackageName() {
            return this.instrumentedPackageName;
        }

        public String getSourceTestClassName() {
            return this.sourceTestClassName;
        }

        public String getInstrumentedClassName() {
            return this.instrumentedClassName;
        }

        public String getSourceTestClassQualifiedName() {
            return this.sourceTestClassQualifiedName;
        }

        public String getInstrumentedClassQualifiedName() {
            return this.instrumentedClassQualifiedName;
        }

        public String getSourceTestMethodRelativePath() {
            return this.sourceTestMethodRelativePath;
        }

        public String getSourceAssertionRelativePath() {
            return this.sourceAssertionRelativePath;
        }

        public String getInstrumentedMethodName() {
            return this.instrumentedMethodName;
        }

        public String getTestedClassQualifiedName() {
            return this.testedClassQualifiedName;
        }

        public String getOracleExpressionType() {
            return this.oracleExpressionType;
        }
    }
}
