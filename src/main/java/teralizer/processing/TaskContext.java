package teralizer.processing;

import com.github.javaparser.JavaParser;
import com.google.gson.Gson;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;

import java.util.HashMap;
import java.util.Map;

public class TaskContext {

    public static final String DSL_CONTEXT = "util.dsl-context";
    public static final String GSON = "util.gson";
    public static final String JAVA_PARSER = "util.java-parser";
    public static final String VELOCITY_ENGINE = "util.velocity-engine";

    private final Map<String, Object> data = new HashMap<>();

    public TaskContext(DSLContext create, Gson gson, JavaParser javaParser, VelocityEngine velocityEngine) {
        this.data.put(DSL_CONTEXT, create);
        this.data.put(GSON, gson);
        this.data.put(JAVA_PARSER, javaParser);
        this.data.put(VELOCITY_ENGINE, velocityEngine);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key) {
        return (T) data.get(key);
    }
}
