package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

public interface Filter {
    FilterResult check(TestRecord testRecord) throws Exception;
}
