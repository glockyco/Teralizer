package teralizer.processing.task;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationListener;

public class JpfExecutionTask extends AbstractTask {

    public TaskCallable<Void> create(TestRecord testRecord) {
        this.setProjectId(testRecord.getProjectId());
        this.setTestId(testRecord.getId());

        return new TaskCallable<>(this, () -> {
            this.runJpf(testRecord);
            return null;
        });
    }

    private void runJpf(TestRecord testRecord) {
        Config config = JPF.createConfig(new String[]{testRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        jpf.addListener(new TestGeneralizationListener(config));
        jpf.run();
    }
}
