package teralizer.processing;

import java.util.HashMap;
import java.util.Map;

public class TaskContext {

    // Global context:
    public static final String DSL_CONTEXT = "global.dsl-context";
    public static final String GSON = "global.gson";
    public static final String VELOCITY_ENGINE = "global.velocity-engine";

    // Project-level context:
    public static final String SPOON_LAUNCHER = "spoon-launcher";
    public static final String FOCAL_TYPE_RESOLVER = "focal-type-resolver";

    private final Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        this.data.put(key, value);
    }

    public void put(long projectId, String key, Object value) {
        this.put("projects." + projectId + "." + key, value);
    }

    public void put(long projectId, long testId, String key, Object value) {
        this.put(projectId, "tests." + testId + "." + key, value);
    }

    public void put(long projectId, long testId, long generalizationId, String key, Object value) {
        this.put(projectId, testId, "generalizations." + generalizationId + "." + key, value);
    }

    public <T> T get(String key) {
        return (T) this.data.get(key);
    }

    public <T> T get(long projectId, String key) {
        return this.get("projects." + projectId + "." + key);
    }

    public <T> T get(long projectId, long testId, String key) {
        return this.get(projectId, "tests." + testId + "." + key);
    }

    public <T> T get(long projectId, long testId, long generalizationId, String key) {
        return this.get(projectId, testId, "generalizations." + generalizationId + "." + key);
    }
}
