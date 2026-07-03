package teralizer.processing.dependencies;

import net.jqwik.api.Example;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.junit.Assert;

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

    private static Document pluginPom(String pluginsOpen, String pluginsClose, String version, boolean includeGroupId) throws Exception {
        return namespacedPom(
            "<build>"
                + pluginsOpen
                + "<plugin>"
                + (includeGroupId ? "<groupId>org.apache.maven.plugins</groupId>" : "")
                + "<artifactId>maven-surefire-plugin</artifactId>"
                + "<version>" + version + "</version>"
                + "</plugin>"
                + pluginsClose
                + "</build>"
        );
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
