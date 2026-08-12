package teralizer.processing.task;

import com.google.gson.Gson;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.junit.Assert;
import teralizer.jpf.OutputSpecClassifier.OutputSpecClass;

/**
 * The extraction rollup reports how many assertions carry an input specification, an output
 * specification, and both. The three counts answer different questions, so a shared predicate
 * makes the funnel flat and hides where extraction lost its result.
 */
class JpfAnalysisTaskSpecCountTest {

    private static final Gson GSON = new Gson();

    private static AssertionRecord assertion(int inputJavaSize, String outputSpecClass) {
        AssertionRecord record = new AssertionRecord();
        record.setInputModelStatistics("{\"javaSize\":" + inputJavaSize + ",\"operationCount\":0}");
        record.setOutputSpecClass(outputSpecClass);
        return record;
    }

    @Example
    void inputSpecificationNeedsARenderedModel() {
        Assert.assertTrue(JpfAnalysisTask.hasInputSpecification(assertion(42, null), GSON));
        Assert.assertFalse(JpfAnalysisTask.hasInputSpecification(assertion(0, null), GSON));
    }

    @Example
    void aMissingStatisticsRecordIsNotAnInputSpecification() {
        AssertionRecord record = new AssertionRecord();
        record.setInputModelStatistics(null);
        Assert.assertFalse(JpfAnalysisTask.hasInputSpecification(record, GSON));
    }

    @Example
    void nullConcreteIsNotAnOutputSpecification() {
        Assert.assertFalse(JpfAnalysisTask.hasOutputSpecification(
            assertion(1, OutputSpecClass.NULL_CONCRETE.name())));
    }

    @Example
    void anUnclassifiedAssertionIsNotAnOutputSpecification() {
        Assert.assertFalse(JpfAnalysisTask.hasOutputSpecification(assertion(1, null)));
    }

    @Example
    void everyOtherClassIsAnOutputSpecification() {
        for (OutputSpecClass outputSpecClass : OutputSpecClass.values()) {
            if (outputSpecClass == OutputSpecClass.NULL_CONCRETE) {
                continue;
            }
            Assert.assertTrue(
                outputSpecClass + " is an oracle the pipeline can use",
                JpfAnalysisTask.hasOutputSpecification(assertion(1, outputSpecClass.name()))
            );
        }
    }

    @Example
    void theThreeCountsSeparate() {
        AssertionRecord inputOnly = assertion(42, OutputSpecClass.NULL_CONCRETE.name());
        AssertionRecord both = assertion(42, OutputSpecClass.SYMBOLIC.name());
        AssertionRecord neither = assertion(0, OutputSpecClass.NULL_CONCRETE.name());

        Assert.assertTrue(JpfAnalysisTask.hasInputSpecification(inputOnly, GSON));
        Assert.assertFalse(JpfAnalysisTask.hasOutputSpecification(inputOnly));

        Assert.assertTrue(JpfAnalysisTask.hasInputSpecification(both, GSON));
        Assert.assertTrue(JpfAnalysisTask.hasOutputSpecification(both));

        Assert.assertFalse(JpfAnalysisTask.hasInputSpecification(neither, GSON));
        Assert.assertFalse(JpfAnalysisTask.hasOutputSpecification(neither));
    }
}
