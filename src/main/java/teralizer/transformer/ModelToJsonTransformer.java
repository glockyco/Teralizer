package teralizer.transformer;

import com.google.gson.*;
import teralizer.domain.Error;
import teralizer.domain.*;

import java.lang.reflect.Type;

public class ModelToJsonTransformer {
    private final Gson gson;

    public ModelToJsonTransformer() {
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.setPrettyPrinting();
        builder.disableHtmlEscaping();

        builder.registerTypeAdapter(Operation.class, new OperationSerializer());
        builder.registerTypeAdapter(Operator.class, new OperatorSerializer());
        builder.registerTypeAdapter(Constant.class, new ConstantSerializer());
        builder.registerTypeAdapter(Variable.class, new VariableSerializer());
        builder.registerTypeAdapter(ArrayExpression.class, new ArrayExpressionSerializer());
        builder.registerTypeAdapter(ArrayElementExpression.class, new ArrayElementExpressionSerializer());
        builder.registerTypeAdapter(Invocation.class, new InvocationSerializer());
        builder.registerTypeAdapter(Not.class, new NotSerializer());
        builder.registerTypeAdapter(teralizer.domain.Error.class, new ErrorSerializer());
        builder.registerTypeAdapter(ExceptionModel.class, new ExceptionSerializer());

        this.gson = builder.create();
    }

    public String transform(Model model) {
        return this.gson.toJson(model);
    }

    private static class OperationSerializer implements JsonSerializer<Operation> {
        @Override
        public JsonElement serialize(Operation operation, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(operation.getClass().getSimpleName()));
            jsonObject.add("left", context.serialize(operation.left));
            jsonObject.add("op", context.serialize(operation.op));
            jsonObject.add("right", context.serialize(operation.right));

            return jsonObject;
        }
    }

    private static class OperatorSerializer implements JsonSerializer<Operator> {
        @Override
        public JsonElement serialize(Operator operator, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(operator.getClass().getSimpleName()));
            jsonObject.add("symbol", new JsonPrimitive(operator.toString()));

            return jsonObject;
        }
    }

    private static class ConstantSerializer implements JsonSerializer<Constant> {
        @Override
        public JsonElement serialize(Constant constant, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(constant.getClass().getSimpleName()));
            switch (constant.domain) {
                case INTEGER:
                    jsonObject.add("value", new JsonPrimitive(((Number) constant.value).longValue()));
                    break;
                case REAL:
                    jsonObject.add("value", new JsonPrimitive(((Number) constant.value).doubleValue()));
                    break;
                case STRING:
                    jsonObject.add("value", new JsonPrimitive((String) constant.value));
                    break;
                default:
                    throw new JsonParseException("Cannot serialize constant domain: " + constant.domain);
            }
            jsonObject.add("domain", new JsonPrimitive(constant.domain.name()));

            return jsonObject;
        }
    }

    private static class VariableSerializer implements JsonSerializer<Variable> {
        @Override
        public JsonElement serialize(Variable variable, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(variable.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(variable.name));
            jsonObject.add("domain", new JsonPrimitive(variable.domain.name()));

            return jsonObject;
        }
    }

    private static class ArrayExpressionSerializer implements JsonSerializer<ArrayExpression> {
        @Override
        public JsonElement serialize(ArrayExpression expression, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(expression.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(expression.name));
            jsonObject.add("elementType", new JsonPrimitive(expression.elementType));

            return jsonObject;
        }
    }

    private static class ArrayElementExpressionSerializer implements JsonSerializer<ArrayElementExpression> {
        @Override
        public JsonElement serialize(ArrayElementExpression expression, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(expression.getClass().getSimpleName()));
            jsonObject.add("arrayName", new JsonPrimitive(expression.arrayName));
            jsonObject.add("elementType", new JsonPrimitive(expression.elementType));
            jsonObject.add("elementSelector", context.serialize(expression.elementSelector));

            return jsonObject;
        }
    }

    private static class InvocationSerializer implements JsonSerializer<Invocation> {
        @Override
        public JsonElement serialize(Invocation invocation, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            JsonArray jsonArgs = new JsonArray(invocation.args.size());
            for (Expression arg : invocation.args) {
                jsonArgs.add(context.serialize(arg));
            }

            jsonObject.add("_type", new JsonPrimitive(invocation.getClass().getSimpleName()));
            jsonObject.add("receiver", context.serialize(invocation.receiver));
            jsonObject.add("qualifier", invocation.qualifier == null ? JsonNull.INSTANCE : new JsonPrimitive(invocation.qualifier));
            jsonObject.add("method", new JsonPrimitive(invocation.method));
            jsonObject.add("args", jsonArgs);

            return jsonObject;
        }
    }

    private static class NotSerializer implements JsonSerializer<Not> {
        @Override
        public JsonElement serialize(Not not, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(not.getClass().getSimpleName()));
            jsonObject.add("operand", context.serialize(not.operand));

            return jsonObject;
        }
    }

    private static class ErrorSerializer implements JsonSerializer<teralizer.domain.Error> {
        @Override
        public JsonElement serialize(Error error, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(error.getClass().getSimpleName()));
            jsonObject.add("type", new JsonPrimitive(error.type));
            jsonObject.add("message", new JsonPrimitive(error.message));

            return jsonObject;
        }
    }

    private static class ExceptionSerializer implements JsonSerializer<ExceptionModel> {
        @Override
        public JsonElement serialize(ExceptionModel exceptionModel, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(exceptionModel.getClass().getSimpleName()));
            jsonObject.add("class", new JsonPrimitive(exceptionModel.name));
            jsonObject.add("message", exceptionModel.message == null ? JsonNull.INSTANCE : new JsonPrimitive(exceptionModel.message));

            return jsonObject;
        }
    }
}
