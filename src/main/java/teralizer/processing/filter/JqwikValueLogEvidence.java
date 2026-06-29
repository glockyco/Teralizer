package teralizer.processing.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class JqwikValueLogEvidence {
    private final boolean readable;
    private final Integer distinctNewTuples;

    private JqwikValueLogEvidence(boolean readable, Integer distinctNewTuples) {
        this.readable = readable;
        this.distinctNewTuples = distinctNewTuples;
    }

    public static JqwikValueLogEvidence read(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String seed = reader.readLine();
            if (seed == null) {
                return new JqwikValueLogEvidence(false, null);
            }

            Set<String> distinctNewRows = new HashSet<>();
            String row;
            while ((row = reader.readLine()) != null) {
                if (!seed.equals(row)) {
                    distinctNewRows.add(row);
                }
            }
            return new JqwikValueLogEvidence(true, distinctNewRows.size());
        } catch (IOException | SecurityException e) {
            return new JqwikValueLogEvidence(false, null);
        }
    }

    public boolean isReadable() {
        return this.readable;
    }

    public Integer getDistinctNewTuples() {
        return this.distinctNewTuples;
    }
}
