package teralizer.transformer;

import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import gov.nasa.jpf.symbc.numeric.SymbolicReal;
import gov.nasa.jpf.symbc.string.StringComparator;
import gov.nasa.jpf.symbc.string.StringConstant;
import gov.nasa.jpf.symbc.string.StringPathCondition;
import gov.nasa.jpf.symbc.string.StringSymbolic;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class SpfToModelTransformerSymbolNameTest {

    @Example
    void preservesNumericSegmentsWhenRemovingJpfIntegerSuffix() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new Variable("_ctor_interval_0_lower", TypeDomain.INTEGER),
            transformer.transform(new SymbolicInteger("_ctor_interval_0_lower_1_SYMINT"))
        );
    }

    @Example
    void removesOnlyTerminalJpfSuffixFromSimpleIntegerNames() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new Variable("value", TypeDomain.INTEGER),
            transformer.transform(new SymbolicInteger("value_2_SYMINT"))
        );
    }

    @Example
    void preservesNumericSegmentsForRealAndStringVariables() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new Variable("_ctor_interval_0_weight", TypeDomain.REAL),
            transformer.transform(new SymbolicReal("_ctor_interval_0_weight_3_SYMREAL", 0.0, 1.0))
        );

        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(
            StringComparator.EQUALS,
            new StringSymbolic("_ctor_label_1_text_4_SYMSTRING"),
            new StringConstant("ok")
        );

        Assert.assertEquals(
            new Invocation(new Variable("_ctor_label_1_text", TypeDomain.STRING), null, "equals", java.util.Collections.singletonList(new Constant("ok", TypeDomain.STRING))),
            transformer.transform(pathCondition)
        );
    }
}
