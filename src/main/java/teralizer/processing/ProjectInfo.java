package teralizer.processing;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectInfo {

    private Path rootPath;
    private Path dataPath;
    private Path mainSourcePath;
    private Path testSourcePath;
    private Path mainCompiledPath;
    private Path testCompiledPath;
    private Path testReportsPath;

    public ProjectInfo(String rootPath) {
        this(Paths.get(rootPath), null, null, null, null, null);
    }

    public ProjectInfo(Path rootPath, Path mainSourcePath, Path testSourcePath, Path mainCompiledPath, Path testCompiledPath, Path testReportsPath) {
        this.rootPath = rootPath;
        this.dataPath = Paths.get("data", rootPath.getFileName().toString());
        this.mainSourcePath = mainSourcePath;
        this.testSourcePath = testSourcePath;
        this.mainCompiledPath = mainCompiledPath;
        this.testCompiledPath = testCompiledPath;
        this.testReportsPath = testReportsPath;
    }

    public Path getRootPath() {
        return this.rootPath;
    }

    public void setRootPath(Path rootPath) {
        this.rootPath = rootPath;
    }

    public Path getDataPath() {
        return this.dataPath;
    }

    public void setDataPath(Path dataPath) {
        this.dataPath = dataPath;
    }

    public Path getMainSourcePath() {
        return this.mainSourcePath;
    }

    public void setMainSourcePath(Path mainSourcePath) {
        this.mainSourcePath = mainSourcePath;
    }

    public Path getTestSourcePath() {
        return this.testSourcePath;
    }

    public void setTestSourcePath(Path testSourcePath) {
        this.testSourcePath = testSourcePath;
    }

    public Path getMainCompiledPath() {
        return this.mainCompiledPath;
    }

    public void setMainCompiledPath(Path mainCompiledPath) {
        this.mainCompiledPath = mainCompiledPath;
    }

    public Path getTestCompiledPath() {
        return this.testCompiledPath;
    }

    public void setTestCompiledPath(Path testCompiledPath) {
        this.testCompiledPath = testCompiledPath;
    }

    public Path getTestReportsPath() {
        return this.testReportsPath;
    }

    public void setTestReportsPath(Path testReportsPath) {
        this.testReportsPath = testReportsPath;
    }
}
