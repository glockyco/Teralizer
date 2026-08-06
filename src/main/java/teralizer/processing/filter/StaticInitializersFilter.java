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
        // A class absent from the model yields no evidence about initializers. This filter only
        // reports, so it accepts rather than failing the filtering task for every other filter too.
        if (testClass == null) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        if (!testClass.getAnonymousExecutables().isEmpty()) {
            String reason = "Test class contains (static) initializers: " + this.testRecord.getTestClassQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.DEFER, reason, FilterReasonCodes.STATIC_INITIALIZERS_PRESENT);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
