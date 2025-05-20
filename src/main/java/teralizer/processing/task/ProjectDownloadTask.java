package teralizer.processing.task;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class ProjectDownloadTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectDownloadTask.class);

    public ProjectDownloadTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.projectRecord.getRootPath() == null) {
            throw new RuntimeException("Cannot download project. Project root path is null.");
        } else if (!this.projectRecord.getRootPath().toString().endsWith(".git")) {
            LOGGER.atInfo().log("Nothing to download for project {}. Project root path is not a Git repository URL.", this.projectRecord.getRootPath());
            this.scheduleNextTask(scheduleTask);
            return;
        }

        Path repositoryUrl = this.projectRecord.getRootPath();
        String projectName = this.sanitizeRepositoryUrl(repositoryUrl.toString());
        Path projectRootPath = Paths.get("projects", projectName);
        Path projectDataPath = Paths.get("data", projectName);

        this.projectRecord.setRootPath(projectRootPath);
        this.projectRecord.setDataPath(projectDataPath);
        this.projectRecord.store();

        if (projectRootPath.toFile().exists()) {
            LOGGER.atInfo().log("Nothing to download for project {}. Project is already on disk.", this.projectRecord.getRootPath());
            this.scheduleNextTask(scheduleTask);
            return;
        }

        SshdSessionFactory sshSessionFactory = new SshdSessionFactory();

        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setURI(repositoryUrl.toString());
        cloneCommand.setDirectory(projectRootPath.toAbsolutePath().toFile());
        cloneCommand.setTransportConfigCallback(transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        });

        cloneCommand.call().close();

        this.scheduleNextTask(scheduleTask);
    }

    public void scheduleNextTask(Consumer<Task> scheduleTask) {
        scheduleTask.accept(new ProjectSetupTask(ProcessingStage.SETUP_PROJECT, this.projectRecord));
    }

    private String sanitizeRepositoryUrl(String url) {
        // Remove protocol prefixes
        String cleaned = url
            .replaceFirst("^https?://", "")
            .replaceFirst("^git@", "");

        // Replace ':' (from SSH URLs) with '/'
        cleaned = cleaned.replace(':', '/');

        // Remove .git suffix if present
        cleaned = cleaned.replaceAll("\\.git$", "");

        // Replace all non-alphanumeric (and non-underscore, non-hyphen) characters with '_'
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9_\\-]", "_");

        return cleaned;
    }
}
