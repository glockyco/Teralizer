package teralizer.processing.filter;

import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtTypeInformation;

public class NestedClassesFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final TestRecord testRecord;

    public NestedClassesFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.spoonLauncher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        CtClass<?> testClass = this.spoonLauncher.getFactory().Class().get(this.testRecord.getTestClassQualifiedName());
        // A class absent from the model yields no evidence about nested classes. This filter only
        // reports, so it accepts rather than failing the filtering task for every other filter too.
        if (testClass == null) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        Set<String> nestedClasses = testClass.getNestedTypes().stream()
            .filter(CtTypeInformation::isClass)
            .map(CtTypeInformation::getQualifiedName)
            .collect(Collectors.toSet());

        if (nestedClasses.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        String classesStr = String.join(", ", nestedClasses);
        String reason = "Test class contains nested classes (" + classesStr + "): " + this.testRecord.getTestClassQualifiedName();
        return new FilterResult(this.getName(), FilterDecision.DEFER, reason, FilterReasonCodes.NESTED_CLASSES);
    }
}
