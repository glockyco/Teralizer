package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.List;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.util.TypeCapability;

public class GeneralizableInput {
    private final GeneralizationRecipe.InputKind kind;
    private final MethodParameter parameter;
    private final MethodArgument argument;
    private final CtExpression<?> sourceExpression;

    private GeneralizableInput(
        GeneralizationRecipe.InputKind kind,
        MethodParameter parameter,
        MethodArgument argument,
        CtExpression<?> sourceExpression
    ) {
        this.kind = kind;
        this.parameter = parameter;
        this.argument = argument;
        this.sourceExpression = sourceExpression;
    }

    public static List<GeneralizableInput> derive(CtMethod<?> testedMethod, CtInvocation<?> testedMethodCall) {
        List<GeneralizableInput> inputs = new ArrayList<>();
        List<CtParameter<?>> parameters = testedMethod.getParameters();
        List<CtExpression<?>> arguments = testedMethodCall.getArguments();
        if (!testedMethod.isStatic() && testedMethodCall.getTarget() instanceof CtConstructorCall<?>) {
            inputs.addAll(deriveConstructorInputs(
                GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG,
                "receiver",
                (CtConstructorCall<?>) testedMethodCall.getTarget()
            ));
        }


        for (int i = 0; i < parameters.size(); i++) {
            CtParameter<?> parameter = parameters.get(i);
            if (i >= arguments.size() || parameter.isVarArgs()) {
                /*
                 * Varargs are deliberately not lifted in v1: the generator has no recipe
                 * for producing a variable-length argument list, and calls such as join()
                 * or join(1, 2, 3) do not have a stable one-parameter/one-argument
                 * correspondence. Positional lifting would therefore be unsound.
                 */
                continue;
            }
            CtExpression<?> argument = arguments.get(i);
            CtTypeReference<?> type = inferType(parameter, argument);
            String typeName = type.getQualifiedName();

            if (TypeCapability.supportsGeneratedInput(typeName)) {
                inputs.add(new GeneralizableInput(
                    GeneralizationRecipe.InputKind.METHOD_ARG,
                    new MethodParameter(typeName, parameter.getSimpleName()),
                    new MethodArgument(typeName, argument.toString()),
                    argument
                ));
            } else if (argument instanceof CtConstructorCall<?>) {
                inputs.addAll(deriveConstructorInputs(GeneralizationRecipe.InputKind.CTOR_ARG, parameter.getSimpleName(), (CtConstructorCall<?>) argument));
            }
        }

        return inputs;
    }

    public static List<GeneralizableInput> deriveFromExpression(CtExpression<?> expression) {
        List<GeneralizableInput> inputs = new ArrayList<>();
        deriveExpressionSites(expression, inputs);
        return inputs;
    }

    static GeneralizableInput fromRecipe(
        GeneralizationRecipe.InputKind kind,
        MethodParameter parameter,
        MethodArgument argument,
        CtExpression<?> sourceExpression
    ) {
        return new GeneralizableInput(
            kind,
            parameter,
            argument,
            sourceExpression
        );
    }

    private static void deriveExpressionSites(CtExpression<?> expression, List<GeneralizableInput> inputs) {
        if (expression instanceof CtInvocation<?>) {
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            deriveExpressionSites(invocation.getTarget(), inputs);
            deriveExpressionArgumentSites(invocation.getArguments(), inputs);
        } else if (expression instanceof CtConstructorCall<?>) {
            deriveExpressionArgumentSites(((CtConstructorCall<?>) expression).getArguments(), inputs);
        } else if (expression instanceof CtBinaryOperator<?>) {
            CtBinaryOperator<?> operator = (CtBinaryOperator<?>) expression;
            deriveExpressionSites(operator.getLeftHandOperand(), inputs);
            deriveExpressionSites(operator.getRightHandOperand(), inputs);
        } else if (expression instanceof CtUnaryOperator<?>) {
            deriveExpressionSites(((CtUnaryOperator<?>) expression).getOperand(), inputs);
        }
    }

    private static void deriveExpressionArgumentSites(List<CtExpression<?>> arguments, List<GeneralizableInput> inputs) {
        for (CtExpression<?> argument : arguments) {
            if (argument instanceof CtLiteral<?>) {
                addExpressionSite((CtLiteral<?>) argument, inputs);
            } else {
                deriveExpressionSites(argument, inputs);
            }
        }
    }

    private static void addExpressionSite(CtLiteral<?> literal, List<GeneralizableInput> inputs) {
        CtTypeReference<?> type = literal.getType();
        if (type == null || !TypeCapability.supportsGeneratedInput(type.getQualifiedName())) {
            return;
        }
        String typeName = type.getQualifiedName();
        String name = sanitize("site" + inputs.size());
        inputs.add(new GeneralizableInput(
            GeneralizationRecipe.InputKind.EXPRESSION_SITE,
            new MethodParameter(typeName, name),
            new MethodArgument(typeName, literal.toString()),
            literal
        ));
    }

    private static List<GeneralizableInput> deriveConstructorInputs(
        GeneralizationRecipe.InputKind kind,
        String methodParameterName,
        CtConstructorCall<?> constructorCall
    ) {
        List<GeneralizableInput> inputs = new ArrayList<>();
        CtConstructor<?> constructor = constructorCall.getExecutable().getDeclaration() instanceof CtConstructor<?>
            ? (CtConstructor<?>) constructorCall.getExecutable().getDeclaration()
            : null;
        List<CtExpression<?>> arguments = constructorCall.getArguments();
        List<CtParameter<?>> constructorParameters = constructor == null ? new ArrayList<>() : constructor.getParameters();

        for (int i = 0; i < arguments.size(); i++) {
            CtExpression<?> argument = arguments.get(i);
            CtTypeReference<?> type = i < constructorParameters.size()
                ? constructorParameters.get(i).getType()
                : argument.getType();
            if (type == null || !TypeCapability.supportsGeneratedInput(type.getQualifiedName())) {
                return new ArrayList<>();
            }

            String constructorParameterName = i < constructorParameters.size()
                ? constructorParameters.get(i).getSimpleName()
                : "arg" + i;
            String inputName = constructorInputName(methodParameterName, i, constructorParameterName);
            String typeName = type.getQualifiedName();
            inputs.add(new GeneralizableInput(
                kind,
                new MethodParameter(typeName, inputName),
                new MethodArgument(typeName, argument.toString()),
                argument
            ));
        }

        return inputs;
    }

    private static CtTypeReference<?> inferType(CtParameter<?> parameter, CtExpression<?> argument) {
        CtTypeReference<?> parameterType = parameter.getType();
        CtTypeReference<?> argumentType = argument.getType();

        if (!parameterType.isGenerics()) {
            return parameterType;
        }
        if (argumentType != null && !argumentType.toString().equals("<nulltype>")) {
            return argumentType;
        }
        return parameterType;
    }

    private static String constructorInputName(String methodParameterName, int constructorInputOrdinal, String constructorParameterName) {
        return "_ctor_"
            + sanitize(methodParameterName)
            + "_"
            + indexName(constructorInputOrdinal)
            + "_"
            + sanitize(constructorParameterName);
    }

    private static String indexName(int index) {
        String[] names = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        if (index < names.length) {
            return names[index];
        }
        return "arg" + index;
    }

    private static String sanitize(String value) {
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sanitized.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        if (sanitized.length() == 0 || !Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized.insert(0, '_');
        }
        return sanitized.toString();
    }


    public GeneralizationRecipe.InputKind getKind() {
        return this.kind;
    }

    public boolean isConstructorArgument() {
        return this.kind == GeneralizationRecipe.InputKind.CTOR_ARG;
    }

    public boolean isReceiverConstructorArgument() {
        return this.kind == GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG;
    }

    public boolean isExpressionSite() {
        return this.kind == GeneralizationRecipe.InputKind.EXPRESSION_SITE;
    }

    public MethodParameter toMethodParameter() {
        return this.parameter;
    }

    public MethodArgument toMethodArgument() {
        return this.argument;
    }

    public CtExpression<?> getSourceExpression() {
        return this.sourceExpression;
    }
}
