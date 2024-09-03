package teralizer.processing.task;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

public class ProjectDownloadTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectDownloadTask.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public ProjectDownloadTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.projectRecord.getRootPath() == null) {
            throw new RuntimeException("Cannot download project. Project root path is null.");
        } else if (!this.projectRecord.getRootPath().toString().endsWith(".git")) {
            LOGGER.atInfo().log("Nothing to download for project {}. Project root path is not a Git repository URL.", this.projectRecord.getRootPath());
            this.scheduleNextTask(scheduleTask);
            return;
        }

        Path repositoryUrl = this.projectRecord.getRootPath();
        String repositoryName = FilenameUtils.getBaseName(repositoryUrl.toString());
        Path projectRootPath = Paths.get("projects", repositoryName);

        this.projectRecord.setRootPath(projectRootPath);
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
        scheduleTask.accept(new ProjectSetupTask(ProcessingStage.PROJECT_SETUP, this.projectRecord));
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
        return "ProjectDownloadTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectDownloadTask)) return false;
        ProjectDownloadTask that = (ProjectDownloadTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
