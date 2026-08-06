package teralizer.processing.reports;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CsvReportParser {

    private CsvReportParser() {
    }

    public static CsvReport parse(Path path) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            boolean quotedField = false;
            boolean fieldStarted = false;
            int character;
            int rowNumber = 1;
            while ((character = reader.read()) != -1) {
                char current = (char) character;
                if (inQuotes) {
                    if (current == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(current);
                    }
                } else if (current == '"') {
                    if (fieldStarted || field.length() > 0) {
                        throw malformed(path, rowNumber, "quote in an unquoted field");
                    }
                    inQuotes = true;
                    quotedField = true;
                    fieldStarted = true;
                } else if (current == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    quotedField = false;
                    fieldStarted = false;
                } else if (current == '\n' || current == '\r') {
                    if (current == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                    }
                    row.add(field.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    quotedField = false;
                    fieldStarted = false;
                    rowNumber++;
                } else {
                    if (quotedField) {
                        throw malformed(path, rowNumber, "characters after a quoted field");
                    }
                    field.append(current);
                    fieldStarted = true;
                }
            }
            if (inQuotes) {
                throw malformed(path, rowNumber, "unterminated quoted field");
            }
            if (fieldStarted || !row.isEmpty()) {
                row.add(field.toString());
                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            throw new RuntimeException("Empty CSV report: " + path);
        }
        List<String> header = rows.get(0);
        if (header.isEmpty()) {
            throw malformed(path, 1, "empty header");
        }
        for (int i = 1; i < rows.size(); i++) {
            List<String> data = rows.get(i);
            if (data.size() != header.size()) {
                throw malformed(path, i + 1, "expected " + header.size() + " columns but found " + data.size());
            }
        }
        return new CsvReport(header, rows.subList(1, rows.size()));
    }

    private static RuntimeException malformed(Path path, int rowNumber, String detail) {
        return new RuntimeException("Malformed CSV report " + path + " at row " + rowNumber + ": " + detail);
    }

    public static final class CsvReport {
        private final List<String> header;
        private final List<List<String>> rows;

        private CsvReport(List<String> header, List<List<String>> rows) {
            this.header = Collections.unmodifiableList(new ArrayList<>(header));
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        public List<String> header() {
            return this.header;
        }

        public List<List<String>> rows() {
            return this.rows;
        }
    }
}
