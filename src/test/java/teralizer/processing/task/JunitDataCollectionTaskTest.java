package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;

public class JunitDataCollectionTaskTest {
    @Example
    void snapshotsJqwikValueLogsBesideJunitReports() {
        Path dataPath = Paths.get("data/jarvis-scoreboard/commons-lang-3.5");

        Path snapshotPath = JunitDataCollectionTask.getJunitJqwikValueLogPath(
            dataPath,
            7L,
            42L,
            "IMPROVED"
        );

        Assert.assertEquals(
            dataPath.resolve("project-id-7/jqwik-data/42.IMPROVED.junit.tsv"),
            snapshotPath
        );
    }
}
