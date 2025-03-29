package teralizer.spoon;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

public class SpoonUtils {

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
                return factory.Type().get(typeName).getReference();
        }
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

        ReferenceRenamer referenceRenamer = new ReferenceRenamer(
            oldClassQualifiedName,
            newClassQualifiedName,
            oldClassName,
            newClassName
        );

        clonedClass.accept(referenceRenamer);

        return clonedClass;
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
            if (!reference.isImplicit() && reference.getQualifiedName().equals(this.oldQualifiedName)) {
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
                !reference.getDeclaringType().isImplicit() &&
                reference.getDeclaringType().getQualifiedName().equals(this.oldQualifiedName)) {
                // Update constructor references
                CtTypeReference<?> newTypeRef = reference.getFactory().Type().createReference(this.newQualifiedName);
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
                !reference.getDeclaringType().isImplicit() &&
                reference.getDeclaringType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = reference.getFactory().Type().createReference(this.newQualifiedName);
                reference.setDeclaringType(newTypeRef);
            }
            super.visitCtFieldReference(reference);
        }

        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invocation) {
            // Handle method invocations
            if (invocation.getTarget() != null &&
                invocation.getTarget().getType() != null &&
                !invocation.getTarget().getType().isImplicit() &&
                invocation.getTarget().getType().getQualifiedName().equals(this.oldQualifiedName)) {

                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtTypeAccess) {
                    // For static method calls (ClassName.method())
                    CtTypeReference<?> newTypeRef = invocation.getFactory().Type().createReference(this.newQualifiedName);
                    CtTypeAccess<?> newTypeAccess = invocation.getFactory().createTypeAccess(newTypeRef);
                    invocation.setTarget(newTypeAccess);
                }
            }
            super.visitCtInvocation(invocation);
        }

        @Override
        public <T> void visitCtNewClass(CtNewClass<T> newClass) {
            // Handle instantiations (new ClassName())
            if (!newClass.getType().isImplicit() && newClass.getType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = newClass.getFactory().Type().createReference(this.newQualifiedName);
                newClass.setType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtNewClass(newClass);
        }

        @Override
        public <T> void visitCtMethod(CtMethod<T> method) {
            // Handle return types
            if (method.getType() != null &&
                !method.getType().isImplicit() &&
                method.getType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = method.getFactory().Type().createReference(this.newQualifiedName);
                method.setType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtMethod(method);
        }

        @Override
        public <T> void visitCtTypeAccess(CtTypeAccess<T> typeAccess) {
            // Handle class literals (ClassName.class)
            if (typeAccess.getAccessedType() != null &&
                !typeAccess.getAccessedType().isImplicit() &&
                typeAccess.getAccessedType().getQualifiedName().equals(this.oldQualifiedName)) {
                CtTypeReference<?> newTypeRef = typeAccess.getFactory().Type().createReference(this.newQualifiedName);
                typeAccess.setAccessedType((CtTypeReference<T>) newTypeRef);
            }
            super.visitCtTypeAccess(typeAccess);
        }
    }
}
