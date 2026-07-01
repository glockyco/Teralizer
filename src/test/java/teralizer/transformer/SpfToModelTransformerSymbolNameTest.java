package teralizer.transformer;

import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import gov.nasa.jpf.symbc.numeric.SymbolicReal;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.string.StringComparator;
import gov.nasa.jpf.symbc.string.StringConstant;
import gov.nasa.jpf.symbc.string.StringPathCondition;
import gov.nasa.jpf.symbc.string.StringSymbolic;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;
import teralizer.domain.Invocation;
import teralizer.domain.VariableString;
import teralizer.domain.ConstantString;

public class SpfToModelTransformerSymbolNameTest {

    @Example
    void preservesNumericSegmentsWhenRemovingJpfIntegerSuffix() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new VariableInteger("_ctor_interval_0_lower"),
            transformer.transform(new SymbolicInteger("_ctor_interval_0_lower_1_SYMINT"))
        );
    }

    @Example
    void removesOnlyTerminalJpfSuffixFromSimpleIntegerNames() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new VariableInteger("value"),
            transformer.transform(new SymbolicInteger("value_2_SYMINT"))
        );
    }

    @Example
    void preservesNumericSegmentsForRealAndStringVariables() {
        SpfToModelTransformer transformer = new SpfToModelTransformer();

        Assert.assertEquals(
            new VariableReal("_ctor_interval_0_weight"),
            transformer.transform(new SymbolicReal("_ctor_interval_0_weight_3_SYMREAL", 0.0, 1.0))
        );

        StringPathCondition pathCondition = new StringPathCondition(new PathCondition());
        pathCondition._addDet(
            StringComparator.EQUALS,
            new StringSymbolic("_ctor_label_1_text_4_SYMSTRING"),
            new StringConstant("ok")
        );

        Assert.assertEquals(
            new Invocation(new VariableString("_ctor_label_1_text"), null, "equals", java.util.Collections.singletonList(new ConstantString("ok"))),
            transformer.transform(pathCondition)
        );
    }
}
