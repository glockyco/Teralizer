package teralizer.processing.task;

import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Locale;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.processing.ProcessingStage;
import teralizer.spoon.InheritedTestMethodScreens;

public class JunitDataCollectionTaskTest {
    @Example
    void inheritedTestMethodStoresDeclaringParentColumns() throws Exception {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public class AbstractBase {\n"
                + "  @Test public void inherited() { org.junit.Assert.assertTrue(true); }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n"
        );
        TestRecord record = testRecord("smoke.SubjectTest", "inherited");

        update(scenario.factory, record);

        CtMethod<?> method = scenario.parent.getMethodsByName("inherited").get(0);
        Assert.assertEquals("smoke.SubjectTest", record.getTestClassQualifiedName());
        Assert.assertEquals("smoke.AbstractBase.inherited", record.getTestMethodQualifiedName());
        Assert.assertEquals(method.getPath().relativePath(scenario.parent).toString(), record.getTestMethodRelativePath());
        Assert.assertTrue(record.getIsIncluded());
        Assert.assertNull(record.getExclusionInfo());
    }

    @Example
    void genericInheritedTestMethodStoresCleanTypedExclusion() throws Exception {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public abstract class AbstractBase<T> {\n"
                + "  @Test public void inherited() {\n"
                + "    T value = null;\n"
                + "    org.junit.Assert.assertNull(value);\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase<String> {\n"
                + "}\n"
        );
        TestRecord record = testRecord("smoke.SubjectTest", "inherited");

        update(scenario.factory, record);

        Assert.assertFalse(record.getIsIncluded());
        Assert.assertEquals(
            InheritedTestMethodScreens.INHERITED_METHOD_NOT_FLATTENABLE + ":TYPE_VARIABLE",
            record.getExclusionInfo()
        );
    }

    @Example
    void closestAncestorMethodWins() throws Exception {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public class AbstractBase {\n"
                + "  @Test public void inherited() { org.junit.Assert.assertEquals(1, value()); }\n"
                + "  protected int value() { return 1; }\n"
                + "}\n"
                + "class MiddleBase extends AbstractBase {\n"
                + "  @Test public void inherited() { org.junit.Assert.assertEquals(2, value()); }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends MiddleBase {\n"
                + "}\n"
        );
        TestRecord record = testRecord("smoke.SubjectTest", "inherited");

        update(scenario.factory, record);

        CtClass<?> middle = scenario.model.getElements(new NamedElementFilter<>(CtClass.class, "MiddleBase")).get(0);
        Assert.assertEquals("smoke.MiddleBase.inherited", record.getTestMethodQualifiedName());
        Assert.assertEquals(
            middle.getMethodsByName("inherited").get(0).getPath().relativePath(middle).toString(),
            record.getTestMethodRelativePath()
        );
    }

    private static void update(Factory factory, TestRecord record) throws Exception {
        JunitDataCollectionTask task = new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL, project(), record);
        Method update = JunitDataCollectionTask.class.getDeclaredMethod("updateTestRecord", Factory.class, TestRecord.class);
        update.setAccessible(true);
        update.invoke(task, factory, record);
    }

    private static ProjectRecord project() {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        return project;
    }

    private static TestRecord testRecord(String classQualifiedName, String methodName) {
        DSLContext dsl = DSL.using(new MockConnection(new StoreSink()), SQLDialect.POSTGRES);
        TestRecord record = dsl.newRecord(Tables.TEST);
        record.setId(11L);
        record.setProjectId(7L);
        record.setTestClassQualifiedName(classQualifiedName);
        record.setTestMethodQualifiedName(classQualifiedName + "." + methodName);
        record.setTestMethodName(methodName);
        record.setIsIncluded(true);
        return record;
    }

    private static Scenario scenario(String parentSource, String childSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(parentSource, Paths.get(System.getProperty("user.dir"), "AbstractBase.java").toString()));
        launcher.addInputResource(new VirtualFile(childSource, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> parent = model.getElements(new NamedElementFilter<>(CtClass.class, "AbstractBase")).get(0);
        return new Scenario(launcher.getFactory(), model, parent);
    }

    private static final class Scenario {
        private final Factory factory;
        private final CtModel model;
        private final CtClass<?> parent;

        private Scenario(Factory factory, CtModel model, CtClass<?> parent) {
            this.factory = factory;
            this.model = model;
            this.parent = parent;
        }
    }

    private static final class StoreSink implements MockDataProvider {
        @Override
        public MockResult[] execute(MockExecuteContext context) {
            if (context.sql().trim().toLowerCase(Locale.ROOT).startsWith("update")) {
                return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult(Tables.TEST))};
            }
            return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult(Tables.TEST))};
        }
    }
}
