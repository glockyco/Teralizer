package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingPipeline;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestAnalysisTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingPipeline.class);

    public TestAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);

        this.analyzeTests(create, gson, javaParser, reportInfo);
    }

    private void analyzeTests(DSLContext create, Gson gson, JavaParser javaParser, Consumer<String> reportInfo) {
        Map<String, List<TestRecord>> allTestRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.getProjectId()))
            .fetch().stream().collect(Collectors.groupingBy(TestRecord::getTestFilePath));

        if (allTestRecords.isEmpty()) {
            throw new RuntimeException("Failed to identify any tests to analyze.");
        }

        for (Map.Entry<String, List<TestRecord>> entry : allTestRecords.entrySet()) {
            Path testFilePath = Paths.get(entry.getKey());
            List<TestRecord> testRecords = entry.getValue();
            String testClassName = testRecords.get(0).getTestClassName();

            CompilationUnit testCompilationUnit;
            try {
                testCompilationUnit = javaParser.parse(testFilePath.toFile()).getResult().get();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            ClassOrInterfaceDeclaration testClassDeclaration = testCompilationUnit.getClassByName(testClassName).get();

            for (TestRecord testRecord : testRecords) {
                MethodDeclaration testMethodDeclaration = testClassDeclaration.getMethodsByName(testRecord.getTestMethodName()).get(0);
                if (!testMethodDeclaration.isAnnotationPresent("Test")) {
                    testRecord.setIsIncluded(false);
                    testRecord.setExclusionInfo("Excluded by " + this + ". Test method has no @Test annotation.");
                    testRecord.store();
                    continue;
                }

                MethodCallExpr testedMethodCall = findTestedMethodCall(testMethodDeclaration);

                if (testedMethodCall == null) {
                    continue;
                }

                ResolvedMethodDeclaration testedMethodDeclaration;
                try {
                    testedMethodDeclaration = testedMethodCall.resolve();
                } catch (UnsolvedSymbolException | UnsupportedOperationException  e) {
                    continue;
                }

                // @TODO: Tested "methods" can be constructors.
                // @TODO: Tested "methods" can be pairs (e.g., encrypt + decrypt).
                // @TODO: Tested "methods" can be sequences (e.g., multiple calls that repeatedly modify object state).

                String testedClassPath = testedMethodDeclaration.toAst()
                    .flatMap(m -> m.findCompilationUnit()
                    .flatMap(cu -> cu.getStorage()
                    .map(s -> Paths.get(System.getProperty("user.dir")).relativize(s.getPath()).toString())))
                    .orElse(null);

                List<MethodParameter> testedMethodParameters = new ArrayList<>();
                for (int i = 0; i < testedMethodDeclaration.getNumberOfParams(); i++) {
                    String paramType = testedMethodDeclaration.getParam(i).describeType();
                    String paramName = testedMethodDeclaration.getParam(i).getName();
                    testedMethodParameters.add(new MethodParameter(paramType, paramName));
                }

                String packageName = testedMethodDeclaration.getPackageName();
                String className = testedMethodDeclaration.getClassName().replace(".", "$");
                String methodName = testedMethodDeclaration.getName();

                String qualifiedClassName = (packageName.isEmpty() ? "" : (packageName + ".")) + className;
                String qualifiedMethodName = qualifiedClassName + "." + methodName;

                testRecord.setTestedFilePath(testedClassPath);
                testRecord.setTestedClassQualifiedName(qualifiedClassName);
                testRecord.setTestedMethodQualifiedName(qualifiedMethodName);
                testRecord.setTestedPackageName(packageName);
                testRecord.setTestedClassName(className);
                testRecord.setTestedMethodName(methodName);
                testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
                testRecord.setTestedMethodReturnType(testedMethodDeclaration.getReturnType().describe());

                testRecord.store();

                this.createAssertionRecords(testRecord, testMethodDeclaration, create, gson, reportInfo);
            }
        }
    }

    public static MethodCallExpr findTestedMethodCall(MethodDeclaration testMethodDeclaration) {
        MethodCallExpr testedMethodCall = null;

        // @TODO: Use more sophisticated detection of tested method.
        //   - Check whether https://github.com/joernio/joern can be useful.

        List<MethodCallExpr> methodCalls = testMethodDeclaration.findAll(MethodCallExpr.class);
        for (MethodCallExpr methodCall : methodCalls) {
            if (methodCall.getNameAsString().startsWith("assert")) {
                break;
            }
            testedMethodCall = methodCall;
        }

        assert testedMethodCall != null;
        assert testedMethodCall.getScope().isPresent();

        return testedMethodCall;
    }

    private void createAssertionRecords(TestRecord testRecord, MethodDeclaration testMethodDeclaration, DSLContext create, Gson gson, Consumer<String> reportInfo) {
        List<AssertionRecord> assertionRecords = new ArrayList<>();

        List<MethodCallExpr> assertMethodCalls = testMethodDeclaration.findAll(MethodCallExpr.class, m -> m.getNameAsString().startsWith("assert"));
        for (MethodCallExpr assertMethodCall : assertMethodCalls) {
            String methodName = assertMethodCall.getNameAsString();
            String sourceCode = assertMethodCall.toString();

            List<MethodParameter> assertArguments = new ArrayList<>();
            for (Expression argument : assertMethodCall.getArguments()) {
                String paramType;
                try {
                    paramType = argument.calculateResolvedType().describe();
                } catch (/*UnsolvedSymbolException | */RuntimeException e) {
                    StringWriter stringWriter = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(stringWriter);
                    e.printStackTrace(printWriter);

                    paramType = null;
                    reportInfo.accept("Could not resolve type of argument " + argument + ". \n\n" + stringWriter);

                    LOGGER.atDebug().log(e.getMessage(), e);
                }
                String paramName = argument.toString();
                assertArguments.add(new MethodParameter(paramType, paramName));
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

    public static MethodCallExpr findAssertEqualsCall(MethodDeclaration testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of generalizable assertEquals calls.
        //   What we want is the assertEquals that checks the output of the tested method.
        //   To (more) reliably identify this, we should probably do, at least, some control flow analysis.
        //   Check whether https://github.com/joernio/joern can be useful.

        List<MethodCallExpr> assertEqualsCalls = testMethodDeclaration.findAll(MethodCallExpr.class, m -> m.getNameAsString().equals("assertEquals"));
        assert assertEqualsCalls.size() == 1;
        return assertEqualsCalls.get(0);
    }
}
