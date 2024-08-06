package teralizer.jooq;

import org.jooq.Converter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathConverter implements Converter<String, Path> {

    @Override
    public Path from(String s) {
        return Paths.get(s);
    }

    @Override
    public String to(Path path) {
        return path.toString();
    }

    @Override
    public Class<String> fromType() {
        return String.class;
    }

    @Override
    public Class<Path> toType() {
        return Path.class;
    }
}
