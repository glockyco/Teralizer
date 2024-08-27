package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class TestFilteringTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFilteringTask.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        if (this.testRecord.getTestedClassPath() == null) {
            reportInfo.accept("Filtering because test.tested_class_path is null.");
            LOGGER.atDebug().log("Filtering test with ID {} because test.tested_class_path is null.", this.testRecord.getId());
            return;
        }

        if (this.testRecord.getTestedClassName() == null) {
            reportInfo.accept("Filtering because test.tested_class_name is null.");
            LOGGER.atDebug().log("Filtering test with ID {} because test.tested_class_name is null.", this.testRecord.getId());
            return;
        }

        if (this.testRecord.getTestedMethodName() == null) {
            reportInfo.accept("Filtering because test.tested_method_name is null.");
            LOGGER.atDebug().log("Filtering test with ID {} because test.tested_method_name is null.", this.testRecord.getId());
            return;
        }

        List<AssertionRecord> assertions = create.selectFrom(Tables.ASSERTION)
            .where(Tables.ASSERTION.TEST_ID.equal(this.testRecord.getId()))
            .fetch();

        if (assertions.size() != 1) {
            reportInfo.accept("Filtering because assertion.size() != 1.");
            LOGGER.atDebug().log("Filtering test with ID {} because assertions.size() != 1.", this.testRecord.getId());
            return;
        }

        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);
        CompilationUnit compilationUnit = javaParser.parse(Paths.get(this.testRecord.getTestClassPath())).getResult().get();
        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(this.testRecord.getTestClassName()).get();

        if (testClassDeclaration.getExtendedTypes().isNonEmpty()) {
            // @TODO: Add generalization support for classes that implement interfaces or extend other (abstract) classes.
            //   ---
            //   Test classes that implement interfaces or extend other (abstract) classes might cause
            //   problems because the implementation of the generalization task does not currently include
            //   all the code of the original test class in the generalized class.
            //   ---
            //   Consequently, implementations for abstract methods might be missing in the generalized
            //   class, thus causing build failures. Similarly, overrides of non-abstract parent methods
            //   might be missing, thus causing unintended behavior.
            String qualifiedTestClassName = this.testRecord.getTestClassPackage() + "." + this.testRecord.getTestClassName();
            reportInfo.accept("Filtering because class " + qualifiedTestClassName + " cannot be safely generalized (has parents: " + testClassDeclaration.getExtendedTypes() + ").");
            LOGGER.atDebug().log("Filtering test with ID {} because it cannot be safely generalized.", this.testRecord.getId());
            return;
        }

        scheduleTask.accept(new JpfInstrumentationTask(ProcessingStage.JPF_INSTRUMENTATION, this.projectRecord, this.testRecord));
    }

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return this.testRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        return "TestFilteringTask{" +
            "stage=" + this.stage +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + this.testRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestFilteringTask)) return false;
        TestFilteringTask that = (TestFilteringTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.testRecord.getId(), that.testRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId(), this.testRecord.getId());
    }
}
