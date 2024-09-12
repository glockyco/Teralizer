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
        builder.registerTypeAdapter(ConstantInteger.class, new ConstantIntegerSerializer());
        builder.registerTypeAdapter(ConstantReal.class, new ConstantRealSerializer());
        builder.registerTypeAdapter(ConstantString.class, new ConstantStringSerializer());
        builder.registerTypeAdapter(VariableInteger.class, new VariableIntegerSerializer());
        builder.registerTypeAdapter(VariableReal.class, new VariableRealSerializer());
        builder.registerTypeAdapter(VariableString.class, new VariableStringSerializer());
        builder.registerTypeAdapter(ArrayExpression.class, new ArrayExpressionSerializer());
        builder.registerTypeAdapter(ArrayElementExpression.class, new ArrayElementExpressionSerializer());
        builder.registerTypeAdapter(SymbolicIntegerFunction.class, new SymbolicIntegerFunctionSerializer());
        builder.registerTypeAdapter(SymbolicRealFunction.class, new SymbolicRealFunctionSerializer());
        builder.registerTypeAdapter(SymbolicStringFunction.class, new SymbolicStringFunctionSerializer());
        builder.registerTypeAdapter(teralizer.domain.Error.class, new ErrorSerializer());

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

    private static class ConstantIntegerSerializer implements JsonSerializer<ConstantInteger> {
        @Override
        public JsonElement serialize(ConstantInteger constant, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(constant.getClass().getSimpleName()));
            jsonObject.add("value", new JsonPrimitive(constant.value));

            return jsonObject;
        }
    }

    private static class ConstantRealSerializer implements JsonSerializer<ConstantReal> {
        @Override
        public JsonElement serialize(ConstantReal constant, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(constant.getClass().getSimpleName()));
            jsonObject.add("value", new JsonPrimitive(constant.value));

            return jsonObject;
        }
    }

    private static class ConstantStringSerializer implements JsonSerializer<ConstantString> {
        @Override
        public JsonElement serialize(ConstantString constant, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(constant.getClass().getSimpleName()));
            jsonObject.add("value", new JsonPrimitive(constant.value));

            return jsonObject;
        }
    }

    private static class VariableIntegerSerializer implements JsonSerializer<VariableInteger> {
        @Override
        public JsonElement serialize(VariableInteger variable, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(variable.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(variable.name));

            return jsonObject;
        }
    }

    private static class VariableRealSerializer implements JsonSerializer<VariableReal> {
        @Override
        public JsonElement serialize(VariableReal variable, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(variable.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(variable.name));

            return jsonObject;
        }
    }

    private static class VariableStringSerializer implements JsonSerializer<VariableString> {
        @Override
        public JsonElement serialize(VariableString variable, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(variable.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(variable.name));

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

    private static class SymbolicIntegerFunctionSerializer implements JsonSerializer<SymbolicIntegerFunction> {
        @Override
        public JsonElement serialize(SymbolicIntegerFunction function, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            JsonArray jsonArgs = new JsonArray(function.args.length);
            for (Expression arg: function.args) {
                jsonArgs.add(context.serialize(arg));
            }

            jsonObject.add("_type", new JsonPrimitive(function.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(function.name));
            jsonObject.add("args", jsonArgs);

            return jsonObject;
        }
    }

    private static class SymbolicRealFunctionSerializer implements JsonSerializer<SymbolicRealFunction> {
        @Override
        public JsonElement serialize(SymbolicRealFunction function, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            JsonArray jsonArgs = new JsonArray(function.args.length);
            for (Expression arg: function.args) {
                jsonArgs.add(context.serialize(arg));
            }

            jsonObject.add("_type", new JsonPrimitive(function.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(function.name));
            jsonObject.add("args", jsonArgs);

            return jsonObject;
        }
    }

    private static class SymbolicStringFunctionSerializer implements JsonSerializer<SymbolicStringFunction> {
        @Override
        public JsonElement serialize(SymbolicStringFunction function, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            JsonArray jsonArgs = new JsonArray(function.args.length);
            for (Expression arg: function.args) {
                jsonArgs.add(context.serialize(arg));
            }

            jsonObject.add("_type", new JsonPrimitive(function.getClass().getSimpleName()));
            jsonObject.add("name", new JsonPrimitive(function.name));
            jsonObject.add("args", jsonArgs);

            return jsonObject;
        }
    }

    private static class ErrorSerializer implements JsonSerializer<teralizer.domain.Error> {
        @Override
        public JsonElement serialize(Error error, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            jsonObject.add("_type", new JsonPrimitive(error.getClass().getSimpleName()));
            jsonObject.add("error_type", new JsonPrimitive(error.type));
            jsonObject.add("message", new JsonPrimitive(error.message));

            return jsonObject;
        }
    }
}
