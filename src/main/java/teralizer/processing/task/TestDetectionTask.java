package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
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
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingPipeline;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestDetectionTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingPipeline.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public TestDetectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);

        this.detectTests(create, gson, javaParser, reportInfo);
    }

    private void detectTests(DSLContext create, Gson gson, JavaParser javaParser, Consumer<String> reportInfo) throws IOException {
        try (Stream<Path> paths = Files.walk(this.projectRecord.getTestSourcePath())) {
            paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(testClassPath -> {
                    CompilationUnit testCompilationUnit;
                    try {
                        testCompilationUnit = javaParser.parse(testClassPath.toFile()).getResult().get();
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    PackageDeclaration testPackageDeclaration = testCompilationUnit.getPackageDeclaration().orElseGet(PackageDeclaration::new);

                    for (ClassOrInterfaceDeclaration testClassDeclaration : testCompilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (testClassDeclaration.isInterface() || testClassDeclaration.isAbstract()) {
                            continue;
                        }

                        for (MethodDeclaration testMethodDeclaration : testClassDeclaration.findAll(MethodDeclaration.class)) {
                            if (!testMethodDeclaration.isAnnotationPresent("Test")) {
                                continue;
                            }

                            TestRecord testRecord = create.newRecord(Tables.TEST);
                            testRecord.setProjectId(this.projectRecord.getId());
                            testRecord.setTestClassPath(testClassPath.toString());
                            testRecord.setTestClassPackage(testPackageDeclaration.getNameAsString());
                            testRecord.setTestClassName(testClassDeclaration.getNameAsString().replace(".", "$"));
                            testRecord.setTestMethodName(testMethodDeclaration.getNameAsString());
                            this.setTestedMethodData(testRecord, testMethodDeclaration, gson);
                            this.setJpfData(this.projectRecord.getDataPath(), testRecord);
                            testRecord.setIsIncluded(true);
                            testRecord.store();

                            this.createAssertionRecords(testRecord, testMethodDeclaration, create, gson, reportInfo);
                        }
                    }
                });
        }
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
            assertionRecord.setTestId(testRecord.getId());
            assertionRecord.setMethodName(methodName);
            assertionRecord.setMethodArgumentTypes(gson.toJson(assertArguments));
            assertionRecord.setSourceCode(sourceCode);
            assertionRecords.add(assertionRecord);
        }

        create.batchStore(assertionRecords).execute();
    }

    private void setTestedMethodData(TestRecord testRecord, MethodDeclaration testMethodDeclaration, Gson gson) {
        MethodCallExpr testedMethodCall = findTestedMethodCall(testMethodDeclaration);

        if (testedMethodCall == null) {
            return;
        }

        ResolvedMethodDeclaration testedMethodDeclaration;
        try {
            testedMethodDeclaration = testedMethodCall.resolve();
        } catch (UnsolvedSymbolException | UnsupportedOperationException  e) {
            return;
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

        testRecord.setTestedClassPath(testedClassPath);
        testRecord.setTestedClassPackage(testedMethodDeclaration.getPackageName());
        testRecord.setTestedClassName(testedMethodDeclaration.getClassName().replace(".", "$"));
        testRecord.setTestedMethodName(testedMethodDeclaration.getName());
        testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
        testRecord.setTestedMethodReturnType(testedMethodDeclaration.getReturnType().describe());
    }

    private void setJpfData(Path projectDataPath, TestRecord testRecord) {
        String driverClassName = "_" + testRecord.getTestClassName() + "_Driver_" + testRecord.getTestMethodName();
        Path driverClassPath = Paths.get(testRecord.getTestClassPath()).getParent().resolve(TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated/" + driverClassName + ".java");

        testRecord.setDriverClassPath(driverClassPath.toString());
        testRecord.setDriverClassPackage(testRecord.getTestClassPackage() + "." + TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated");
        testRecord.setDriverClassName(driverClassName);

        String testClassQualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();

        Path jpfConfigFilePath = projectDataPath.resolve(testMethodQualifiedName + ".jpf");
        Path inputSpecificationPath = projectDataPath.resolve(testMethodQualifiedName + ".jpf.input.json");
        Path outputSpecificationPath = projectDataPath.resolve(testMethodQualifiedName + ".jpf.output.json");

        testRecord.setJpfConfigPath(jpfConfigFilePath.toString());
        testRecord.setInputSpecificationPath(inputSpecificationPath.toString());
        testRecord.setOutputSpecificationPath(outputSpecificationPath.toString());
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

    public static MethodCallExpr findAssertEqualsCall(MethodDeclaration testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of generalizable assertEquals calls.
        //   What we want is the assertEquals that checks the output of the tested method.
        //   To (more) reliably identify this, we should probably do, at least, some control flow analysis.
        //   Check whether https://github.com/joernio/joern can be useful.

        List<MethodCallExpr> assertEqualsCalls = testMethodDeclaration.findAll(MethodCallExpr.class, m -> m.getNameAsString().equals("assertEquals"));
        assert assertEqualsCalls.size() == 1;
        return assertEqualsCalls.get(0);
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
        return null;
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        return "TestDetectionTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestDetectionTask)) return false;
        TestDetectionTask that = (TestDetectionTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
