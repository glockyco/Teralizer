package teralizer.processing.dependencies;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import teralizer.TestGeneralizationRunner;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static teralizer.processing.task.AddDependenciesTask.PITEST_CONFIG_PATH_MAVEN;

public class MavenDependencyManager {

    private final Path pomFilePath;
    private final Document document;
    private final Element root;
    private final Element dependencies;

    public MavenDependencyManager(Path projectPath) throws DocumentException {
        this.pomFilePath = projectPath.resolve("pom.xml");
        this.document = new SAXReader().read(this.pomFilePath.toFile());
        this.root = this.document.getRootElement();

        Element dependencies = this.root.element("dependencies");
        this.dependencies = dependencies != null ? dependencies : this.root.addElement("dependencies");
    }

    public Set<Dependency> detectInProject(Set<Dependency> requiredDependencies) {
        Set<Dependency> identifiedDependencies = new HashSet<>();

        for (Iterator<Element> it = this.dependencies.elementIterator(); it.hasNext(); ) {
            Element dependency = it.next();
            String groupId = dependency.element("groupId").getText();
            String artifactId = dependency.element("artifactId").getText();
            String version = dependency.element("version").getText();

            Dependency identifiedDependency = new Dependency(groupId, artifactId, version);
            if (requiredDependencies.contains(identifiedDependency)) {
                identifiedDependencies.add(identifiedDependency);
            }
        }

        return identifiedDependencies;
    }

    public void addToProject(Set<Dependency> missingDependencies) throws IOException, DocumentException {
        boolean hasModifiedDocument = false;

        if (!missingDependencies.isEmpty()) {
            for (Dependency addedDependency : missingDependencies) {
                Element dependency = this.dependencies.addElement("dependency");
                dependency.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
                dependency.addElement("groupId").addText(addedDependency.groupId);
                dependency.addElement("artifactId").addText(addedDependency.artifactId);
                dependency.addElement("version").addText(addedDependency.version);
                dependency.addElement("scope").addText("test");
            }
            hasModifiedDocument = true;
        }

        // Add the PIT configuration if it does not exist yet:
        XPath xpath = DocumentHelper.createXPath("/m:project/m:build/m:plugins/m:plugin[m:groupId='org.pitest' and m:artifactId='pitest-maven']");
        Map<String, String> namespaceURIs = new HashMap<>();
        namespaceURIs.put("m", "http://maven.apache.org/POM/4.0.0");
        xpath.setNamespaceURIs(namespaceURIs);
        List<Node> nodes = xpath.selectNodes(this.document);

        if (nodes.isEmpty()) {
            Element buildElement = this.root.element("build");
            if (buildElement == null) {
                buildElement = this.root.addElement("build");
                buildElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            }

            Element pluginsElement = buildElement.element("plugins");
            if (pluginsElement == null) {
                pluginsElement = buildElement.addElement("plugins");
                pluginsElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            }

            Document pitestConfigDocument = new SAXReader().read(PITEST_CONFIG_PATH_MAVEN.toFile());
            Element pluginElement = pitestConfigDocument.getRootElement();
            pluginElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            pluginsElement.add(pluginElement.detach());

            hasModifiedDocument = true;
        }

        // Write the changes to the pom.xml file:
        if (hasModifiedDocument) {
            XMLWriter writer = new XMLWriter(new FileWriter(this.pomFilePath.toFile()), OutputFormat.createPrettyPrint());
            writer.write(this.document);
            writer.close();
        }
    }
}
