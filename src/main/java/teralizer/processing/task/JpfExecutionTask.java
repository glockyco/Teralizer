package teralizer.processing.task;

import com.google.gson.Gson;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFListenerException;
import gov.nasa.jpf.JPFNativePeerException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.jpf.CapturedInvocation;
import teralizer.jpf.ExtractionAborted;
import teralizer.jpf.ExtractionOutcome;
import teralizer.jpf.OutputSpecClassifier;
import teralizer.jpf.SpecificationExtractor;
import teralizer.jpf.TestGeneralizationListener;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.PipelineQueries;
import teralizer.spoon.analysis.GeneralizationRecipe;

public class JpfExecutionTask extends AbstractTask {

    public JpfExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null, null);
    }

    public JpfExecutionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            this.executeTask(context, reportInfo);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<Record> records = PipelineQueries.fetchIncludedAssertions(create, this.getProjectId());
        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfExecutionTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void executeTask(TaskContext context, Consumer<String> reportInfo) {
        Config config = JPF.createConfig(new String[]{this.assertionRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);

        try {
            jpf.run();
        } catch (JPFNativePeerException e) {
            // Exception that is (likely) due to JPFs incorrect handling of shadowing.
            // See https://github.com/glockyco/test-generalization/issues/37 for further details
            throw new RuntimeException("Failed JPF execution due to exception in native peers.", e);
        } catch (JPFListenerException e) {
            // JPF wraps a listener's throw; surface the typed extraction abort so the task is
            // recorded with its reason (and token), not JPF's wrapper.
            if (e.getCause() instanceof ExtractionAborted) {
                throw (ExtractionAborted) e.getCause();
            }
            if (e.getCause() instanceof teralizer.transformer.UnsupportedSpfTermException) {
                Throwable cause = e.getCause();
                ExtractionOutcome outcome = ExtractionOutcome.unsupportedTerm(cause.getMessage());
                throw new RuntimeException(this.assertionRecord.getInstrumentedMethodQualifiedName()
                    + " - " + outcome.getKind().name() + ": " + outcome.getDetail());
            }
            throw e;
        } catch (gov.nasa.jpf.symbc.string.UnsupportedSymbolicStringOpException e) {
            // SPF reached a String operation it cannot model; record a typed exclusion rather than
            // an untyped crash, matching how the other non-EXTRACTED outcomes surface.
            ExtractionOutcome outcome = ExtractionOutcome.unsupportedTerm(e.getMessage());
            throw new RuntimeException(this.assertionRecord.getInstrumentedMethodQualifiedName()
                + " - " + outcome.getKind().name() + ": " + outcome.getDetail());
        }

        if (jpf.foundErrors()) {
            List<Error> errors = jpf.getSearchErrors();
            String errorMessage = "Identified " + errors.size() + " error(s) during JPF execution.\n\n--\n\n" +
                jpf.getSearchErrors().stream().map(
                    e -> e.getDescription() + "\n\n" + e.getDetails()
                ).collect(Collectors.joining("\n--\n\n"));
            throw new RuntimeException(errorMessage);
        }

        if (!jpf.getVM().isInitialized()) {
            throw new RuntimeException("Failed to initialize VM during JPF execution.");
        }

        boolean targetNotEnteredIsFailure = this.targetNotEnteredIsFailure(context);
        ExtractionOutcome outcome = ExtractionOutcome.fromState(
            listener.wasTargetEntered(), listener.getInvocation() != null, targetNotEnteredIsFailure);
        if (outcome.getKind() != ExtractionOutcome.Kind.EXTRACTED) {
            throw new RuntimeException(this.assertionRecord.getInstrumentedMethodQualifiedName()
                + " - " + outcome.getKind().name() + ": " + outcome.getDetail());
        }
        CapturedInvocation invocation = listener.getInvocation();
        new SpecificationExtractor().write(
            invocation,
            Paths.get(this.assertionRecord.getInputValuesPath()),
            Paths.get(this.assertionRecord.getOutputValuePath()),
            Paths.get(this.assertionRecord.getInputSpecificationPath()),
            Paths.get(this.assertionRecord.getOutputSpecificationPath()));
        this.assertionRecord.setOutputSpecClass(OutputSpecClassifier.classify(invocation).name());
        this.assertionRecord.setOutputIsLiteral(listener.getOutputIsLiteral());
        this.assertionRecord.setConcretizationEvents(listener.getConcretizationEvents());
        this.assertionRecord.setPostConcretizationDivergenceRisk(
            listener.getPostConcretizationDivergenceRisk());
        Map<String, Integer> concretizedMethods = listener.getConcretizedMethods();
        Gson gson = context.get(TaskContext.GSON);
        this.assertionRecord.setConcretizedMethods(
            concretizedMethods.isEmpty() ? null : gson.toJson(concretizedMethods));
        this.assertionRecord.store();
    }

    private boolean targetNotEnteredIsFailure(TaskContext context) {
        GeneralizationRecipe recipe = GeneralizationRecipe.fromJson(
            context.get(TaskContext.GSON),
            this.assertionRecord.getGeneralizationRecipe()
        );
        return recipe.getInputSites().stream()
            .noneMatch(site -> site.getKind() == GeneralizationRecipe.InputKind.EXPRESSION_SITE);
    }
}
