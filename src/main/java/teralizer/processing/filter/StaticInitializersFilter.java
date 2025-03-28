package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

public class StaticInitializersFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final TestRecord testRecord;

    public StaticInitializersFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.spoonLauncher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        CtClass<?> testClass = this.spoonLauncher.getFactory().Class().get(this.testRecord.getTestClassQualifiedName());

        if (!testClass.getAnonymousExecutables().isEmpty()) {
            String reason = "Test class contains (static) initializers: " + this.testRecord.getTestClassQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
