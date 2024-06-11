package teralizer.tasks;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestDetectionTask {

    private final Task task = Task.TEST_DETECTION;

    private final JavaParser javaParser;
    private final Gson gson;

    public TestDetectionTask(JavaParser javaParser, Gson gson) {
        this.javaParser = javaParser;
        this.gson = gson;
    }

    public List<TestRecord> run(DSLContext create, ProjectRecord projectRecord) throws IOException {
        return this.detectTests(create, projectRecord);
    }

    private List<TestRecord> detectTests(DSLContext create, ProjectRecord projectRecord) throws IOException {
        List<TestRecord> testRecords = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get(projectRecord.getPath()))) {
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
                                testRecord.setProjectId(projectRecord.getId());

                                testRecord.setTestClassPath(testClassPath.toAbsolutePath().toString());
                                testRecord.setTestClassPackage(testPackageDeclaration.getNameAsString());
                                testRecord.setTestClassName(testClassDeclaration.getNameAsString());
                                testRecord.setTestMethodName(testMethodDeclaration.getNameAsString());

                                MethodCallExpr testedMethodCall = findTestedMethodCall(testMethodDeclaration);
                                MethodDeclaration testedMethodDeclaration = testedMethodCall.resolve().toAst(MethodDeclaration.class).get();

                                ClassOrInterfaceDeclaration testedClassDeclaration = testedMethodDeclaration.findAncestor(ClassOrInterfaceDeclaration.class).get();
                                CompilationUnit testedClassCompilationUnit = testedClassDeclaration.findCompilationUnit().get();
                                PackageDeclaration testedPackageDeclaration = testedClassCompilationUnit.getPackageDeclaration().orElseGet(PackageDeclaration::new);
                                Path testedClassPath = testedClassCompilationUnit.getStorage().get().getPath();

                                testRecord.setTestedClassPath(testedClassPath.toAbsolutePath().toString());
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

                                testRecord.setDriverClassPath(driverClassPath.toAbsolutePath().toString());
                                testRecord.setDriverClassPackage(testRecord.getTestClassPackage() + ".teralizer");
                                testRecord.setDriverClassName(driverClassName);

                                String testClassQualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName();
                                String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();

                                Path jpfConfigFilePath = Paths.get("data", testMethodQualifiedName + ".jpf");
                                Path inputSpecificationPath = Paths.get("data", testMethodQualifiedName + ".jpf.input.json");
                                Path outputSpecificationPath = Paths.get("data", testMethodQualifiedName + ".jpf.output.json");

                                testRecord.setJpfConfigPath(jpfConfigFilePath.toAbsolutePath().toString());
                                testRecord.setInputSpecificationPath(inputSpecificationPath.toAbsolutePath().toString());
                                testRecord.setOutputSpecificationPath(outputSpecificationPath.toAbsolutePath().toString());

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
            .where(Tables.TEST.PROJECT_ID.equal(projectRecord.getId()))
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
}
