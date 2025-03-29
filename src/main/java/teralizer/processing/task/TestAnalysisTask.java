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
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.spoon.analysis.TestAnalysis;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);
        CtClass<?> testClass = spoonLauncher.getFactory().Class().get(this.testRecord.getTestClassQualifiedName());

        List<CtMethod<?>> matchingMethods = testClass.getMethodsByName(this.testRecord.getTestMethodName());

        if (matchingMethods.isEmpty()) {
            // This can happen if the test method was inherited from some other class.
            // The JUnit reports list the test as part of the child class then, but
            // the source code file of the child class does not contain the method.
            throw new RuntimeException("No method matches for test method (might be inherited): " + this.testRecord.getTestMethodQualifiedName());
        }

        Set<String> testAnnotations = new HashSet<>(Arrays.asList("Test", "ParameterizedTest"));

        List<CtMethod<?>> candidateMethods = matchingMethods.stream()
            .filter(method -> method.getAnnotations().stream()
                .anyMatch(a -> testAnnotations.contains(a.getType().getSimpleName())))
            .collect(Collectors.toList());

        if (candidateMethods.size() > 1) {
            throw new RuntimeException("Multiple matches for test method (" + candidateMethods.size() + " total): " + this.testRecord.getTestMethodQualifiedName());
        } else if (candidateMethods.isEmpty()) {
            String annotationsStr = testAnnotations.stream().map(a -> "@" + a).collect(Collectors.joining(", "));
            throw new RuntimeException("No annotation matches for test method (" + annotationsStr +"): " + this.testRecord.getTestMethodQualifiedName());
        }

        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        this.createAssertionRecords(candidateMethods.get(0), create, gson);
    }

    private void createAssertionRecords(CtMethod<?> testMethod, DSLContext create, Gson gson) {
        List<AssertionRecord> records = new ArrayList<>();

        List<CtInvocation<?>> assertionCalls = TestAnalysis.findAllAsserts(testMethod);

        if (assertionCalls.isEmpty()) {
            return;
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
                    record.setTestedMethodAbsolutePath(testedMethod.getPath().toString());
                    record.setTestedMethodRelativePath(testedMethod.getPath().relativePath(testedType).toString());
                }
            }

            record.setIsIncluded(true);

            records.add(record);
        }

        create.batchStore(records).execute();
    }
}
