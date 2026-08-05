package teralizer.processing.dependencies;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import net.jqwik.api.Example;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Assert;
import teralizer.util.Configuration;

public class MavenSurefireFloorTest {

    @Example
    void pluginPinBelowFloorGetsSurefireFloor() throws Exception {
        Document doc = pluginPom("<plugins>", "</plugins>", "2.17", true);

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertTrue(doc.asXML(), doc.asXML().contains("<version>2.22.2</version>"));
    }

    @Example
    void pluginManagementPinBelowFloorGetsSurefireFloor() throws Exception {
        Document doc = pluginPom("<pluginManagement><plugins>", "</plugins></pluginManagement>", "2.17", false);

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertTrue(doc.asXML(), doc.asXML().contains("<version>2.22.2</version>"));
    }

    @Example
    void staticArgLineWithOldSurefireVersionMergesJacocoProperty() throws Exception {
        Document doc = surefirePom(false, "2.15", "<configuration><argLine>-Xmx1024m</argLine></configuration>");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertEquals(Configuration.SUREFIRE_MIN_VERSION, surefireVersion(doc, false).getTextTrim());
        Assert.assertEquals("@{argLine} -Xmx1024m", surefireArgLine(doc, false).getTextTrim());
    }

    @Example
    void missingArgLineWithOldSurefireVersionStaysAbsent() throws Exception {
        Document doc = surefirePom(false, "2.15", "");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertEquals(Configuration.SUREFIRE_MIN_VERSION, surefireVersion(doc, false).getTextTrim());
        Assert.assertNull(surefireArgLine(doc, false));
    }

    @Example
    void mergedArgLineIsNotPrefixedAgain() throws Exception {
        Document doc = surefirePom(false, "2.15", "<configuration><argLine>-Xmx1024m</argLine></configuration>");

        Assert.assertTrue(MavenDependencyManager.applySurefireFloor(doc));
        Assert.assertEquals("@{argLine} -Xmx1024m", surefireArgLine(doc, false).getTextTrim());

        Assert.assertFalse(MavenDependencyManager.applySurefireFloor(doc));
        Assert.assertEquals("@{argLine} -Xmx1024m", surefireArgLine(doc, false).getTextTrim());
    }

    @Example
    void existingLateBoundArgLineReferenceIsUnchanged() throws Exception {
        Document doc = surefirePom(false, "3.0.0", "<configuration><argLine>@{argLine} -Xmx1024m</argLine></configuration>");
        String before = doc.asXML();

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertEquals(before, doc.asXML());
        Assert.assertEquals("@{argLine} -Xmx1024m", surefireArgLine(doc, false).getTextTrim());
    }

    @Example
    void pluginManagementStaticArgLineMergesJacocoProperty() throws Exception {
        Document doc = surefirePom(true, "2.15", "<configuration><argLine>-ea</argLine></configuration>");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertEquals(Configuration.SUREFIRE_MIN_VERSION, surefireVersion(doc, true).getTextTrim());
        Assert.assertEquals("@{argLine} -ea", surefireArgLine(doc, true).getTextTrim());
    }

    @Example
    void newerSurefireStaticArgLineStillMergesJacocoProperty() throws Exception {
        Document doc = surefirePom(false, "3.0.0", "<configuration><argLine>-Xmx512m</argLine></configuration>");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertEquals("3.0.0", surefireVersion(doc, false).getTextTrim());
        Assert.assertEquals("@{argLine} -Xmx512m", surefireArgLine(doc, false).getTextTrim());
        Assert.assertTrue(changed);
    }

    @Example
    void newerSurefirePinIsUnchanged() throws Exception {
        Document doc = pluginPom("<plugins>", "</plugins>", "3.0.2", true);
        String before = doc.asXML();

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertEquals(before, doc.asXML());
    }

    @Example
    void pomWithoutExplicitSurefirePluginIsUnchanged() throws Exception {
        Document doc = namespacedPom("<build><plugins></plugins></build>");
        String before = doc.asXML();

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertEquals(before, doc.asXML());
    }

    @Example
    void generalizedBuildFileDerivationFloorsCopyWithoutMutatingSharedPom() throws Exception {
        Path tempDir = Files.createTempDirectory("teralizer-surefire-floor");
        Path sharedPomPath = tempDir.resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        Path generalizedPomPath = tempDir.resolve(Configuration.MAVEN_GENERALIZED_BUILD_FILE);
        byte[] sharedPomBytes = pluginPomXml("<plugins>", "</plugins>", "2.17", true).getBytes(StandardCharsets.UTF_8);
        Files.write(sharedPomPath, sharedPomBytes);

        MavenDependencyManager.deriveGeneralizedBuildFile(sharedPomPath, generalizedPomPath);

        Assert.assertTrue(Files.exists(generalizedPomPath));
        Assert.assertTrue(readUtf8(generalizedPomPath).contains("<version>" + Configuration.SUREFIRE_MIN_VERSION + "</version>"));
        Assert.assertArrayEquals(sharedPomBytes, Files.readAllBytes(sharedPomPath));
    }

    @Example
    void generalizedBuildFileDerivationMergesArgLineOnlyInDerivedPom() throws Exception {
        Path tempDir = Files.createTempDirectory("teralizer-surefire-argline");
        Path sharedPomPath = tempDir.resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        Path generalizedPomPath = tempDir.resolve(Configuration.MAVEN_GENERALIZED_BUILD_FILE);
        byte[] sharedPomBytes = surefirePomXml(
            false,
            "2.15",
            "<configuration><argLine>-ea</argLine></configuration>"
        ).getBytes(StandardCharsets.UTF_8);
        Files.write(sharedPomPath, sharedPomBytes);

        MavenDependencyManager.deriveGeneralizedBuildFile(sharedPomPath, generalizedPomPath);

        Assert.assertArrayEquals(sharedPomBytes, Files.readAllBytes(sharedPomPath));
        Assert.assertEquals("-ea", surefireArgLine(DocumentHelper.parseText(readUtf8(sharedPomPath)), false).getTextTrim());
        Assert.assertEquals(
            "@{argLine} -ea",
            surefireArgLine(DocumentHelper.parseText(readUtf8(generalizedPomPath)), false).getTextTrim()
        );
    }

    @Example
    void generalizedBuildFileDerivationCopiesNewerSurefirePinWithoutAssigningFloor() throws Exception {
        Path tempDir = Files.createTempDirectory("teralizer-surefire-floor");
        Path sharedPomPath = tempDir.resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        Path generalizedPomPath = tempDir.resolve(Configuration.MAVEN_GENERALIZED_BUILD_FILE);
        Files.write(sharedPomPath, pluginPomXml("<plugins>", "</plugins>", "3.0.2", true).getBytes(StandardCharsets.UTF_8));

        MavenDependencyManager.deriveGeneralizedBuildFile(sharedPomPath, generalizedPomPath);

        String generalizedPom = readUtf8(generalizedPomPath);
        Assert.assertTrue(Files.exists(generalizedPomPath));
        Assert.assertTrue(generalizedPom.contains("<version>3.0.2</version>"));
        Assert.assertFalse(generalizedPom.contains("<version>" + Configuration.SUREFIRE_MIN_VERSION + "</version>"));
    }

    @Example
    void jacocoPinBelowFloorGetsFloored() throws Exception {
        Document doc = toolPluginPom("org.jacoco", "jacoco-maven-plugin", "0.7.2.201409121644");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertTrue(doc.asXML(), doc.asXML().contains("<version>" + Configuration.JACOCO_MIN_VERSION + "</version>"));
    }

    @Example
    void pitestPinBelowFloorGetsFloored() throws Exception {
        Document doc = toolPluginPom("org.pitest", "pitest-maven", "0.30");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertTrue(changed);
        Assert.assertTrue(doc.asXML(), doc.asXML().contains("<version>" + Configuration.PITEST_MIN_VERSION + "</version>"));
    }

    @Example
    void propertyManagedToolPinsBelowFloorGetFloored() throws Exception {
        Document jacoco = propertyManagedToolPluginPom(
            "jacoco.version", "0.7.5.201505241946", "org.jacoco", "jacoco-maven-plugin");
        Document pitest = propertyManagedToolPluginPom(
            "pitest.version", "0.30", "org.pitest", "pitest-maven");

        Assert.assertTrue(MavenDependencyManager.applySurefireFloor(jacoco));
        Assert.assertTrue(MavenDependencyManager.applySurefireFloor(pitest));
        Assert.assertTrue(jacoco.asXML(), jacoco.asXML().contains(
            "<version>" + Configuration.JACOCO_MIN_VERSION + "</version>"));
        Assert.assertTrue(pitest.asXML(), pitest.asXML().contains(
            "<version>" + Configuration.PITEST_MIN_VERSION + "</version>"));
    }

    @Example
    void propertyManagedSurefirePinBelowFloorGetsFloored() throws Exception {
        Document doc = propertyManagedToolPluginPom(
            "surefire.version", "2.17", "org.apache.maven.plugins", "maven-surefire-plugin");

        Assert.assertTrue(MavenDependencyManager.applySurefireFloor(doc));
        Assert.assertTrue(doc.asXML(), doc.asXML().contains(
            "<version>" + Configuration.SUREFIRE_MIN_VERSION + "</version>"));
    }

    @Example
    void unresolvedPluginVersionPropertyIsKept() throws Exception {
        Document doc = DocumentHelper.parseText("<project><build><plugins><plugin>"
            + "<groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId>"
            + "<version>${missing.version}</version></plugin></plugins></build></project>");
        String before = doc.asXML();

        Assert.assertFalse(MavenDependencyManager.applySurefireFloor(doc));
        Assert.assertEquals(before, doc.asXML());
    }

    @Example
    void newerToolPinsAreKept() throws Exception {
        Document doc = toolPluginPom("org.pitest", "pitest-maven", "1.19.5");

        boolean changed = MavenDependencyManager.applySurefireFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertTrue(doc.asXML(), doc.asXML().contains("<version>1.19.5</version>"));
    }

    private static Document toolPluginPom(String groupId, String artifactId, String version) throws Exception {
        String xml = "<project><build><plugins><plugin>"
            + "<groupId>" + groupId + "</groupId>"
            + "<artifactId>" + artifactId + "</artifactId>"
            + "<version>" + version + "</version>"
            + "</plugin></plugins></build></project>";
        return DocumentHelper.parseText(xml);
    }

    private static Document propertyManagedToolPluginPom(
        String propertyName,
        String propertyValue,
        String groupId,
        String artifactId
    ) throws Exception {
        String xml = "<project><properties><" + propertyName + ">" + propertyValue
            + "</" + propertyName + "></properties><build><plugins><plugin>"
            + "<groupId>" + groupId + "</groupId>"
            + "<artifactId>" + artifactId + "</artifactId>"
            + "<version>${" + propertyName + "}</version>"
            + "</plugin></plugins></build></project>";
        return DocumentHelper.parseText(xml);
    }

    private static Document pluginPom(String pluginsOpen, String pluginsClose, String version, boolean includeGroupId) throws Exception {
        return DocumentHelper.parseText(pluginPomXml(pluginsOpen, pluginsClose, version, includeGroupId));
    }

    private static Document pluginPom(
        String pluginsOpen,
        String pluginsClose,
        String version,
        boolean includeGroupId,
        String configurationXml
    ) throws Exception {
        return DocumentHelper.parseText(pluginPomXml(pluginsOpen, pluginsClose, version, includeGroupId, configurationXml));
    }

    private static String pluginPomXml(String pluginsOpen, String pluginsClose, String version, boolean includeGroupId) {
        return pluginPomXml(pluginsOpen, pluginsClose, version, includeGroupId, "");
    }

    private static String pluginPomXml(
        String pluginsOpen,
        String pluginsClose,
        String version,
        boolean includeGroupId,
        String configurationXml
    ) {
        return namespacedPomXml(
            "<build>"
                + pluginsOpen
                + "<plugin>"
                + (includeGroupId ? "<groupId>org.apache.maven.plugins</groupId>" : "")
                + "<artifactId>maven-surefire-plugin</artifactId>"
                + "<version>" + version + "</version>"
                + configurationXml
                + "</plugin>"
                + pluginsClose
                + "</build>"
        );
    }

    private static Document surefirePom(boolean inPluginManagement, String version, String configurationXml) throws Exception {
        return pluginPom(
            inPluginManagement ? "<pluginManagement><plugins>" : "<plugins>",
            inPluginManagement ? "</plugins></pluginManagement>" : "</plugins>",
            version,
            true,
            configurationXml
        );
    }

    private static String surefirePomXml(boolean inPluginManagement, String version, String configurationXml) {
        return pluginPomXml(
            inPluginManagement ? "<pluginManagement><plugins>" : "<plugins>",
            inPluginManagement ? "</plugins></pluginManagement>" : "</plugins>",
            version,
            true,
            configurationXml
        );
    }

    private static Document namespacedPom(String body) throws Exception {
        return DocumentHelper.parseText(namespacedPomXml(body));
    }

    private static String namespacedPomXml(String body) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
            + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">"
            + "<modelVersion>4.0.0</modelVersion>"
            + "<groupId>example</groupId>"
            + "<artifactId>subject</artifactId>"
            + "<version>1.0</version>"
            + body
            + "</project>";
    }

    private static Element surefireVersion(Document document, boolean inPluginManagement) {
        return childElement(surefirePlugin(document, inPluginManagement), "version");
    }

    private static Element surefireArgLine(Document document, boolean inPluginManagement) {
        Element configuration = childElement(surefirePlugin(document, inPluginManagement), "configuration");
        return childElement(configuration, "argLine");
    }

    private static Element surefirePlugin(Document document, boolean inPluginManagement) {
        Element build = childElement(document.getRootElement(), "build");
        Element pluginManagement = inPluginManagement ? childElement(build, "pluginManagement") : null;
        Element plugins = childElement(inPluginManagement ? pluginManagement : build, "plugins");
        for (Iterator<Element> it = plugins.elementIterator(); it.hasNext(); ) {
            Element plugin = it.next();
            if ("maven-surefire-plugin".equals(childText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        throw new AssertionError("Expected maven-surefire-plugin in test POM");
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

    private static String childText(Element parent, String childName) {
        Element child = childElement(parent, childName);
        return child == null ? null : child.getTextTrim();
    }

    private static String readUtf8(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
