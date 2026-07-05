package teralizer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes canonical shape keys from model expression trees. Literal payloads are
 * omitted so telemetry can group constraints by structure and type domain.
 */
public class ShapeFolder extends ModelFolder<String> {
    @Override
    public String fold(Constant constant) {
        return "Constant:" + constant.domain.name();
    }

    @Override
    public String fold(Variable variable) {
        return "Variable:" + variable.domain.name();
    }

    @Override
    public String fold(ArrayExpression expression) {
        return "Array:" + expression.elementType;
    }

    @Override
    public String fold(ArrayElementExpression expression, String elementSelector) {
        return "ArrayElement:" + expression.elementType + "[" + elementSelector + "]";
    }

    @Override
    public String fold(Invocation invocation, String receiver, List<String> args) {
        List<String> operands = new ArrayList<>();
        if (receiver != null) {
            operands.add(receiver);
        }
        operands.addAll(args);
        return invocation.method + "(" + operands.stream().collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String fold(Not not, String operand) {
        return "!(" + operand + ")";
    }

    @Override
    public String fold(Operation operation, String left, String right) {
        return operation.op.name() + "(" + left + "," + right + ")";
    }

    @Override
    public String fold(Operator operator) {
        return operator.name();
    }

    @Override
    public String fold(Error error) {
        return "Error";
    }

    @Override
    public String fold(ExceptionModel exceptionModel) {
        return "Exception:" + exceptionModel.name;
    }

}
