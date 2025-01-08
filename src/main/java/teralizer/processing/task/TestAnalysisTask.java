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
import teralizer.domain.MethodArgument;
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

        // Analysis is intentionally performed for ALL tests, not just included ones.
        Result<TestRecord> testRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.projectRecord.getId()))
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

        this.createAssertionRecords(testMethod, create, gson);
    }

    private void createAssertionRecords(CtMethod<?> testMethod, DSLContext create, Gson gson) {
        List<AssertionRecord> records = new ArrayList<>();

        List<CtInvocation<?>> assertionCalls = TestAnalysis.findAllAsserts(testMethod);

        if (assertionCalls.isEmpty()) {
            throw new RuntimeException("No assertions found in " + this.testRecord.getTestMethodQualifiedName() + ".");
        }

        for (CtInvocation<?> assertionCall : assertionCalls) {
            List<MethodArgument> assertionArguments = new ArrayList<>();
            for (CtExpression<?> argument : assertionCall.getArguments()) {
                String argType = argument.getType().getQualifiedName();
                String argValue = argument.toString();
                assertionArguments.add(new MethodArgument(argType, argValue));
            }

            AssertionRecord record = create.newRecord(Tables.ASSERTION);
            record.setProjectId(this.getProjectId());
            record.setTestId(this.getTestId());

            record.setAssertionName(assertionCall.getExecutable().getSimpleName());
            record.setAssertionArguments(gson.toJson(assertionArguments));
            record.setAssertionSourceCode(assertionCall.toString());
            record.setAssertionAbsolutePath(assertionCall.getPath().toString());
            record.setAssertionRelativePath(assertionCall.getPath().relativePath(testMethod).toString());

            CtInvocation<?> testedMethodCall = TestAnalysis.findTestedMethodCall(testMethod, assertionCall).orElse(null);

            if (testedMethodCall != null) {
                List<MethodArgument> methodArguments = new ArrayList<>();
                for (CtExpression<?> argument : testedMethodCall.getArguments()) {
                    String argType = argument.getType().getQualifiedName();
                    String argValue = argument.toString();
                    methodArguments.add(new MethodArgument(argType, argValue));
                }

                record.setTestedMethodName(testedMethodCall.getExecutable().getSimpleName());
                record.setTestedMethodCallArguments(gson.toJson(methodArguments));
                record.setTestedMethodCallSourceCode(testedMethodCall.toString());
                record.setTestedMethodCallAbsolutePath(testedMethodCall.getPath().toString());
                record.setTestedMethodCallRelativePath(testedMethodCall.getPath().relativePath(testMethod).toString());

                CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();
                CtType<?> testedType = testedMethod == null ? null : testedMethod.getDeclaringType();

                if (testedMethod != null && testedType != null) {
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

                    record.setTestedFilePath(testedClassPath);
                    record.setTestedClassQualifiedName(qualifiedClassName);
                    record.setTestedMethodQualifiedName(qualifiedMethodName);
                    record.setTestedPackageName(packageName);
                    record.setTestedClassName(className);
                    record.setTestedMethodName(methodName);
                    record.setTestedMethodParameters(gson.toJson(testedMethodParameters));
                    record.setTestedMethodReturnType(testedMethod.getType().getQualifiedName());
                }
            }

            record.setIsIncluded(true);

            records.add(record);
        }

        create.batchStore(records).execute();
    }
}
