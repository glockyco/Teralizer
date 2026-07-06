package teralizer.processing.diagnostics;

import net.jqwik.api.Example;
import org.junit.Assert;

public class BuildEnvironmentObservationWriterTest {

    @Example
    void derivesMavenTestSourceFloorOutcomes() {
        Assert.assertEquals("APPLIED", BuildEnvironmentObservationWriter.mavenTestSourceFloorOutcome(
            pom("<properties><java.version>1.5</java.version></properties>"
                + compilerPlugin("<source>${java.version}</source><testSource>1.8</testSource><testTarget>1.8</testTarget>"))));

        Assert.assertEquals("SKIPPED_UNRESOLVABLE", BuildEnvironmentObservationWriter.mavenTestSourceFloorOutcome(
            pom(compilerPlugin("<source>${java.version}</source>"))));

        Assert.assertEquals("NOT_NEEDED", BuildEnvironmentObservationWriter.mavenTestSourceFloorOutcome(
            pom(compilerPlugin("<source>1.8</source><target>1.8</target>"))));
    }

    @Example
    void derivesGradleTestSourceFloorOutcomesWithoutUnresolvableSkip() {
        String teralizerFloor = "// Added by Teralizer - START.\n"
            + "tasks.matching { it.name == 'compileTestJava' }.all {\n"
            + "    if (JavaVersion.toVersion(sourceCompatibility) < JavaVersion.VERSION_1_8) {\n"
            + "        sourceCompatibility = '1.8'\n"
            + "    }\n"
            + "}\n"
            + "// Added by Teralizer - END.";
        Assert.assertEquals("APPLIED", BuildEnvironmentObservationWriter.gradleTestSourceFloorOutcome(teralizerFloor));
        Assert.assertEquals("NOT_NEEDED", BuildEnvironmentObservationWriter.gradleTestSourceFloorOutcome(
            "tasks.withType(JavaCompile) { sourceCompatibility = '1.8' }"));
    }

    @Example
    void derivesMavenSurefireFloorOutcomes() {
        Assert.assertEquals("APPLIED", BuildEnvironmentObservationWriter.mavenSurefireFloorOutcome(
            pom(surefirePlugin("2.17")),
            pom(surefirePlugin("2.22.2"))));

        Assert.assertEquals("SKIPPED_UNRESOLVABLE", BuildEnvironmentObservationWriter.mavenSurefireFloorOutcome(
            pom(surefirePlugin("${surefire.version}")),
            pom(surefirePlugin("${surefire.version}"))));

        Assert.assertEquals("NOT_NEEDED", BuildEnvironmentObservationWriter.mavenSurefireFloorOutcome(
            pom(surefirePlugin("3.0.2")),
            pom(surefirePlugin("3.0.2"))));
    }

    private static String compilerPlugin(String configuration) {
        return "<build><plugins><plugin>"
            + "<groupId>org.apache.maven.plugins</groupId>"
            + "<artifactId>maven-compiler-plugin</artifactId>"
            + "<configuration>" + configuration + "</configuration>"
            + "</plugin></plugins></build>";
    }

    private static String surefirePlugin(String version) {
        return "<build><plugins><plugin>"
            + "<groupId>org.apache.maven.plugins</groupId>"
            + "<artifactId>maven-surefire-plugin</artifactId>"
            + "<version>" + version + "</version>"
            + "</plugin></plugins></build>";
    }

    private static String pom(String body) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
            + "<modelVersion>4.0.0</modelVersion>"
            + body
            + "</project>";
    }
}
