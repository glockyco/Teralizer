package teralizer.processing;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.util.Configuration;

public class ProjectIdentityTest {

    @Example
    void noExistingProjectReturnsNullForFreshStart() {
        ProjectStore store = new ProjectStore();

        ProjectRecord record = ProjectIdentity.resolveOrCreate(
            store.dsl(), Paths.get("/tmp/teralizer/no-row"), ConfigIdentity.hash(fullConfig(60, false, true, true)));

        Assert.assertNull(record);
    }

    @Example
    void existingProjectWithMatchingIdentityHashReturnsNewestRecord() {
        Path rootPath = Paths.get("/tmp/teralizer/matching");
        ProjectStore store = new ProjectStore();
        store.add(project(1L, rootPath, fullConfig(60, false, true, true)));
        store.add(project(2L, rootPath, fullConfig(60, true, false, false)));
        String currentHash = ConfigIdentity.hash(fullConfig(60, false, false, true));

        ProjectRecord record = ProjectIdentity.resolveOrCreate(store.dsl(), rootPath, currentHash);

        Assert.assertNotNull(record);
        Assert.assertEquals(Long.valueOf(2L), record.getId());
    }

    @Example
    void runScopedConfigurationRefreshesPhaseTogglesOnAttachedRecord() {
        ProjectRecord record = project(4L, Paths.get("/tmp/teralizer/resume"), fullConfig(60, false, true, false));
        String currentConfig = fullConfig(60, false, false, true);

        ProjectIdentity.applyRunScopedConfiguration(record, false, false, true, currentConfig);

        Assert.assertFalse(record.getUseTestGeneration());
        Assert.assertFalse(record.getUseTestGeneralization());
        Assert.assertTrue(record.getUseTestReduction());
        Assert.assertEquals(currentConfig, record.getConfiguration());
    }

    @Example
    void mismatchedConfigOnSamePathFailsLoud() {
        Path rootPath = Paths.get("/tmp/teralizer/mismatch");
        ProjectStore store = new ProjectStore();
        store.add(project(3L, rootPath, fullConfig(60, false, true, true)));
        String currentHash = ConfigIdentity.hash(fullConfig(120, false, true, true));

        RuntimeException thrown = Assert.assertThrows(RuntimeException.class,
            () -> ProjectIdentity.resolveOrCreate(store.dsl(), rootPath, currentHash));

        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains(rootPath.toString()));
    }

    @Example
    void identityProjectionExcludesOnlyPhaseToggles() {
        String projectedA = ConfigIdentity.renderIdentity(fullConfig(60, false, true, true));
        String projectedB = ConfigIdentity.renderIdentity(fullConfig(60, true, false, false));
        String projectedDifferent = ConfigIdentity.renderIdentity(fullConfig(120, false, true, true));

        Assert.assertEquals(projectedA, projectedB);
        Assert.assertNotEquals(projectedA, projectedDifferent);
        Assert.assertTrue(projectedA, projectedA.contains("original-initial"));
        Assert.assertFalse(projectedA, projectedA.contains("use-test-generation"));
        Assert.assertFalse(projectedA, projectedA.contains("use-test-generalization"));
        Assert.assertFalse(projectedA, projectedA.contains("use-test-reduction"));
    }

    @Example
    void identityProjectionExcludesPitestEnabled() {
        String base = "{data-dir=\"data\",pitest={timeout={original-initial=3600},original={enabled=false}}}";
        String withEnabled = "{data-dir=\"data\",pitest={enabled=true,timeout={original-initial=3600},original={enabled=false}}}";
        String differentTimeout = "{data-dir=\"data\",pitest={timeout={original-initial=120},original={enabled=false}}}";

        String baseProjection = ConfigIdentity.renderIdentity(base);
        String withEnabledProjection = ConfigIdentity.renderIdentity(withEnabled);

        Assert.assertEquals(baseProjection, withEnabledProjection);
        Assert.assertEquals(ConfigIdentity.hash(base), ConfigIdentity.hash(withEnabled));
        Assert.assertTrue(withEnabledProjection, withEnabledProjection.contains("original"));
        Assert.assertNotEquals(baseProjection, ConfigIdentity.renderIdentity(differentTimeout));
    }

    @Example
    void configurationRenderIdentityOmitsPhaseToggles() {
        String rendered = Configuration.renderIdentity();

        Assert.assertTrue(rendered, rendered.contains("data-dir"));
        Assert.assertFalse(rendered, rendered.contains("use-test-generation"));
        Assert.assertFalse(rendered, rendered.contains("use-test-generalization"));
        Assert.assertFalse(rendered, rendered.contains("use-test-reduction"));
    }

    private static ProjectRecord project(long id, Path rootPath, String configuration) {
        ProjectRecord record = new ProjectRecord();
        record.setId(id);
        record.setRootPath(rootPath);
        record.setConfiguration(configuration);
        return record;
    }

    private static String fullConfig(int pitestTimeoutSeconds, boolean useGeneration,
                                     boolean useGeneralization, boolean useReduction) {
        return "{"
            + "data-dir=\"data\","
            + "project={root-path=\"/tmp/teralizer/project\","
            + "use-test-generation=" + useGeneration + ","
            + "use-test-generalization=" + useGeneralization + ","
            + "use-test-reduction=" + useReduction + "},"
            + "pitest={timeout={original-initial=" + pitestTimeoutSeconds + "}}"
            + "}";
    }

    private static final class ProjectStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final List<ProjectRecord> projects = new ArrayList<>();

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        private void add(ProjectRecord record) {
            this.projects.add(record);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (!sql.startsWith("select") || !sql.contains("project")) {
                return new MockResult[] {new MockResult(0, this.records.newResult(Tables.PROJECT))};
            }
            Assert.assertTrue(sql, sql.contains("order by") && sql.contains("id") && sql.contains("desc"));
            Assert.assertTrue(sql, sql.contains("limit"));

            Path rootPath = Paths.get(context.bindings()[0].toString());
            Result<ProjectRecord> result = this.records.newResult(Tables.PROJECT);
            this.projects.stream()
                .filter(project -> rootPath.equals(project.getRootPath()))
                .max(Comparator.comparing(ProjectRecord::getId))
                .ifPresent(result::add);
            return new MockResult[] {new MockResult(result.size(), result)};
        }
    }
}
