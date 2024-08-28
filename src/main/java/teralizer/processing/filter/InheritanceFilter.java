package teralizer.processing.filter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.jooq.generated.tables.records.TestRecord;

import java.io.IOException;
import java.nio.file.Paths;

public class InheritanceFilter extends AbstractFilter {

    private final JavaParser javaParser;

    public InheritanceFilter(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    @Override
    public FilterResult check(TestRecord testRecord) throws IOException {
        CompilationUnit compilationUnit = this.javaParser.parse(Paths.get(testRecord.getTestClassPath())).getResult().get();
        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(testRecord.getTestClassName()).get();

        if (testClassDeclaration.getExtendedTypes().isNonEmpty()) {
            // @TODO: Add generalization support for classes that implement interfaces or extend other (abstract) classes.
            //   ---
            //   Test classes that implement interfaces or extend other (abstract) classes might cause
            //   problems because the implementation of the generalization task does not currently include
            //   all the code of the original test class in the generalized class.
            //   ---
            //   Consequently, implementations for abstract methods might be missing in the generalized
            //   class, thus causing build failures. Similarly, overrides of non-abstract parent methods
            //   might be missing, thus causing unintended behavior.
            return new FilterResult(this.getName(), FilterDecision.REJECT, "The test class has parents " + testClassDeclaration.getExtendedTypes() + ".");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
