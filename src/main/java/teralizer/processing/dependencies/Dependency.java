package teralizer.processing.dependencies;

import java.util.Objects;

public class Dependency {

    public final String groupId;
    public final String artifactId;
    public final String version;

    public Dependency(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @Override
    public String toString() {
        return this.groupId + ":" + this.artifactId + ":" + this.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency)) return false;
        Dependency that = (Dependency) o;
        // Only the groupId and the artifactId must match. We do not care about the version.
        return Objects.equals(this.groupId, that.groupId) && Objects.equals(this.artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId);
    }
}

