package teralizer.tasks;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationListener;

public class JpfExecutionTask {

    private final Task task = Task.JPF_EXECUTION;

    public void run(TestRecord testRecord) {
        this.runJpf(testRecord);
    }

    private void runJpf(TestRecord testRecord) {
        Config config = JPF.createConfig(new String[]{testRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        jpf.addListener(new TestGeneralizationListener(config));
        jpf.run();
    }
}
