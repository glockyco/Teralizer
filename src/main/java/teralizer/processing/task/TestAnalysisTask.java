package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestAnalysisTask extends AbstractTask {

    public TestAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);

        this.analyzeTests(create, gson, spoonLauncher);
    }

    private void analyzeTests(DSLContext create, Gson gson, Launcher spoonLauncher) {
        Map<String, List<TestRecord>> allTestRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.getProjectId()))
            .fetch().stream().collect(Collectors.groupingBy(TestRecord::getTestClassQualifiedName));

        if (allTestRecords.isEmpty()) {
            throw new RuntimeException("Failed to identify any tests to analyze.");
        }

        CtModel ctModel = spoonLauncher.getModel();
        ctModel.processWith(new AbstractProcessor<CtClass<?>>() {
            @Override
            public void process(CtClass<?> ctClass) {
                if (!allTestRecords.containsKey(ctClass.getQualifiedName())) {
                    return;
                }
                List<TestRecord> testRecords = allTestRecords.get(ctClass.getQualifiedName());
                for (TestRecord testRecord : testRecords) {
                    List<CtMethod<?>> testMethodDeclarations = ctClass.getMethodsByName(testRecord.getTestMethodName());
                    if (testMethodDeclarations.isEmpty()) {
                        // This can happen if the test method was inherited from some other class.
                        // The JUnit reports list the test as part of the child class then, but
                        // the source code file of the child class does not contain the method.
                        testRecord.setIsIncluded(false);
                        testRecord.setExclusionInfo("Excluded by " + this + ". Method " + TestAnalysisTask.this.testRecord.getTestMethodName() + " not found in " + TestAnalysisTask.this.testRecord.getTestClassQualifiedName() + " (might be inherited).");
                        testRecord.store();
                        continue;
                    }

                    if (testMethodDeclarations.size() > 1) {
                        // This should never happen because there can only be multiple methods
                        // with the same name if they have different signatures. However, all
                        // @Test methods should have the same signature (no inputs, void output).
                        testRecord.setIsIncluded(false);
                        testRecord.setExclusionInfo("Excluded by " + this + ". Multiple methods with name " + TestAnalysisTask.this.testRecord.getTestMethodName() + " found in " + TestAnalysisTask.this.testRecord.getTestClassQualifiedName() + ".");
                        testRecord.store();
                        continue;
                    }

                    CtMethod<?> testMethodDeclaration = testMethodDeclarations.get(0);
                    if (testMethodDeclaration.getAnnotations().stream().noneMatch(a -> a.getType().getSimpleName().equals("Test"))) {
                        testRecord.setIsIncluded(false);
                        testRecord.setExclusionInfo("Excluded by " + this + ". Test method has no @Test annotation.");
                        testRecord.store();
                        continue;
                    }

                    CtInvocation<?> testedMethodCall = findTestedMethodCall(testMethodDeclaration);

                    if (testedMethodCall == null) {
                        continue;
                    }

                    CtMethod<?> testedMethodDeclaration = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();
                    CtType<?> testedType = testedMethodCall.getExecutable().getDeclaringType().getTypeDeclaration();

                    if (testedMethodDeclaration == null || testedType == null) {
                        continue;
                    }

                    SourcePosition position = testedType.getPosition();
                    String testedClassPath = Paths.get(System.getProperty("user.dir")).relativize(position.getFile().toPath()).toString();

                    String packageName = testedType.getPackage().toString();
                    String className = testedType.getSimpleName();
                    String methodName = testedMethodDeclaration.getSimpleName();

                    String qualifiedClassName = testedType.getQualifiedName();
                    String qualifiedMethodName = qualifiedClassName + "." + methodName;

                    List<MethodParameter> testedMethodParameters = new ArrayList<>();
                    for (CtParameter<?> param : testedMethodDeclaration.getParameters()) {
                        String paramType = param.getType().getQualifiedName();
                        String paramName = param.getSimpleName();
                        testedMethodParameters.add(new MethodParameter(paramType, paramName));
                    }

                    testRecord.setTestedFilePath(testedClassPath);
                    testRecord.setTestedClassQualifiedName(qualifiedClassName);
                    testRecord.setTestedMethodQualifiedName(qualifiedMethodName);
                    testRecord.setTestedPackageName(packageName);
                    testRecord.setTestedClassName(className);
                    testRecord.setTestedMethodName(methodName);
                    testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
                    testRecord.setTestedMethodReturnType(testedMethodDeclaration.getType().getQualifiedName());

                    testRecord.store();

                    TestAnalysisTask.createAssertionRecords(testRecord, testMethodDeclaration, create, gson);
                }
            }
        });
    }

    public static CtInvocation<?> findTestedMethodCall(CtMethod<?> testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of tested method.

        CtInvocation<?> testedMethodCall = null;

        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> methodCall : methodCalls) {
            if (methodCall.getExecutable().getSimpleName().startsWith("assert")) {
                break;
            }
            testedMethodCall = methodCall;
        }

        assert testedMethodCall != null;

        return testedMethodCall;
    }

    public static CtInvocation<?> findAssertEqualsCall(CtMethod<?> testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of generalizable assertEquals calls.

        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        List<CtInvocation<?>> assertEqualsCalls = methodCalls.stream().filter(m -> m.getExecutable().getSimpleName().equals("assertEquals")).collect(Collectors.toList());

        assert assertEqualsCalls.size() == 1;
        return assertEqualsCalls.get(0);
    }

    public static void createAssertionRecords(TestRecord testRecord, CtMethod<?> testMethodDeclaration, DSLContext create, Gson gson) {
        List<AssertionRecord> assertionRecords = new ArrayList<>();

        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        List<CtInvocation<?>> assertMethodCalls = methodCalls.stream().filter(m -> m.getExecutable().getSimpleName().startsWith("assert")).collect(Collectors.toList());

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
