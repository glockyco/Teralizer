package teralizer.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Path;

public class JavaParserFactory {

    public static JavaParser createJavaParser(Path mainSourcePath, Path testSourcePath) {
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver(
            new JavaParserTypeSolver(mainSourcePath),
            new JavaParserTypeSolver(testSourcePath),
            new ReflectionTypeSolver()
        );

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));

        return new JavaParser(configuration);
    }
}
