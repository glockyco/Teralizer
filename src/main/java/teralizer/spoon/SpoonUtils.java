package teralizer.spoon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.analysis.TestShape;

public class SpoonUtils {

    /**
     * Creates a type reference for generated declarations from a type name that may denote a
     * model type, a JDK type, or any other type outside the Spoon source model. References are
     * therefore created by name, never looked up in the model — a model lookup cannot see
     * shadow types and would leave JDK-typed inputs (e.g. {@code java.lang.String}) without a
     * reference. The explicit switch arms exist because simple boxed names ("Long") must map
     * to their {@code java.lang} references; created references would keep them unqualified.
     */
    public static CtTypeReference<?> getTypeReference(Factory factory, String typeName) {
        switch (typeName) {
            case "boolean":
                return factory.Type().BOOLEAN_PRIMITIVE;
            case "java.lang.Boolean":
            case "Boolean":
                return factory.Type().BOOLEAN;
            case "byte":
                return factory.Type().BYTE_PRIMITIVE;
            case "java.lang.Byte":
            case "Byte":
                return factory.Type().BYTE;
            case "char":
                return factory.Type().CHARACTER_PRIMITIVE;
            case "java.lang.Character":
            case "Character":
                return factory.Type().CHARACTER;
            case "double":
                return factory.Type().DOUBLE_PRIMITIVE;
            case "java.lang.Double":
            case "Double":
                return factory.Type().DOUBLE;
            case "float":
                return factory.Type().FLOAT_PRIMITIVE;
            case "java.lang.Float":
            case "Float":
                return factory.Type().FLOAT;
            case "int":
                return factory.Type().INTEGER_PRIMITIVE;
            case "java.lang.Integer":
            case "Integer":
                return factory.Type().INTEGER;
            case "long":
                return factory.Type().LONG_PRIMITIVE;
            case "java.lang.Long":
            case "Long":
                return factory.Type().LONG;
            case "short":
                return factory.Type().SHORT_PRIMITIVE;
            case "java.lang.Short":
            case "Short":
                return factory.Type().SHORT;
            case "void":
                return factory.Type().VOID_PRIMITIVE;
            case "java.lang.Void":
            case "Void":
                return factory.Type().VOID;
            default:
                return factory.Type().createReference(typeName);
        }
    }

    /**
     * Deletes every test method in the cloned class except the one being generalized.
     *
     * <p>A method counts as a test if it carries a test annotation, or if it follows JUnit 3's
     * {@code test*} naming convention. Both kinds go, whatever framework the source used. The
     * pipeline reads one surefire report for the generalized class, and a sibling that fails there
     * looks exactly like the property failing.
     *
     * <p>Deleting the convention-named siblings also stops the vintage engine from finding a test to
     * run in the class. {@code TestCaseDetachment} then deletes the {@code TestCase} ancestry that
     * the engine matches on.
     */
    public static void deleteOtherTestMethodsInClass(CtClass<?> testClass, CtMethod<?> testMethod) {
        List<CtMethod<?>> otherTestMethods = testClass.getMethods().stream()
            .filter(m -> m != testMethod)
            .filter(m -> hasTestAnnotation(m) || TestShape.isJUnit3TestMethod(m, testClass))
            .collect(Collectors.toList());
        otherTestMethods.forEach(testClass::removeMethod);
    }

    public static boolean hasTestAnnotation(CtMethod<?> testMethod) {
        return TestShape.hasTestAnnotation(testMethod);
    }

    /**
     * Deletes every assertion in the method except the one being generalized.
     *
     * <p>Deleting a void assertion also deletes any call inside its arguments, so the assertion that
     * remains can run against a different state than it did in the source test. The instrumented
     * wrapper deletes the same assertions as the generated test, so the specification describes the
     * code the generalized test actually runs. If the deletion changes behavior, the generalized test
     * fails validation and the pipeline drops it.
     */
    public static void deleteOtherAssertionsInMethod(CtMethod<?> method, CtInvocation<?> assertion) {
        List<CtInvocation> otherAssertions = method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
            .filter(i -> i != assertion && TestAnalysis.isSupportedFrameworkAssertion(i))
            .filter(i -> !isCatchAssertionForRecognizedTryFail(assertion, i))
            .collect(Collectors.toList());

        otherAssertions.forEach(a -> {
            CtTypeReference<?> returnType = a.getExecutable().getType();
            boolean hasVoidReturnType = a.getFactory().Type().voidPrimitiveType().equals(returnType);
            if (hasVoidReturnType || (
                a.getParent(CtAssignment.class) == null &&
                a.getParent(CtVariable.class) == null &&
                a.getParent(CtInvocation.class) == null
            )) {
                a.delete();
            }
        });
    }

    private static boolean isCatchAssertionForRecognizedTryFail(CtInvocation<?> keptAssertion, CtInvocation<?> candidate) {
        if (!"fail".equals(keptAssertion.getExecutable().getSimpleName())
            || !TestAnalysis.normalizedAssertion(keptAssertion).isPresent()) {
            return false;
        }
        CtTry expectedTry = keptAssertion.getParent(CtTry.class);
        CtCatch candidateCatch = candidate.getParent(CtCatch.class);
        return expectedTry != null && candidateCatch != null && candidateCatch.getParent(CtTry.class) == expectedTry;
    }

    public static CtClass<?> cloneClass(
        Factory factory,
        CtClass<?> originalClass,
        String oldClassPackage,
        String newClassPackage,
        String oldClassName,
        String newClassName,
        String oldClassQualifiedName,
        String newClassQualifiedName
    ) {
        CtClass<?> clonedClass = originalClass.clone();
        clonedClass.setSimpleName(newClassName);
        factory.Package().get(newClassPackage).addType(clonedClass);
        flattenInheritedTestMethods(originalClass, clonedClass);

        ReferenceRenamer referenceRenamer = new ReferenceRenamer(
            oldClassQualifiedName,
            newClassQualifiedName,
            oldClassName,
            newClassName
        );

        clonedClass.accept(referenceRenamer);

        return clonedClass;
    }

    private static void flattenInheritedTestMethods(CtClass<?> originalClass, CtClass<?> clonedClass) {
        CtTypeReference<?> superclassReference = originalClass.getSuperclass();
        while (superclassReference != null) {
            CtClass<?> superclass = superclassReference.getDeclaration() instanceof CtClass<?>
                ? (CtClass<?>) superclassReference.getDeclaration()
                : null;
            if (superclass == null) {
                return;
            }
            for (CtMethod<?> method : superclass.getMethods()) {
                if (isFlattenableInheritedMethod(originalClass, clonedClass, method)) {
                    clonedClass.addMethod(method.clone());
                }
            }
            superclassReference = superclass.getSuperclass();
        }
    }

    private static boolean isFlattenableInheritedMethod(CtClass<?> originalClass, CtClass<?> clonedClass, CtMethod<?> method) {
        CtClass<?> declaringClass = method.getParent(CtClass.class);
        return (hasTestAnnotation(method)
                || TestShape.isFixture(method, declaringClass)
                || TestShape.isJUnit3TestMethod(method, declaringClass))
            && clonedClass.getMethodsByName(method.getSimpleName()).isEmpty()
            && InheritedTestMethodScreens.evaluate(originalClass, method).isFlattenable();
    }

    public static List<CtImport> importsForGeneratedClass(CtClass<?> generatedClass) {
        CtCompilationUnit classUnit = compilationUnitFor(generatedClass);
        List<CtImport> imports = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addImports(imports, seen, classUnit);

        for (CtMethod<?> method : generatedClass.getMethods()) {
            CtCompilationUnit methodUnit = compilationUnitFor(method);
            if (methodUnit != null && methodUnit != classUnit) {
                addImports(imports, seen, methodUnit);
            }
        }

        return imports;
    }

    private static CtCompilationUnit compilationUnitFor(CtElement element) {
        SourcePosition position = element.getPosition();
        if (position == null || !position.isValidPosition()) {
            return null;
        }
        return position.getCompilationUnit();
    }

    private static void addImports(List<CtImport> imports, Set<String> seen, CtCompilationUnit unit) {
        if (unit == null) {
            return;
        }
        for (CtImport ctImport : unit.getImports()) {
            if (seen.add(ctImport.toString())) {
                imports.add(ctImport.clone());
            }
        }
    }

    public static String getBoxedType(String type) {
        switch (type) {
            case "byte": return "Byte";
            case "short": return "Short";
            case "int": return "Integer";
            case "long": return "Long";
            case "float": return "Float";
            case "double": return "Double";
            case "char": return "Character";
            case "boolean": return "Boolean";
            default: return type;
        }
    }

    private static class ReferenceRenamer extends CtScanner {
        private final String oldQualifiedName;
        private final String newQualifiedName;
        private final String oldSimpleName;
        private final String newSimpleName;

        public ReferenceRenamer(
            String oldQualifiedName,
            String newQualifiedName,
            String oldSimpleName,
            String newSimpleName
        ) {
            this.oldQualifiedName = oldQualifiedName;
            this.newQualifiedName = newQualifiedName;
            this.oldSimpleName = oldSimpleName;
            this.newSimpleName = newSimpleName;
        }

        @Override
        public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
            if (reference.getQualifiedName().equals(this.oldQualifiedName)) {
                reference.setSimpleName(this.newSimpleName);

                // Handle package change
                String newPackageName = this.newQualifiedName.substring(0, this.newQualifiedName.lastIndexOf("."));
                reference.setPackage(reference.getFactory().Package().createReference(newPackageName));
            }
            super.visitCtTypeReference(reference);
        }

        @Override
        public <T> void visitCtExecutableReference(CtExecutableReference<T> reference) {
            if (reference.getDeclaringType() != null &&
                reference.getDeclaringType().getQualifiedName().equals(this.oldQualifiedName)) {
                // Update constructor references
                CtTypeReference<?> newTypeRef = reference.getFactory().Type().createReference(this.newQualifiedName);
                newTypeRef.setImplicit(reference.getDeclaringType().isImplicit());
                reference.setDeclaringType(newTypeRef);
            }
            super.visitCtExecutableReference(reference);
        }

        @Override
        public <T> void visitCtConstructor(CtConstructor<T> constructor) {
            constructor.setSimpleName(this.newSimpleName);
            super.visitCtConstructor(constructor);
        }

        @Override
        public <T> void visitCtFieldReference(CtFieldReference<T> reference) {
            if (reference.getDeclaringType() != null &&
                reference.getDeclaringType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = reference.getFactory().Type().createReference(this.newQualifiedName);
                newTypeRef.setImplicit(reference.getDeclaringType().isImplicit());
                reference.setDeclaringType(newTypeRef);
            }
            super.visitCtFieldReference(reference);
        }

        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invocation) {
            // Handle method invocations
            if (invocation.getTarget() != null &&
                invocation.getTarget().getType() != null &&
                invocation.getTarget().getType().getQualifiedName().equals(this.oldQualifiedName)) {

                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtTypeAccess) {
                    // For static method calls (ClassName.method())
                    CtTypeReference<?> newTypeRef = invocation.getFactory().Type().createReference(this.newQualifiedName);
                    newTypeRef.setImplicit(invocation.getTarget().getType().isImplicit());
                    CtTypeAccess<?> newTypeAccess = invocation.getFactory().createTypeAccess(newTypeRef);
                    invocation.setTarget(newTypeAccess);
                }
            }
            super.visitCtInvocation(invocation);
        }

        @Override
        public <T> void visitCtNewClass(CtNewClass<T> newClass) {
            // Handle instantiations (new ClassName())
            if (newClass.getType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = newClass.getFactory().Type().createReference(this.newQualifiedName);
                newTypeRef.setImplicit(newClass.getType().isImplicit());
                newClass.setType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtNewClass(newClass);
        }

        @Override
        public <T> void visitCtMethod(CtMethod<T> method) {
            // Handle return types
            if (method.getType() != null &&
                method.getType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = method.getFactory().Type().createReference(this.newQualifiedName);
                newTypeRef.setImplicit(method.getType().isImplicit());
                method.setType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtMethod(method);
        }

        @Override
        public <T> void visitCtTypeAccess(CtTypeAccess<T> typeAccess) {
            // Handle class literals (ClassName.class)
            if (typeAccess.getAccessedType() != null &&
                typeAccess.getAccessedType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = typeAccess.getFactory().Type().createReference(this.newQualifiedName);
                newTypeRef.setImplicit(typeAccess.isImplicit());
                typeAccess.setAccessedType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtTypeAccess(typeAccess);
        }
    }
}
