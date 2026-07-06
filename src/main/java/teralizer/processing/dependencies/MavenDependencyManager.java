package teralizer.processing.dependencies;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.TestFramework;
import teralizer.util.Configuration;

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
        hasModifiedDocument |= applyTestCompilerFloor(this.document);

        if (hasModifiedDocument) {
            XMLWriter writer = new XMLWriter(new FileWriter(this.pomFilePath.toFile()), OutputFormat.createPrettyPrint());
            writer.write(this.document);
            writer.close();
        }
        Path generalizedPomFilePath = this.projectRecord.getRootPath().resolve(Configuration.MAVEN_GENERALIZED_BUILD_FILE);
        deriveGeneralizedBuildFile(this.pomFilePath, generalizedPomFilePath);
    }

    /**
     * Generalized tests need a JUnit-platform-capable surefire runner, while original and
     * initial suites must keep the project's declared runner behavior. Flooring the shared
     * Teralizer POM would make native-suite stages execute under a different surefire than the
     * project pins, which can alter discovery and execution enough to hang or exceed the
     * uniform execution ceiling. The derived POM is written unconditionally so generalized
     * execution can depend on its existence without changing the build file used by native
     * suites.
     */
    static void deriveGeneralizedBuildFile(Path sharedPomPath, Path generalizedPomPath) throws DocumentException, IOException {
        Document generalizedDocument = new SAXReader().read(sharedPomPath.toFile());
        applySurefireFloor(generalizedDocument);

        XMLWriter writer = new XMLWriter(new FileWriter(generalizedPomPath.toFile()), OutputFormat.createPrettyPrint());
        writer.write(generalizedDocument);
        writer.close();
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

    /**
     * Generated property tests inline a telemetry harness that uses Java 8 syntax. Only test
     * compilation is floored here, so the target project's main sources keep their declared
     * language level. The floor is applied only when a below-floor pin is visible in the copied
     * POM: parent-POM inheritance and plugin defaults are intentionally left untouched because a
     * visible build failure is safer than guessing at an effective level that may already be high
     * enough.
     */
    static boolean applyTestCompilerFloor(Document document) {
        Element properties = childElement(document.getRootElement(), "properties");
        Element compilerConfiguration = findCompilerPluginConfiguration(document);
        if (compilerConfiguration != null) {
            Element testSource = childElement(compilerConfiguration, "testSource");
            if (testSource != null) {
                return applyTestCompilerFloorToPluginConfiguration(compilerConfiguration, testSource.getTextTrim(), properties);
            }

            Element source = childElement(compilerConfiguration, "source");
            if (source != null) {
                return applyTestCompilerFloorToPluginConfiguration(compilerConfiguration, source.getTextTrim(), properties);
            }
        }

        if (properties == null) {
            return false;
        }

        Element testSource = childElement(properties, "maven.compiler.testSource");
        if (testSource != null) {
            return applyTestCompilerFloorToProperties(properties, testSource.getTextTrim());
        }

        Element source = childElement(properties, "maven.compiler.source");
        if (source != null) {
            return applyTestCompilerFloorToProperties(properties, source.getTextTrim());
        }

        // The release property is a Java 9+ cross-compilation knob, so it never indicates a
        // below-Java-8 test source level for this floor.
        return false;
    }

    private static boolean applyTestCompilerFloorToPluginConfiguration(Element compilerConfiguration, String languageLevel, Element properties) {
        if (!isBelowGeneratedTestLanguageLevel(resolvePropertyReference(languageLevel, properties))) {
            return false;
        }
        setChildText(compilerConfiguration, "testSource", Configuration.GENERATED_TEST_LANGUAGE_LEVEL);
        setChildText(compilerConfiguration, "testTarget", Configuration.GENERATED_TEST_LANGUAGE_LEVEL);
        return true;
    }

    private static boolean applyTestCompilerFloorToProperties(Element properties, String languageLevel) {
        if (!isBelowGeneratedTestLanguageLevel(resolvePropertyReference(languageLevel, properties))) {
            return false;
        }
        setChildText(properties, "maven.compiler.testSource", Configuration.GENERATED_TEST_LANGUAGE_LEVEL);
        setChildText(properties, "maven.compiler.testTarget", Configuration.GENERATED_TEST_LANGUAGE_LEVEL);
        return true;
    }

    private static String resolvePropertyReference(String languageLevel, Element properties) {
        if (languageLevel == null || properties == null) {
            return languageLevel;
        }

        String trimmed = languageLevel.trim();
        if (!trimmed.startsWith("${") || !trimmed.endsWith("}") || trimmed.indexOf("${", 2) >= 0) {
            return languageLevel;
        }

        String propertyName = trimmed.substring(2, trimmed.length() - 1);
        if (propertyName.isEmpty()) {
            return languageLevel;
        }

        String resolved = childText(properties, propertyName);
        return resolved == null ? languageLevel : resolved;
    }

    /**
     * Surefire only gains a JUnit-platform provider at 2.22.2; older explicit pins report a green
     * Maven build while never discovering jqwik property classes. Existing plugin declarations are
     * floored in place because adding a second surefire declaration would leave Maven plugin
     * selection ambiguous. Unparseable versions are left untouched so property-managed or qualified
     * project policies stay under the project's control.
     */
    static boolean applySurefireFloor(Document document) {
        Element build = childElement(document.getRootElement(), "build");
        if (build == null) {
            return false;
        }

        boolean changed = applySurefireFloorToPlugins(childElement(build, "plugins"));
        Element pluginManagement = childElement(build, "pluginManagement");
        changed |= pluginManagement != null && applySurefireFloorToPlugins(childElement(pluginManagement, "plugins"));
        return changed;
    }

    private static boolean applySurefireFloorToPlugins(Element plugins) {
        if (plugins == null) {
            return false;
        }

        boolean changed = false;
        for (Iterator<Element> it = plugins.elementIterator(); it.hasNext(); ) {
            Element plugin = it.next();
            if (!isSurefirePlugin(plugin)) {
                continue;
            }

            Element version = childElement(plugin, "version");
            if (version != null && isVersionBelow(version.getTextTrim(), Configuration.SUREFIRE_MIN_VERSION)) {
                version.setText(Configuration.SUREFIRE_MIN_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isSurefirePlugin(Element plugin) {
        String groupId = childText(plugin, "groupId");
        String artifactId = childText(plugin, "artifactId");
        return "maven-surefire-plugin".equals(artifactId) && (groupId == null || "org.apache.maven.plugins".equals(groupId));
    }

    private static boolean isVersionBelow(String version, String minimumVersion) {
        int[] parsedVersion = parseNumericVersion(version);
        int[] parsedMinimumVersion = parseNumericVersion(minimumVersion);
        if (parsedVersion == null || parsedMinimumVersion == null) {
            return false;
        }

        for (int i = 0; i < parsedVersion.length; i++) {
            if (parsedVersion[i] != parsedMinimumVersion[i]) {
                return parsedVersion[i] < parsedMinimumVersion[i];
            }
        }
        return false;
    }

    private static int[] parseNumericVersion(String version) {
        if (version == null) {
            return null;
        }

        String[] parts = version.trim().split("\\.", -1);
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }

        int[] parsed = new int[]{0, 0, 0};
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            try {
                parsed[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return parsed;
    }

    private static Element findCompilerPluginConfiguration(Document document) {
        Element build = childElement(document.getRootElement(), "build");
        if (build == null) {
            return null;
        }

        Element compilerPlugin = findCompilerPlugin(childElement(build, "plugins"));
        if (compilerPlugin == null) {
            Element pluginManagement = childElement(build, "pluginManagement");
            compilerPlugin = pluginManagement == null ? null : findCompilerPlugin(childElement(pluginManagement, "plugins"));
        }
        return compilerPlugin == null ? null : childElement(compilerPlugin, "configuration");
    }

    private static Element findCompilerPlugin(Element plugins) {
        if (plugins == null) {
            return null;
        }

        for (Iterator<Element> it = plugins.elementIterator(); it.hasNext(); ) {
            Element plugin = it.next();
            String groupId = childText(plugin, "groupId");
            String artifactId = childText(plugin, "artifactId");
            if ("maven-compiler-plugin".equals(artifactId) && (groupId == null || "org.apache.maven.plugins".equals(groupId))) {
                return plugin;
            }
        }
        return null;
    }

    private static boolean isBelowGeneratedTestLanguageLevel(String languageLevel) {
        if (languageLevel == null) {
            return false;
        }

        switch (languageLevel.trim()) {
            case "1.1":
            case "1.2":
            case "1.3":
            case "1.4":
            case "1.5":
            case "1.6":
            case "1.7":
            case "5":
            case "6":
            case "7":
                return true;
            default:
                return false;
        }
    }

    private static void setChildText(Element parent, String childName, String text) {
        Element child = childElement(parent, childName);
        if (child == null) {
            child = parent.addElement(childName);
        }
        child.setText(text);
    }

    private static String childText(Element parent, String childName) {
        Element child = childElement(parent, childName);
        return child == null ? null : child.getTextTrim();
    }

    private static Element childElement(Element parent, String childName) {
        if (parent == null) {
            return null;
        }

        for (Iterator<Element> it = parent.elementIterator(); it.hasNext(); ) {
            Element child = it.next();
            if (childName.equals(child.getName())) {
                return child;
            }
        }
        return null;
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
