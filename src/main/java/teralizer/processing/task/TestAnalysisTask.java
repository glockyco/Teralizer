package teralizer.processing.task;

import com.google.gson.Gson;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathException;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.MethodUnderTestResolver;
import teralizer.spoon.analysis.MutResolution;
import teralizer.spoon.analysis.TestAnalysis;

public class TestAnalysisTask extends AbstractTask {
    static final String UNRESOLVED_TYPE_NAME = "<unresolved>";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnalysisTask.class);


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
        String testMethodRelativePath = this.testRecord.getTestMethodRelativePath();
        if (testMethodRelativePath == null) {
            return;
        }

        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);
        Factory  factory = spoonLauncher.getFactory();

        CtClass<?> testClass = factory.Class().get(this.testRecord.getTestClassQualifiedName());

        CtPathStringBuilder pathBuilder = new CtPathStringBuilder();
        CtPath testMethodPath = pathBuilder.fromString(testMethodRelativePath);
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(testClass).get(0);

        this.createAssertionRecords(testMethod, create, gson);
    }

    void createAssertionRecords(CtMethod<?> testMethod, DSLContext create, Gson gson) {

        List<CtInvocation<?>> assertionCalls = TestAnalysis.findAllAsserts(testMethod);

        if (assertionCalls.isEmpty()) {
            return;
        }

        for (CtInvocation<?> assertionCall : assertionCalls) {
            try {
                List<MethodArgument> assertionArguments = new ArrayList<>();
                for (CtExpression<?> argument : assertionCall.getArguments()) {
                    String argType = typeNameOf(argument.getType());
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

                MutResolution resolution = MethodUnderTestResolver.resolve(testMethod, assertionCall);
                CtInvocation<?> testedMethodCall = resolution.getPick();
                if (testedMethodCall != null) {
                    String callAbsolutePath = pathOrNull(testedMethodCall::getPath);
                    String callRelativePath = pathOrNull(() -> testedMethodCall.getPath().relativePath(testMethod));
                    // Guard, not cast: a pick outside the source model (or a non-method executable)
                    // stays characterization-only and must not abort the whole test's analysis.
                    CtMethod<?> testedMethod = testedMethodCall.getExecutable().getDeclaration() instanceof CtMethod<?>
                        ? (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration()
                        : null;
                    CtType<?> testedType = testedMethod == null ? null : testedMethod.getDeclaringType();
                    String methodAbsolutePath = testedMethod == null ? null : pathOrNull(testedMethod::getPath);
                    String methodRelativePath = testedMethod == null || testedType == null
                        ? null
                        : pathOrNull(() -> testedMethod.getPath().relativePath(testedType));

                    List<MethodArgument> methodArguments = new ArrayList<>();
                    List<GeneralizableInput> generalizableInputs = null;
                    GeneralizationRecipe generalizationRecipe = null;
                    if (callAbsolutePath != null && callRelativePath != null && testedMethod != null) {
                        generalizableInputs = GeneralizableInput.derive(testedMethod, testedMethodCall);
                        generalizationRecipe = GeneralizationRecipe.from(testedMethod, testedMethodCall, generalizableInputs);
                    }
                    if (generalizableInputs == null) {
                        for (CtExpression<?> argument : testedMethodCall.getArguments()) {
                            String argType = typeNameOf(argument.getType());
                            String argValue = argument.toString();
                            methodArguments.add(new MethodArgument(argType, argValue));
                        }
                    } else {
                        for (GeneralizableInput input : generalizableInputs) {
                            methodArguments.add(input.toMethodArgument());
                        }
                    }
                    record.setTestedMethodName(testedMethodCall.getExecutable().getSimpleName());
                    record.setTestedMethodCallArguments(gson.toJson(methodArguments));
                    record.setTestedMethodCallSourceCode(testedMethodCall.toString());

                    if (callAbsolutePath != null && callRelativePath != null) {
                        record.setTestedMethodCallAbsolutePath(callAbsolutePath);
                        record.setTestedMethodCallRelativePath(callRelativePath);
                    }

                    /*
                     * Invariant: declaration-dependent tested_* columns are all-or-nothing
                     * with the call-path columns. Downstream instrumentation rematerializes
                     * both the picked call and declaration from CtPath strings, so an
                     * unpathable call or declaration must remain MissingValueFilter-typed
                     * instead of looking complete and later NPEing in JPF instrumentation.
                     */
                    if (callAbsolutePath != null
                        && callRelativePath != null
                        && methodAbsolutePath != null
                        && methodRelativePath != null
                        && testedMethod != null
                        && testedType != null
                        && testedType.getPackage() != null
                        && testedType.getPosition() != null
                        && testedType.getPosition().isValidPosition()
                        && testedType.getPosition().getFile() != null) {
                        SourcePosition position = testedType.getPosition();
                        String testedClassPath = Paths.get(System.getProperty("user.dir")).relativize(position.getFile().toPath()).toString();

                        String packageName = testedType.getPackage().toString();
                        String className = testedType.getSimpleName();
                        String methodName = testedMethod.getSimpleName();

                        String qualifiedClassName = testedType.getQualifiedName();
                        String qualifiedMethodName = qualifiedClassName + "." + methodName;

                        List<MethodParameter> testedMethodParameters = new ArrayList<>();
                        if (generalizableInputs == null) {
                            for (CtParameter<?> param : testedMethod.getParameters()) {
                                String paramType = typeNameOf(param.getType());
                                String paramName = param.getSimpleName();
                                testedMethodParameters.add(new MethodParameter(paramType, paramName));
                            }
                        } else {
                            for (GeneralizableInput input : generalizableInputs) {
                                testedMethodParameters.add(input.toMethodParameter());
                            }
                        }

                        record.setTestedFilePath(testedClassPath);
                        record.setTestedClassQualifiedName(qualifiedClassName);
                        record.setTestedMethodQualifiedName(qualifiedMethodName);
                        record.setTestedPackageName(packageName);
                        record.setTestedClassName(className);
                        record.setTestedMethodName(methodName);
                        record.setTestedMethodParameters(gson.toJson(testedMethodParameters));
                        record.setTestedMethodReturnType(typeNameOf(testedMethod.getType()));
                        record.setTestedMethodAbsolutePath(methodAbsolutePath);
                        record.setTestedMethodRelativePath(methodRelativePath);
                        record.setGeneralizationRecipe(generalizationRecipe.toJson(gson));
                    }
                }

                record.setIsIncluded(true);
                record.store();

                MutResolutionObservationRecord observation = create.newRecord(Tables.MUT_RESOLUTION_OBSERVATION);
                MutResolutionObservationMapper.map(resolution, this.getProjectId(), this.getTestId(), record.getId(), gson, observation);
                observation.store();
            } catch (RuntimeException e) {
                /*
                 * Last-resort guard only: the known crash classes above are handled at
                 * their sources. This bounds any still-unknown assertion shape to one
                 * skipped assertion instead of losing the rest of the test or project.
                 */
                LOGGER.atWarn()
                    .setCause(e)
                    .log("Skipping assertion analysis after unexpected failure for assertion: {}", sourceSnippet(assertionCall));
            }
        }
    }


    static String pathOrNull(Supplier<CtPath> pathSupplier) {
        try {
            CtPath path = pathSupplier.get();
            return path == null ? null : path.toString();
        } catch (CtPathException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String sourceSnippet(CtInvocation<?> assertionCall) {
        String source;
        try {
            source = assertionCall == null ? "<null assertion>" : assertionCall.toString();
        } catch (RuntimeException e) {
            source = "<unprintable assertion>";
        }
        String compact = source.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 117) + "...";
    }
    /*
     * Spoon no-classpath models can leave expression type references unresolved. A
     * typed sentinel keeps the serialized argument shape explicit without pretending
     * that an unknown type is usable for downstream generation.
     */
    static String typeNameOf(CtTypeReference<?> type) {
        return type == null ? UNRESOLVED_TYPE_NAME : type.getQualifiedName();
    }
}
