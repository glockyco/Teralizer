package teralizer.processing.dependencies;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.TestFramework;
import teralizer.util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class MavenDependencyManager {

    private final ProjectRecord projectRecord;
    private final Consumer<String> reportInfo;

    private final Path pomFilePath;
    private final Document document;
    private final Element dependenciesElement;
    private final Element pluginsElement;

    private final Set<Dependency> dependencies;

    public MavenDependencyManager(ProjectRecord projectRecord, Consumer<String> reportInfo) throws DocumentException {
        this.projectRecord = projectRecord;
        this.reportInfo = reportInfo;

        this.pomFilePath = this.projectRecord.getRootPath().resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        this.document = new SAXReader().read(this.pomFilePath.toFile());

        Element root = this.document.getRootElement();
        this.dependenciesElement = this.getOrCreateDependenciesElement(root);
        this.pluginsElement = this.getOrCreatePluginsElement(root);

        this.dependencies = this.getDependencies(this.dependenciesElement);
    }

    public void addRequiredDependencies() throws DocumentException, IOException {
        boolean hasModifiedDocument = false;
        if (this.projectRecord.getTestFramework() == TestFramework.JUNIT_4) {
            // Deliberately using non-short-circuiting OR here. If multiple
            // dependencies are missing, we want to add all of them.
            hasModifiedDocument |= this.addJUnitIfOutdated();
            hasModifiedDocument |= this.addDependencyIfMissing(Configuration.JUNIT_VINTAGE_DEPENDENCY);
        }
        hasModifiedDocument |= this.addJacocoPlugin();
        hasModifiedDocument |= this.addDependencyIfMissing(Configuration.PITEST_DEPENDENCY);
        hasModifiedDocument |= this.addPitestPlugin();
        hasModifiedDocument |= this.addDependencyIfMissing(Configuration.JQWIK_DEPENDENCY);

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
            element.addComment("Added by " + Configuration.TOOL_NAME + ".");
        }
        return element;
    }

    private Set<Dependency> getDependencies(Element dependenciesElement) {
        Set<Dependency> dependencies = new HashSet<>();
        for (Iterator<Element> it = dependenciesElement.elementIterator(); it.hasNext(); ) {
            Element dependency = it.next();
            String groupId = dependency.element("groupId").getText();
            String artifactId = dependency.element("artifactId").getText();
            Element versionElement = dependency.element("version");
            String version = versionElement == null ? null : versionElement.getText();
            dependencies.add(new Dependency(groupId, artifactId, version));
        }
        return dependencies;
    }

    private boolean addJUnitIfOutdated() {
        String testFrameworkVersion = this.projectRecord.getTestFrameworkVersion();
        // Check whether a recent enough version of JUnit 4 is used (JUnit
        // Vintage requires at least JUnit 4.12). Not a very clean solution,
        // but good enough for our purposes.
        for (int i = 12; i < 20; i++) {
            if (testFrameworkVersion.startsWith("4." + i)) {
                return false;
            }
        }
        // If the detected JUnit version is not supported, we just add one
        // that is in addition to the one that is already present. This is easy
        // to do, but is not necessarily guaranteed to convince Maven that the
        // newly added version should be used over the existing one.
        // @TODO: Update the existing JUnit version instead of adding a new one.
        this.addDependency(Configuration.JUNIT_4_DEPENDENCY);
        return true;
    }

    private boolean addDependencyIfMissing(Dependency dependency) {
        for (Dependency identifiedDependency : this.dependencies) {
            if (identifiedDependency.equals(dependency)) {
                this.reportInfo.accept("Found dependency: " + identifiedDependency);
                return false;
            }
        }
        this.addDependency(dependency);
        this.reportInfo.accept("Added dependency: " + dependency);
        return true;
    }

    private void addDependency(Dependency dependency) {
        Element dependencyElement = this.dependenciesElement.addElement("dependency");
        dependencyElement.addComment("Added by " + Configuration.TOOL_NAME + ".");
        dependencyElement.addElement("groupId").addText(dependency.groupId);
        dependencyElement.addElement("artifactId").addText(dependency.artifactId);
        dependencyElement.addElement("version").addText(dependency.version);
        dependencyElement.addElement("scope").addText("test");
    }

    private boolean addJacocoPlugin() throws DocumentException {
        if (this.hasPlugin("org.jacoco", "jacoco-maven-plugin")) {
            this.reportInfo.accept("Found plugin / config: jacoco");
            return false;
        }
        this.pluginsElement.add(this.readPluginConfig(Configuration.MAVEN_JACOCO_CONFIG_PATH));
        this.reportInfo.accept("Added plugin / config: jacoco");
        return true;
    }

    private boolean addPitestPlugin() throws DocumentException {
        if (this.hasPlugin("org.pitest", "pitest-maven")) {
            this.reportInfo.accept("Found plugin / config: pitest");
            return false;
        }
        this.pluginsElement.add(this.readPluginConfig(Configuration.MAVEN_PITEST_CONFIG_PATH));
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
        Comment comment = DocumentHelper.createComment("Added by " + Configuration.TOOL_NAME + ".");
        pluginElement.content().add(0, comment);
        return pluginElement.detach();
    }

    public static void updatePitestTargets(Path pomFilePath, List<String> targetClasses, List<String> targetTests) throws DocumentException, IOException {
        Document pomDocument = new SAXReader().read(pomFilePath.toFile());

        XPath xpath = DocumentHelper.createXPath("/m:project/m:build/m:plugins/m:plugin[m:groupId='org.pitest' and m:artifactId='pitest-maven']");
        Map<String, String> namespaceURIs = new HashMap<>();
        namespaceURIs.put("m", "http://maven.apache.org/POM/4.0.0");
        xpath.setNamespaceURIs(namespaceURIs);

        List<Node> nodes = xpath.selectNodes(pomDocument);
        if (nodes.isEmpty()) {
            throw new RuntimeException("PIT plugin not found in POM file.");
        }

        Element pitestPlugin = (Element) nodes.get(0);
        Element configElement = pitestPlugin.element("configuration");
        if (configElement == null) {
            configElement = pitestPlugin.addElement("configuration");
            configElement.addComment("Configuration added by " + Configuration.TOOL_NAME + ".");
        }

        if (targetClasses != null) {
            updateXmlListElement(configElement, "targetClasses", targetClasses);
        }

        if (targetTests != null) {
            updateXmlListElement(configElement, "targetTests", targetTests);
        }

        XMLWriter writer = new XMLWriter(new FileWriter(pomFilePath.toFile()), OutputFormat.createPrettyPrint());
        writer.write(pomDocument);
        writer.close();
    }

    private static void updateXmlListElement(Element parent, String elementName, List<String> values) {
        // Remove the existing element if it exists.
        Element existingElement = parent.element(elementName);
        if (existingElement != null) {
            parent.remove(existingElement);
        }

        // Create the new element with values.
        Element element = parent.addElement(elementName);
        element.addComment("Added by " + Configuration.TOOL_NAME + ".");
        for (String value : values) {
            element.addElement("param").addText(value);
        }
    }
}
