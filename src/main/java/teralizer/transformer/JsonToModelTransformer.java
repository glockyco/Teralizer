package teralizer.transformer;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.Arrays;
import teralizer.domain.*;
import teralizer.domain.Error;

public class JsonToModelTransformer {
    private final Gson gson;

    public JsonToModelTransformer() {
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.setPrettyPrinting();

        builder.registerTypeAdapter(Operation.class, new OperationDeserializer());
        builder.registerTypeAdapter(Operator.class, new OperatorDeserializer());
        builder.registerTypeAdapter(Constant.class, new ConstantDeserializer());
        builder.registerTypeAdapter(Variable.class, new VariableDeserializer());
        builder.registerTypeAdapter(ArrayExpression.class, new ArrayExpressionDeserializer());
        builder.registerTypeAdapter(ArrayElementExpression.class, new ArrayElementExpressionDeserializer());
        builder.registerTypeAdapter(Invocation.class, new InvocationDeserializer());
        builder.registerTypeAdapter(Not.class, new NotDeserializer());
        builder.registerTypeAdapter(teralizer.domain.Error.class, new ErrorDeserializer());
        builder.registerTypeAdapter(ExceptionModel.class, new ExceptionDeserializer());

        builder.registerTypeHierarchyAdapter(Model.class, new ModelDeserializer());

        this.gson = builder.create();
    }

    public Model transform(String json) {
        return this.gson.fromJson(json, Model.class);
    }

    private static class ModelDeserializer implements JsonDeserializer<Model> {
        @Override
        public Model deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String packageName = Model.class.getPackage().getName();
            String className = packageName + "." + jsonObject.get("_type").getAsString();

            try {
                return context.deserialize(jsonElement, Class.forName(className));
            } catch (ClassNotFoundException exception) {
                throw new JsonParseException("Unknown class: " + className, exception);
            }
        }
    }

    private static class OperationDeserializer implements JsonDeserializer<Operation> {
        @Override
        public Operation deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            Expression left = context.deserialize(jsonObject.get("left"), Expression.class);
            Operator op = context.deserialize(jsonObject.get("op"), Operator.class);
            Expression right = context.deserialize(jsonObject.get("right"), Expression.class);

            return new Operation(left, op, right);
        }
    }

    private static class OperatorDeserializer implements JsonDeserializer<Operator> {
        @Override
        public Operator deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            return Operator.get(jsonElement.getAsJsonObject().get("symbol").getAsString());
        }
    }

    private static class ConstantDeserializer implements JsonDeserializer<Constant> {
        @Override
        public Constant deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            TypeDomain domain = TypeDomain.valueOf(jsonObject.get("domain").getAsString());
            JsonElement value = jsonObject.get("value");
            switch (domain) {
                case INTEGER:
                    return new Constant(value.getAsLong(), domain);
                case REAL:
                    return new Constant(value.getAsDouble(), domain);
                case STRING:
                    return new Constant(value.getAsString(), domain);
                default:
                    throw new JsonParseException("Cannot deserialize constant domain: " + domain);
            }
        }
    }

    private static class VariableDeserializer implements JsonDeserializer<Variable> {
        @Override
        public Variable deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            return new Variable(
                jsonObject.get("name").getAsString(),
                TypeDomain.valueOf(jsonObject.get("domain").getAsString()));
        }
    }

    private static class ArrayExpressionDeserializer implements JsonDeserializer<ArrayExpression> {
        @Override
        public ArrayExpression deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            String name = jsonObject.get("name").getAsString();
            String elementType = jsonObject.get("elementType").getAsString();

            return new ArrayExpression(name, elementType);
        }
    }

    private static class ArrayElementExpressionDeserializer implements JsonDeserializer<ArrayElementExpression> {
        @Override
        public ArrayElementExpression deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            String arrayName = jsonObject.get("arrayName").getAsString();
            String elementType = jsonObject.get("elementType").getAsString();
            Expression elementSelector = context.deserialize(jsonObject.get("elementSelector"), Expression.class);

            return new ArrayElementExpression(arrayName, elementType, elementSelector);
        }
    }

    private static class InvocationDeserializer implements JsonDeserializer<Invocation> {
        @Override
        public Invocation deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            Expression receiver = context.deserialize(jsonObject.get("receiver"), Expression.class);
            JsonElement qualifierElement = jsonObject.get("qualifier");
            String qualifier = qualifierElement == null || qualifierElement.isJsonNull()
                ? null
                : qualifierElement.getAsString();
            String method = jsonObject.get("method").getAsString();
            Expression[] args = context.deserialize(jsonObject.get("args"), Expression[].class);

            return new Invocation(receiver, qualifier, method, Arrays.asList(args));
        }
    }

    private static class NotDeserializer implements JsonDeserializer<Not> {
        @Override
        public Not deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            Expression operand = context.deserialize(jsonObject.get("operand"), Expression.class);
            return new Not(operand);
        }
    }

    private static class ErrorDeserializer implements JsonDeserializer<teralizer.domain.Error> {
        @Override
        public teralizer.domain.Error deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            String errorType = jsonObject.get("type").getAsString();
            String message = jsonObject.get("message").getAsString();

            return new Error(errorType, message);
        }
    }

    public static class ExceptionDeserializer implements JsonDeserializer<ExceptionModel> {
        @Override
        public ExceptionModel deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            String clazz = jsonObject.get("class").getAsString();
            String message = jsonObject.get("message").getAsString();

            return new ExceptionModel(clazz, message);
        }
    }
}
