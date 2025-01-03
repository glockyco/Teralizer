package teralizer.processing.task;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFNativePeerException;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.jpf.TestGeneralizationListener;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
            this.executeTask(context);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<Record> records = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId());
        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfExecutionTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void executeTask(TaskContext context) {
        Config config = JPF.createConfig(new String[]{this.assertionRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        jpf.addListener(new TestGeneralizationListener(config));

        try {
            jpf.run();
        } catch (JPFNativePeerException e) {
            // Exception that is (likely) due to JPFs incorrect handling of shadowing.
            // See https://github.com/glockyco/test-generalization/issues/37 for further details
            throw new RuntimeException("Failed JPF execution due to exception in native peers.", e);
        }

        if (jpf.foundErrors()) {
            List<Error> errors = jpf.getSearchErrors();
            String errorMessage = "Identified " + errors.size() + " error(s) during JPF execution.\n\n--\n\n" +
                jpf.getSearchErrors().stream().map(
                    e -> e.getDescription() + "\n\n" + e.getDetails()
                ).collect(Collectors.joining("\n--\n\n"));
            throw new RuntimeException(errorMessage);
        }
    }
}
