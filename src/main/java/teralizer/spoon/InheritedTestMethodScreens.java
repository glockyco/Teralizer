package teralizer.spoon;

import java.util.Set;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class InheritedTestMethodScreens {
    public static final String INHERITED_METHOD_NOT_FLATTENABLE = "INHERITED_METHOD_NOT_FLATTENABLE";

    private static final String TYPE_VARIABLE = "TYPE_VARIABLE";
    private static final String PRIVATE_MEMBER = "PRIVATE_MEMBER";

    private InheritedTestMethodScreens() {
    }

    public static Result evaluate(CtClass<?> childClass, CtMethod<?> inheritedMethod) {
        if (containsTypeParameterReference(inheritedMethod)) {
            return Result.excluded(TYPE_VARIABLE);
        }
        if (referencesPrivateDeclaringClassMember(childClass, inheritedMethod)) {
            return Result.excluded(PRIVATE_MEMBER);
        }
        return Result.flattenable();
    }

    private static boolean containsTypeParameterReference(CtMethod<?> method) {
        return !method.getElements(new TypeFilter<>(CtTypeParameterReference.class)).isEmpty();
    }

    private static boolean referencesPrivateDeclaringClassMember(CtClass<?> childClass, CtMethod<?> method) {
        CtType<?> declaringType = method.getParent(CtType.class);
        if (declaringType == null) {
            return false;
        }

        return method.getElements(new TypeFilter<>(CtExecutableReference.class)).stream()
            .anyMatch(reference -> isPrivateMemberOfDeclaringType(reference.getDeclaration(), declaringType))
            || method.getElements(new TypeFilter<>(CtFieldReference.class)).stream()
                .anyMatch(reference -> isPrivateMemberOfDeclaringType(reference.getDeclaration(), declaringType));
    }

    private static boolean isPrivateMemberOfDeclaringType(CtExecutable<?> executable, CtType<?> declaringType) {
        if (executable instanceof CtMethod<?>) {
            CtMethod<?> method = (CtMethod<?>) executable;
            return belongsToDeclaringType(method, declaringType) && hasPrivateModifier(method.getModifiers());
        }
        if (executable instanceof CtConstructor<?>) {
            CtConstructor<?> constructor = (CtConstructor<?>) executable;
            return belongsToDeclaringType(constructor, declaringType) && hasPrivateModifier(constructor.getModifiers());
        }
        return false;
    }

    private static boolean isPrivateMemberOfDeclaringType(CtField<?> field, CtType<?> declaringType) {
        return field != null
            && belongsToDeclaringType(field, declaringType)
            && hasPrivateModifier(field.getModifiers());
    }

    private static boolean belongsToDeclaringType(CtElement member, CtType<?> declaringType) {
        CtType<?> parentType = member.getParent(CtType.class);
        return parentType != null && parentType.getQualifiedName().equals(declaringType.getQualifiedName());
    }

    private static boolean hasPrivateModifier(Set<ModifierKind> modifiers) {
        return modifiers.contains(ModifierKind.PRIVATE);
    }

    public static final class Result {
        private final boolean flattenable;
        private final String exclusionInfo;

        private Result(boolean flattenable, String exclusionInfo) {
            this.flattenable = flattenable;
            this.exclusionInfo = exclusionInfo;
        }

        private static Result flattenable() {
            return new Result(true, null);
        }

        private static Result excluded(String failingScreen) {
            return new Result(false, INHERITED_METHOD_NOT_FLATTENABLE + ":" + failingScreen);
        }

        public boolean isFlattenable() {
            return this.flattenable;
        }

        public String getExclusionInfo() {
            return this.exclusionInfo;
        }
    }
}
