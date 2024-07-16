package teralizer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.gson.Gson;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import teralizer.processing.*;
import teralizer.processing.task.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class TestGeneralizationRunner {
    public static void main(String[] args) throws Exception {
        // Arguments: [benchmark]
        // - [benchmark]: Path to the benchmark directory, e.g., ../benchmarks/.
        //new TestGeneralizationRunner().run(args[0]);
        new TestGeneralizationRunner().run();
    }

    // @TODO: Add JPF models for more native methods (e.g., SymbolicStringHandler)?
    // @TODO: Use jpf-nhandler.

    // @TODO: Use some workflow engine to manage the tasks.
    // @TODO: Make individual tasks skip-able (=> "caching"?).
    // @TODO: Store intermediate results more often than just after a full run.
    // @TODO: Allow white-/blacklisting of individual tests/classes.
    // @TODO: Store runtime information for each generalized test.
    // @TODO: Compare how much longer test execution takes with JPF compared to "normally".
    // @TODO: Decide how to deal with files that are already created (perhaps from earlier runs).
    // @TODO: Add support for Maven projects.

    public void run() throws IOException {
        // @TODO: Get project directories from input args.
        // @TODO: Add support for analysis of multiple project directories in a single run.
        //   That way, we can share some of the initialization, and - more importantly - can perform
        //   the analysis tasks after ALL projects have been processed / generalized.
        Path projectPath = Paths.get("/Users/joaichberger/Projects/test-generalization-example");

        JavaParser javaParser = this.createJavaParser(projectPath);
        VelocityEngine velocityEngine = this.createVelocityEngine();

        Gson gson = new Gson();

        DSLContext create = DSL.using("jdbc:sqlite:/Users/joaichberger/Projects/test-generalization/database/db.sqlite");

        TaskRunner taskRunner = new TaskRunner(create);

        ProjectSetupTask projectSetupTask = new ProjectSetupTask(create);
        ProjectBuildTask projectBuildTask = new ProjectBuildTask();
        TestDetectionTask testDetectionTask = new TestDetectionTask(create, javaParser, gson);
        JpfInstrumentationTask jpfInstrumentationTask = new JpfInstrumentationTask(velocityEngine);
        JpfExecutionTask jpfExecutionTask = new JpfExecutionTask();
        TestGeneralizationTask testGeneralizationTask = new TestGeneralizationTask(create, velocityEngine, javaParser, gson);

        // @TODO: Add shutdown handler.

        ProjectRecord projectRecord = taskRunner.runTask(ProcessingStage.PROJECT_SETUP, projectSetupTask.create(projectPath));
        List<TestRecord> testRecords = taskRunner.runTask(ProcessingStage.TEST_DETECTION, testDetectionTask.create(projectRecord));

        // @TODO: Attempt an initial project build to see whether the project is even buildable.
        //   Note that a failed build does not necessarily imply a "broken" project.
        //   We might just be trying to build the project "the wrong way".

        for (TestRecord testRecord : testRecords) {
            try {
                // @TODO: Catch errors in each task to log them.
                //   However, still rethrow them to end the execution.
                taskRunner.runTask(ProcessingStage.JPF_INSTRUMENTATION, jpfInstrumentationTask.create(projectRecord, testRecord));
                taskRunner.runTask(ProcessingStage.PROJECT_BUILDING_INSTRUMENTED, projectBuildTask.create(projectRecord));
                taskRunner.runTask(ProcessingStage.JPF_EXECUTION, jpfExecutionTask.create(testRecord));

                // @TODO: Perform generalization for all tool variants + settings.
                taskRunner.runTask(ProcessingStage.TEST_GENERALIZATION, testGeneralizationTask.create(testRecord, "naive"));
            } catch (Exception e) {
                System.out.println(e);
                e.printStackTrace();
            }
        }

        // @TODO: Store file paths relative to the teralizer root directory.
        //   This is necessary to ensure the portability of the collected data.
        //   Also, this makes anonymization for double-blind review easier.

        Result<Record> generalizationRecords = create.select(Tables.GENERALIZATION.fields())
            .from(Tables.PROJECT)
            .join(Tables.TEST)
            .on(Tables.PROJECT.ID.eq(Tables.TEST.PROJECT_ID))
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.PROJECT.ID.eq(projectRecord.getId()))
            .fetch();

        Result<Record> taskRecords = create.select(Tables.TASK.fields())
            .from(Tables.PROJECT)
            .join(Tables.TASK)
            .on(Tables.PROJECT.ID.eq(Tables.TASK.PROJECT_ID))
            .where(Tables.PROJECT.ID.eq(projectRecord.getId()))
            .fetch();

        System.out.println(projectRecord);
        System.out.println(testRecords);
        System.out.println(generalizationRecords);
        System.out.println(taskRecords);

        // @TODO: Store the pre-condition and post-condition of every test(method) in z3 representation (?).
        //   We need a z3 representation to identify duplicate tests.
        // @TODO: Implement test duplication detection.

        // @TODO: Execute @Before / @BeforeClass / @After / @AfterAll methods.
        // @TODO: Think about using JUnit InvocationInterceptor + @ExtendWith to handle more tests.
        // @TODO: Think about using Gradle's "test { executable = '/path/to/custom/java' }" to execute tests with SPF.

        // @TODO: Calculate how often multiple tests cover the same path / partition.

        // @TODO: Evaluate simplification of inputs + outputs.
    }

    public JavaParser createJavaParser(Path projectPath) {
        Path mainSrcPath = projectPath.resolve("src/main/java");
        Path testSrcPath = projectPath.resolve("src/test/java");

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver(
            new JavaParserTypeSolver(mainSrcPath),
            new JavaParserTypeSolver(testSrcPath),
            new ReflectionTypeSolver()
        );

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));

        return new JavaParser(configuration);
    }

    public VelocityEngine createVelocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        return velocityEngine;
    }
}
