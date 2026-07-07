package teralizer.processing;

import java.nio.file.Path;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;

public final class ProjectIdentity {

    private ProjectIdentity() {
    }

    public static ProjectRecord resolveOrCreate(DSLContext create, Path rootPath, String identityHash) {
        ProjectRecord existing = create.selectFrom(Tables.PROJECT)
            .where(Tables.PROJECT.ROOT_PATH.eq(rootPath))
            .orderBy(Tables.PROJECT.ID.desc())
            .limit(1)
            .fetchOne();
        if (existing == null) {
            return null;
        }

        String storedHash = ConfigIdentity.hash(existing.getConfiguration());
        if (!storedHash.equals(identityHash)) {
            throw new RuntimeException("Refusing to attach to project at " + rootPath
                + ": stored configuration differs from the current run. Use a fresh workspace or reconcile the config.");
        }
        return existing;
    }
}
