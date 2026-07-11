package teralizer.tools.cutpvc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime sink for intercepted method arguments.
 *
 * Loaded into the bootstrap class loader (see {@link CaptureAgent}) so that
 * advice code inlined into instrumented application classes can reference it
 * regardless of their class loader. One TSV file per intercepted method, one
 * line per invocation, in the exact jqwik value-log format that
 * {@code teralizer.jarvis_scoreboard.parse_jqwik_value_log} reads:
 * tab-separated {@code p<i>=<escaped value>} cells in parameter order.
 */
public final class CaptureLog {

    /** Output directory for the per-method TSV files. Set by the agent premain. */
    public static volatile String outDir;

    /**
     * Fully qualified test class whose direct calls are recorded. Calls whose
     * immediate caller is a different class (e.g. library-internal calls to the
     * same method) are skipped so the measurement matches the values of the
     * test traces, not transitive usage. Empty records every call.
     */
    public static volatile String callerClass = "";

    private static final Map<String, Writer> WRITERS = new ConcurrentHashMap<String, Writer>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                for (Writer writer : WRITERS.values()) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                        // Best effort: lines are flushed per write.
                    }
                }
            }
        }));
    }

    private CaptureLog() {}

    public static void record(String typeName, String methodName, Object[] args) {
        try {
            if (callerClass != null && !callerClass.isEmpty()) {
                StackTraceElement[] stack = new Throwable().getStackTrace();
                // stack[0] = record, stack[1] = instrumented method (advice is
                // inlined), stack[2] = the immediate caller.
                String caller = stack.length > 2 ? stack[2].getClassName() : "";
                if (!caller.equals(callerClass) && !caller.startsWith(callerClass + "$")) {
                    return;
                }
            }
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    row.append('\t');
                }
                row.append('p').append(i).append('=').append(escapeValue(args[i]));
            }
            row.append('\n');
            String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
            String fileName = simpleName + "." + sanitize(methodName) + ".tsv";
            Writer writer = writerFor(fileName);
            synchronized (writer) {
                writer.write(row.toString());
                writer.flush();
            }
        } catch (Throwable t) {
            // The capture must never break the suite under measurement.
        }
    }

    private static Writer writerFor(String fileName) throws IOException {
        Writer existing = WRITERS.get(fileName);
        if (existing != null) {
            return existing;
        }
        synchronized (WRITERS) {
            existing = WRITERS.get(fileName);
            if (existing != null) {
                return existing;
            }
            Path directory = Paths.get(outDir);
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName);
            BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            WRITERS.put(fileName, writer);
            return writer;
        }
    }

    private static String sanitize(String methodName) {
        return "<init>".equals(methodName) ? "init" : methodName;
    }

    // Copied verbatim from the escapeValue helper Teralizer emits into its
    // generalized tests, so string-level set intersection with the jqwik value
    // logs is exact.
    private static String escapeValue(Object value) {
        String raw = String.valueOf(value);
        StringBuilder escaped = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '\\' :
                    escaped.append("\\\\");
                    break;
                case '\n' :
                    escaped.append("\\n");
                    break;
                case '\r' :
                    escaped.append("\\r");
                    break;
                case '\t' :
                    escaped.append("\\t");
                    break;
                default :
                    if (Character.isISOControl(ch) || Character.isSurrogate(ch)) {
                        escaped.append(String.format("\\u%04x", ((int) (ch))));
                    } else {
                        escaped.append(ch);
                    }
            }
        }
        return escaped.toString();
    }
}
