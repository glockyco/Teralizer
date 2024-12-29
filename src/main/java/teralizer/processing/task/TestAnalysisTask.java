package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.spoon.analysis.TestAnalysis;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestAnalysisTask extends AbstractTask {

    public TestAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public TestAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            this.executeTask(context);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<TestRecord> testRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.projectRecord.getId()))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();

        for (TestRecord testRecord : testRecords) {
            scheduleTask.accept(new TestAnalysisTask(this.stage, this.projectRecord, testRecord));
        }
    }

    private void executeTask(TaskContext context) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);

        CtType<?> testClass = spoonLauncher.getFactory().Type().get(this.testRecord.getTestClassQualifiedName());
        CtMethod<?> testMethod = testClass.getMethod(this.testRecord.getTestMethodName());

        if (testMethod == null) {
            List<CtMethod<?>> testMethods = testClass.getMethodsByName(this.testRecord.getTestMethodName());
            if (testMethods.isEmpty()) {
                // This can happen if the test method was inherited from some other class.
                // The JUnit reports list the test as part of the child class then, but
                // the source code file of the child class does not contain the method.
                throw new RuntimeException("Method " + this.testRecord.getTestMethodName() + " not found in " + this.testRecord.getTestClassQualifiedName() + " (might be inherited).");
            } else {
                throw new RuntimeException("Method " + this.testRecord.getTestMethodQualifiedName() + " has parameters.");
            }
        }

        if (testMethod.getAnnotations().stream().noneMatch(a -> a.getType().getSimpleName().equals("Test"))) {
            throw new RuntimeException("Method " + this.testRecord.getTestMethodQualifiedName() + " has no @Test annotation.");
        }

        TestAnalysisTask.createAssertionRecords(this.testRecord, testMethod, create, gson);

        CtInvocation<?> assertion = TestAnalysis.findGeneralizableAssert(testMethod).orElse(null);
        CtInvocation<?> testedMethodCall = TestAnalysis.findTestedMethodCall(testMethod, assertion).orElse(null);

        if (testedMethodCall == null) {
            // Unable to identify tested method.
            return;
        }

        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();
        CtType<?> testedType = testedMethod == null ? null : testedMethod.getDeclaringType();

        if (testedMethod == null || testedType == null) {
            // Unable to identify tested method.
            return;
        }

        SourcePosition position = testedType.getPosition();
        String testedClassPath = Paths.get(System.getProperty("user.dir")).relativize(position.getFile().toPath()).toString();

        String packageName = testedType.getPackage().toString();
        String className = testedType.getSimpleName();
        String methodName = testedMethod.getSimpleName();

        String qualifiedClassName = testedType.getQualifiedName();
        String qualifiedMethodName = qualifiedClassName + "." + methodName;

        List<MethodParameter> testedMethodParameters = new ArrayList<>();
        for (CtParameter<?> param : testedMethod.getParameters()) {
            String paramType = param.getType().getQualifiedName();
            String paramName = param.getSimpleName();
            testedMethodParameters.add(new MethodParameter(paramType, paramName));
        }

        this.testRecord.setTestedFilePath(testedClassPath);
        this.testRecord.setTestedClassQualifiedName(qualifiedClassName);
        this.testRecord.setTestedMethodQualifiedName(qualifiedMethodName);
        this.testRecord.setTestedPackageName(packageName);
        this.testRecord.setTestedClassName(className);
        this.testRecord.setTestedMethodName(methodName);
        this.testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
        this.testRecord.setTestedMethodReturnType(testedMethod.getType().getQualifiedName());

        this.testRecord.store();
    }

    public static void createAssertionRecords(TestRecord testRecord, CtMethod<?> testMethod, DSLContext create, Gson gson) {
        List<AssertionRecord> assertionRecords = new ArrayList<>();

        List<CtInvocation<?>> assertMethodCalls = TestAnalysis.findAllAsserts(testMethod);
        for (CtInvocation<?> assertMethodCall : assertMethodCalls) {
            CtExecutableReference<?> assertMethodRef = assertMethodCall.getExecutable();
            String methodName = assertMethodRef.getSimpleName();
            String sourceCode = assertMethodCall.toString();

            List<MethodParameter> assertArguments = new ArrayList<>();
            for (CtExpression<?> argument : assertMethodCall.getArguments()) {
                String paramType = argument.getType().getQualifiedName();
                String paramValue = argument.toString();
                assertArguments.add(new MethodParameter(paramType, paramValue));
            }

            AssertionRecord assertionRecord = create.newRecord(Tables.ASSERTION);
            assertionRecord.setProjectId(testRecord.getProjectId());
            assertionRecord.setTestId(testRecord.getId());
            assertionRecord.setMethodName(methodName);
            assertionRecord.setMethodArgumentTypes(gson.toJson(assertArguments));
            assertionRecord.setSourceCode(sourceCode);
            assertionRecords.add(assertionRecord);
        }

        create.batchStore(assertionRecords).execute();
    }
}
