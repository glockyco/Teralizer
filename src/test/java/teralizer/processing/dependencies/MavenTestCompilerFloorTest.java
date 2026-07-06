package teralizer.processing.dependencies;

import net.jqwik.api.Example;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.junit.Assert;

public class MavenTestCompilerFloorTest {

    @Example
    void pluginPinBelowFloorGetsTestFloor() throws Exception {
        Document doc = pluginPom("<source>1.5</source><target>1.5</target>");

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertTrue(changed);
        String xml = doc.asXML();
        Assert.assertTrue(xml, xml.contains("<testSource>1.8</testSource>"));
        Assert.assertTrue(xml, xml.contains("<testTarget>1.8</testTarget>"));
        Assert.assertTrue(xml, xml.contains("<source>1.5</source>"));
        Assert.assertTrue(xml, xml.contains("<target>1.5</target>"));
    }

    @Example
    void pluginPropertyReferenceBelowFloorGetsTestFloor() throws Exception {
        Document doc = pluginPom("<source>${java.version}</source><target>${java.version}</target>")
            .getRootElement()
            .addElement("properties")
            .addElement("java.version")
            .addText("1.5")
            .getDocument();

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertTrue(changed);
        String xml = doc.asXML();
        Assert.assertTrue(xml, xml.contains("<testSource>1.8</testSource>"));
        Assert.assertTrue(xml, xml.contains("<testTarget>1.8</testTarget>"));
        Assert.assertTrue(xml, xml.contains("<source>${java.version}</source>"));
        Assert.assertTrue(xml, xml.contains("<java.version>1.5</java.version>"));
    }


    @Example
    void propertyPinBelowFloorGetsTestProperties() throws Exception {
        Document doc = propertiesPom("<maven.compiler.source>1.7</maven.compiler.source>");

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertTrue(changed);
        String xml = doc.asXML();
        Assert.assertTrue(xml, xml.contains("<maven.compiler.testSource>1.8</maven.compiler.testSource>"));
        Assert.assertTrue(xml, xml.contains("<maven.compiler.testTarget>1.8</maven.compiler.testTarget>"));
        Assert.assertTrue(xml, xml.contains("<maven.compiler.source>1.7</maven.compiler.source>"));
    }

    @Example
    void compilerPropertyReferenceBelowFloorGetsTestProperties() throws Exception {
        Document doc = propertiesPom(
            "<maven.compiler.source>${java.version}</maven.compiler.source>"
                + "<java.version>1.5</java.version>"
        );

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertTrue(changed);
        String xml = doc.asXML();
        Assert.assertTrue(xml, xml.contains("<maven.compiler.testSource>1.8</maven.compiler.testSource>"));
        Assert.assertTrue(xml, xml.contains("<maven.compiler.testTarget>1.8</maven.compiler.testTarget>"));
        Assert.assertTrue(xml, xml.contains("<maven.compiler.source>${java.version}</maven.compiler.source>"));
        Assert.assertTrue(xml, xml.contains("<java.version>1.5</java.version>"));
    }


    @Example
    void existingJava8PinIsUnchanged() throws Exception {
        Document doc = pluginPom("<source>1.8</source><target>1.8</target>");
        String before = doc.asXML();

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertEquals(before, doc.asXML());
    }

    @Example
    void pomWithoutExplicitCompilerPinIsUnchanged() throws Exception {
        Document doc = namespacedPom("<build><plugins></plugins></build>");
        String before = doc.asXML();

        boolean changed = MavenDependencyManager.applyTestCompilerFloor(doc);

        Assert.assertFalse(changed);
        Assert.assertEquals(before, doc.asXML());
    }

    private static Document pluginPom(String compilerConfiguration) throws Exception {
        return namespacedPom(
            "<build><plugins><plugin>"
                + "<groupId>org.apache.maven.plugins</groupId>"
                + "<artifactId>maven-compiler-plugin</artifactId>"
                + "<configuration>"
                + compilerConfiguration
                + "</configuration>"
                + "</plugin></plugins></build>"
        );
    }

    private static Document propertiesPom(String properties) throws Exception {
        return namespacedPom("<properties>" + properties + "</properties>");
    }

    private static Document namespacedPom(String body) throws Exception {
        return DocumentHelper.parseText(
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>example</groupId>"
                + "<artifactId>subject</artifactId>"
                + "<version>1.0</version>"
                + body
                + "</project>"
        );
    }
}
