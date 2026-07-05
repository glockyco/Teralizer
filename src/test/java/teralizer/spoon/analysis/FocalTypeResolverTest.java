package teralizer.spoon.analysis;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.support.compiler.VirtualFile;

public class FocalTypeResolverTest {

    @Example
    void pathMirror_helper() {
        Assert.assertEquals("src/main/java/com/x/Foo.java",
            FocalTypeResolver.mirrorTestPath("src/test/java/com/x/FooTest.java"));
        Assert.assertEquals("src/main/java/com/x/Foo.java",
            FocalTypeResolver.mirrorTestPath("src/test/java/com/x/TestFoo.java"));
        Assert.assertNull(FocalTypeResolver.mirrorTestPath("src/test/java/com/x/Helper.java"));
        Assert.assertNull(FocalTypeResolver.mirrorTestPath("src/main/java/com/x/Foo.java"));
    }

    @Example
    void nameDerivedFocalReportsSourceAndQualifiedName() {
        FocalTypeResolver.Focal focal = resolveFocal(
            "public class SubjectTest {"
            + "  public void t() { org.junit.Assert.assertEquals(0, new Subject().getTotal()); }"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        Assert.assertEquals("Subject", focal.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, focal.source);
    }

    @Example
    void prefersMatchingPackageBeforeModelOrderFallback() {
        CtModel model = modelOf(
            "package a; public class Duplicate { public int value() { return 1; } }",
            "package b; public class Duplicate { public int value() { return 2; } }",
            "package a; public class DuplicateTest {"
            + "  public void t() { org.junit.Assert.assertEquals(1, new Duplicate().value()); }"
            + "}",
            "package c; public class DuplicateTest {"
            + "  public void t() { org.junit.Assert.assertEquals(1, new a.Duplicate().value()); }"
            + "}");
        FocalTypeResolver resolver = new FocalTypeResolver();

        FocalTypeResolver.Focal preferredPackage = resolver.resolveFocalType(testMethod(model, "a.DuplicateTest", "t"));
        Assert.assertEquals("a.Duplicate", preferredPackage.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, preferredPackage.source);

        FocalTypeResolver.Focal modelOrderFallback = resolver.resolveFocalType(testMethod(model, "c.DuplicateTest", "t"));
        Assert.assertEquals("a.Duplicate", modelOrderFallback.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, modelOrderFallback.source);
    }

    @Example
    void repeatedAssertionsShareFocalWhileDifferentTestClassesStayIsolated() {
        CtModel model = modelOf(
            "package a; public class Alpha { public int first() { return 1; }"
            + "  public int second() { return 2; } }",
            "package b; public class Beta { public int first() { return 1; }"
            + "  public int second() { return 2; } }",
            "package a; public class AlphaTest { public void t() {"
            + "  Alpha subject = new Alpha();"
            + "  org.junit.Assert.assertEquals(1, subject.first());"
            + "  org.junit.Assert.assertEquals(2, subject.second());"
            + "} }",
            "package b; public class BetaTest { public void t() {"
            + "  Beta subject = new Beta();"
            + "  org.junit.Assert.assertEquals(1, subject.first());"
            + "  org.junit.Assert.assertEquals(2, subject.second());"
            + "} }");
        FocalTypeResolver resolver = new FocalTypeResolver();

        FocalTypeResolver.Focal alphaFirst = resolver.resolveFocalType(testMethod(model, "a.AlphaTest", "t"));
        FocalTypeResolver.Focal alphaSecond = resolver.resolveFocalType(testMethod(model, "a.AlphaTest", "t"));
        Assert.assertEquals(alphaFirst.qualifiedName, alphaSecond.qualifiedName);
        Assert.assertEquals(alphaFirst.source, alphaSecond.source);
        Assert.assertEquals("a.Alpha", alphaFirst.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, alphaFirst.source);

        FocalTypeResolver.Focal betaFirst = resolver.resolveFocalType(testMethod(model, "b.BetaTest", "t"));
        FocalTypeResolver.Focal betaSecond = resolver.resolveFocalType(testMethod(model, "b.BetaTest", "t"));
        Assert.assertEquals(betaFirst.qualifiedName, betaSecond.qualifiedName);
        Assert.assertEquals(betaFirst.source, betaSecond.source);
        Assert.assertEquals("b.Beta", betaFirst.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, betaFirst.source);
    }

    /**
     * Virtual-file Spoon models do not provide real files on disk, so focal resolution cannot
     * exercise the path-derived arm in this unit suite; corpus identity checks cover that arm.
     */
    @Example
    void virtualFileFocalResolutionReportsNameOnlyBecausePathMirrorIsUnavailable() {
        FocalTypeResolver.Focal focal = resolveFocal(
            "public class SubjectTest {"
            + "  public void t() { org.junit.Assert.assertEquals(0, new Subject().getTotal()); }"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        Assert.assertEquals("Subject", focal.qualifiedName);
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, focal.source);
    }

    private static FocalTypeResolver.Focal resolveFocal(String testSource, String... otherSources) {
        CtModel model = modelOf(concat(testSource, otherSources));
        return new FocalTypeResolver().resolveFocalType(testMethod(model, "SubjectTest", "t"));
    }

    private static CtModel modelOf(String... sources) {
        Launcher launcher = new Launcher();
        for (int i = 0; i < sources.length; i++) {
            launcher.addInputResource(new VirtualFile(sources[i], "Source" + i + ".java"));
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    private static CtMethod<?> testMethod(CtModel model, String testClassName, String testMethodName) {
        CtClass<?> testClass = model.getElements(CtClass.class::isInstance).stream()
            .map(CtClass.class::cast)
            .filter(type -> testClassName.equals(type.getQualifiedName()))
            .findFirst()
            .get();
        return testClass.getMethodsByName(testMethodName).get(0);
    }

    private static String[] concat(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}
