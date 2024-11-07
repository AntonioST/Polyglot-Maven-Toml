package io.ast.maven.polyglot.toml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Map;

import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.model.io.ModelReader;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.maven.polyglot.io.ModelReaderSupport;

@Component(role = ModelProcessor.class)
public class TomlModelProcessor extends ModelReaderSupport implements ModelProcessor {

    @Requirement
    private ModelReader modelReader;

    @Override
    public File locatePom(File projectDirectory) {
        var dir = projectDirectory.toPath();
        var pom = dir.resolve("pom.toml");
        if (!Files.exists(pom)) {
            pom = dir.resolve("pom.xml");
        }
        return pom.toFile();
    }


    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException, ModelParseException {
        var file = getSourceLocation(options);

        // XML FILE ==> DefaultModelReader
        return modelReader.read(input, options);
    }

    private String getSourceLocation(Map<String, ?> options) {
        Object value = (options != null) ? options.get(INPUT_SOURCE) : null;
        if (value instanceof InputSource source) {
            return source.getLocation();
        } else {
            return null;
        }
    }
}
