package teralizer.transformer;

import teralizer.domain.Constant;
import teralizer.domain.Expression;
import teralizer.domain.TypeDomain;

public class ValueToModelTransformer {
    public Expression transform(Object object) {
        if (object instanceof Integer) {
            return this.transform((Integer) object);
        } else if (object instanceof Double) {
            return this.transform((Double) object);
        } else if (object instanceof String) {
            return this.transform((String) object);
        }
        throw new RuntimeException("Unable to transform object '" + object + "' of class '" + object.getClass() + "' to model.");
    }

    public Constant transform(Integer value) {
        return new Constant(value.longValue(), TypeDomain.INTEGER);
    }

    public Constant transform(Double value) {
        return new Constant(value, TypeDomain.REAL);
    }

    public Constant transform(String value) {
        return new Constant(value, TypeDomain.STRING);
    }
}
