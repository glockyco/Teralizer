package teralizer.processing;

import java.util.HashMap;
import java.util.Map;

public class TaskContext {

    // Global context:
    public static final String DSL_CONTEXT = "global.dsl-context";
    public static final String GSON = "global.gson";
    public static final String VELOCITY_ENGINE = "global.velocity-engine";

    // Project-level context:
    public static final String JAVA_PARSER = "java-parser";

    private final Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        this.data.put(key, value);
    }

    public void put(int projectId, String key, Object value) {
        this.put("projects." + projectId + "." + key, value);
    }

    public void put(int projectId, int testId, String key, Object value) {
        this.put(projectId, "tests." + testId + "." + key, value);
    }

    public void put(int projectId, int testId, int generalizationId, String key, Object value) {
        this.put(projectId, testId, "generalizations." + generalizationId + "." + key, value);
    }

    public <T> T get(String key) {
        return (T) this.data.get(key);
    }

    public <T> T get(int projectId, String key) {
        return this.get("projects." + projectId + "." + key);
    }

    public <T> T get(int projectId, int testId, String key) {
        return this.get(projectId, "tests." + testId + "." + key);
    }

    public <T> T get(int projectId, int testId, int generalizationId, String key) {
        return this.get(projectId, testId, "generalizations." + generalizationId + "." + key);
    }
}
