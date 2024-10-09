package teralizer.processing.dependencies;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.TestFramework;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static teralizer.processing.task.AddDependenciesTask.*;

public class MavenDependencyManager {

    private final Path pomFilePath;
    private final Document document;
    private final Element dependenciesElement;
    private final Element pluginsElement;

    private final Set<Dependency> dependencies;
    private final TestFramework testFramework;
    private final Consumer<String> reportInfo;

    public MavenDependencyManager(Path projectPath, TestFramework testFramework, Consumer<String> reportInfo) throws DocumentException {
        this.pomFilePath = projectPath.resolve("pom.xml");
        this.document = new SAXReader().read(this.pomFilePath.toFile());

        Element root = this.document.getRootElement();
        this.dependenciesElement = this.getOrCreateDependenciesElement(root);
        this.pluginsElement = this.getOrCreatePluginsElement(root);

        this.dependencies = this.getDependencies(this.dependenciesElement);

        this.testFramework = testFramework;
        this.reportInfo = reportInfo;
    }

    public void addRequiredDependencies() throws DocumentException, IOException {
        boolean hasModifiedDocument = false;
        if (this.testFramework == TestFramework.JUNIT_4) {
            hasModifiedDocument = hasModifiedDocument || this.addDependency(JUNIT_VINTAGE_DEPENDENCY);
        }
        hasModifiedDocument = hasModifiedDocument || this.addJacocoPlugin();
        hasModifiedDocument = hasModifiedDocument || this.addDependency(PITEST_DEPENDENCY);
        hasModifiedDocument = hasModifiedDocument || this.addPitestPlugin();
        hasModifiedDocument = hasModifiedDocument || this.addDependency(JQWIK_DEPENDENCY);

        if (hasModifiedDocument) {
            XMLWriter writer = new XMLWriter(new FileWriter(this.pomFilePath.toFile()), OutputFormat.createPrettyPrint());
            writer.write(this.document);
            writer.close();
        }
    }

    private Element getOrCreateDependenciesElement(Element root) {
        return this.getOrCreateElement(root, "dependencies");
    }

    private Element getOrCreatePluginsElement(Element root) {
        Element buildElement = this.getOrCreateElement(root, "build");
        return this.getOrCreateElement(buildElement, "plugins");
    }

    private Element getOrCreateElement(Element parent, String name) {
        Element element = parent.element(name);
        if (element == null) {
            element = parent.addElement(name);
            element.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
        }
        return element;
    }

    private Set<Dependency> getDependencies(Element dependenciesElement) {
        Set<Dependency> dependencies = new HashSet<>();
        for (Iterator<Element> it = dependenciesElement.elementIterator(); it.hasNext(); ) {
            Element dependency = it.next();
            String groupId = dependency.element("groupId").getText();
            String artifactId = dependency.element("artifactId").getText();
            String version = dependency.element("version").getText();
            dependencies.add(new Dependency(groupId, artifactId, version));
        }
        return dependencies;
    }

    private boolean addDependency(Dependency dependency) {
        for (Dependency identifiedDependency : this.dependencies) {
            if (identifiedDependency.equals(dependency)) {
                this.reportInfo.accept("Found dependency: " + identifiedDependency);
                return false;
            }
        }
        Element dependencyElement = this.dependenciesElement.addElement("dependency");
        dependencyElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
        dependencyElement.addElement("groupId").addText(dependency.groupId);
        dependencyElement.addElement("artifactId").addText(dependency.artifactId);
        dependencyElement.addElement("version").addText(dependency.version);
        dependencyElement.addElement("scope").addText("test");
        this.reportInfo.accept("Added dependency: " + dependency);
        return true;
    }

    private boolean addJacocoPlugin() throws DocumentException {
        if (this.hasPlugin("org.jacoco", "jacoco-maven-plugin")) {
            this.reportInfo.accept("Found plugin / config: jacoco");
            return false;
        }
        this.pluginsElement.add(this.readPluginConfig(JACOCO_CONFIG_PATH_MAVEN));
        this.reportInfo.accept("Added plugin / config: jacoco");
        return true;
    }

    private boolean addPitestPlugin() throws DocumentException {
        if (this.hasPlugin("org.pitest", "pitest-maven")) {
            this.reportInfo.accept("Found plugin / config: pitest");
            return false;
        }
        this.pluginsElement.add(this.readPluginConfig(PITEST_CONFIG_PATH_MAVEN));
        this.reportInfo.accept("Added plugin / config: pitest");
        return true;
    }

    private boolean hasPlugin(String groupId, String artifactId) {
        XPath xpath = DocumentHelper.createXPath("/m:project/m:build/m:plugins/m:plugin[m:groupId='" + groupId + "' and m:artifactId='" + artifactId+ "']");
        Map<String, String> namespaceURIs = new HashMap<>();
        namespaceURIs.put("m", "http://maven.apache.org/POM/4.0.0");
        xpath.setNamespaceURIs(namespaceURIs);
        List<Node> nodes = xpath.selectNodes(this.document);
        return !nodes.isEmpty();
    }

    private Node readPluginConfig(Path configPath) throws DocumentException {
        Document pitestConfigDocument = new SAXReader().read(configPath.toFile());
        Element pluginElement = pitestConfigDocument.getRootElement();
        pluginElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
        return pluginElement.detach();
    }
}
