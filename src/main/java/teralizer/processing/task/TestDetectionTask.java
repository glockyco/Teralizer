package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
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
import java.util.stream.Collectors;
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
            scheduleTask.accept(new JpfInstrumentationTask(ProcessingStage.JPF_INSTRUMENTATION, this.projectRecord, testRecord));
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
                                MethodDeclaration testedMethodDeclaration = testedMethodCall.resolve().toAst(MethodDeclaration.class).get();

                                ClassOrInterfaceDeclaration testedClassDeclaration = testedMethodDeclaration.findAncestor(ClassOrInterfaceDeclaration.class).get();
                                CompilationUnit testedClassCompilationUnit = testedClassDeclaration.findCompilationUnit().get();
                                PackageDeclaration testedPackageDeclaration = testedClassCompilationUnit.getPackageDeclaration().orElseGet(PackageDeclaration::new);
                                Path testedClassPath = Paths.get(System.getProperty("user.dir")).relativize(testedClassCompilationUnit.getStorage().get().getPath());

                                testRecord.setTestedClassPath(testedClassPath.toString());
                                testRecord.setTestedClassPackage(testedPackageDeclaration.getNameAsString());
                                testRecord.setTestedClassName(testedClassDeclaration.getNameAsString());

                                List<MethodParameter> testedMethodParameters = testedMethodDeclaration.getParameters().stream()
                                    .map(p -> new MethodParameter(p.getTypeAsString(), p.getNameAsString()))
                                    .collect(Collectors.toList());

                                testRecord.setTestedMethodName(testedMethodDeclaration.getNameAsString());
                                testRecord.setTestedMethodParamTypes(gson.toJson(testedMethodParameters));
                                testRecord.setTestedMethodReturnType(testedMethodDeclaration.getTypeAsString());

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

                                testRecords.add(testRecord);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        create.batchStore(testRecords).execute();

        // Read the inserted records to get their IDs.
        return create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.equal(this.projectRecord.getId()))
            .fetch();
    }

    public static MethodCallExpr findTestedMethodCall(MethodDeclaration testMethodDeclaration) {
        MethodCallExpr testedMethodCall = null;

        // @TODO: Use more sophisticated detection of tested method.

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
