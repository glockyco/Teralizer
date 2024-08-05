package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestDetectionTask implements Task {

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

        List<TestRecord> testRecords = this.detectTests(create, gson, javaParser);

        for (TestRecord testRecord : testRecords) {
            scheduleTask.accept(new TestFilteringTask(ProcessingStage.TEST_FILTERING, this.projectRecord, testRecord));
        }
    }

    private List<TestRecord> detectTests(DSLContext create, Gson gson, JavaParser javaParser) throws IOException {
        List<TestRecord> testRecords = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get(this.projectRecord.getPath()))) {
            paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(testClassPath -> {
                    try {
                        CompilationUnit testCompilationUnit = javaParser.parse(testClassPath.toFile()).getResult().get();
                        String[] sourceCodeLines = testCompilationUnit.toString().split("\\R");
                        PackageDeclaration testPackageDeclaration = testCompilationUnit.getPackageDeclaration().orElseGet(PackageDeclaration::new);

                        for (ClassOrInterfaceDeclaration testClassDeclaration : testCompilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                            for (MethodDeclaration testMethodDeclaration : testClassDeclaration.findAll(MethodDeclaration.class)) {
                                if (!testMethodDeclaration.isAnnotationPresent("Test")) {
                                    continue;
                                }

                                TestRecord testRecord = create.newRecord(Tables.TEST);
                                testRecord.setProjectId(this.projectRecord.getId());

                                testRecord.setTestClassPath(testClassPath.toString());
                                testRecord.setTestClassPackage(testPackageDeclaration.getNameAsString());
                                testRecord.setTestClassName(testClassDeclaration.getNameAsString());
                                testRecord.setTestMethodName(testMethodDeclaration.getNameAsString());

                                MethodCallExpr testedMethodCall = findTestedMethodCall(testMethodDeclaration);
                                ResolvedMethodDeclaration testedMethodDeclaration = testedMethodCall.resolve();

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
                                testRecord.setTestedClassName(testedMethodDeclaration.getClassName());
                                testRecord.setTestedMethodName(testedMethodDeclaration.getName());
                                testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
                                testRecord.setTestedMethodReturnType(testedMethodDeclaration.getReturnType().describe());

                                String driverClassName = "_" + testRecord.getTestClassName() + "_Driver_" + testRecord.getTestMethodName();
                                Path driverClassPath = testClassPath.getParent().resolve(Paths.get("teralizer", driverClassName + ".java"));

                                testRecord.setDriverClassPath(driverClassPath.toString());
                                testRecord.setDriverClassPackage(testRecord.getTestClassPackage() + ".teralizer");
                                testRecord.setDriverClassName(driverClassName);

                                String testClassQualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName();
                                String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();

                                Path jpfConfigFilePath = Paths.get("data", testMethodQualifiedName + ".jpf");
                                Path inputSpecificationPath = Paths.get("data", testMethodQualifiedName + ".jpf.input.json");
                                Path outputSpecificationPath = Paths.get("data", testMethodQualifiedName + ".jpf.output.json");

                                testRecord.setJpfConfigPath(jpfConfigFilePath.toString());
                                testRecord.setInputSpecificationPath(inputSpecificationPath.toString());
                                testRecord.setOutputSpecificationPath(outputSpecificationPath.toString());

                                testRecord.store();

                                List<MethodCallExpr> assertMethodCalls = testMethodDeclaration.findAll(MethodCallExpr.class, m -> m.getNameAsString().startsWith("assert"));
                                for (MethodCallExpr assertMethodCall : assertMethodCalls) {
                                    String methodName = assertMethodCall.getNameAsString();
                                    String sourceCode = assertMethodCall.toString();

                                    List<MethodParameter> assertArguments = new ArrayList<>();
                                    for (Expression argument : assertMethodCall.getArguments()) {
                                        assert argument instanceof NameExpr;
                                        String paramType = argument.calculateResolvedType().describe();
                                        String paramName = ((NameExpr) argument).getNameAsString();
                                        assertArguments.add(new MethodParameter(paramType, paramName));
                                    }

                                    AssertionRecord assertionRecord = create.newRecord(Tables.ASSERTION);
                                    assertionRecord.setTestId(testRecord.getId());
                                    assertionRecord.setMethodName(methodName);
                                    assertionRecord.setMethodArgumentTypes(gson.toJson(assertArguments));
                                    assertionRecord.setSourceCode(sourceCode);
                                    assertionRecord.store();
                                }

                                testRecords.add(testRecord);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        return testRecords;
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
