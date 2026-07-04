package teralizer.spoon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.compiler.VirtualFile;
import teralizer.domain.MethodParameter;
import teralizer.spoon.generalization.TestParametersFactory;

public class SpoonUtilsTypeReferenceTest {

    @Example
    void createReferenceCharacterizationKeepsSimpleBoxedNamesUnqualified() {
        Factory factory = new Launcher().getFactory();

        Assert.assertEquals("int", factory.Type().createReference("int").getQualifiedName());
        Assert.assertEquals("Long", factory.Type().createReference("Long").getQualifiedName());
        Assert.assertEquals("java.lang.Long", factory.Type().createReference("java.lang.Long").getQualifiedName());
        Assert.assertEquals("java.lang.String", factory.Type().createReference("java.lang.String").getQualifiedName());
        Assert.assertEquals("java.util.List", factory.Type().createReference("java.util.List").getQualifiedName());
        Assert.assertEquals("com.external.MissingType", factory.Type().createReference("com.external.MissingType").getQualifiedName());
    }

    @Example
    void getTypeReferenceKeepsPrimitiveAndBoxedSwitchContracts() {
        Factory factory = new Launcher().getFactory();

        for (TypeCase typeCase : switchTypeCases()) {
            CtTypeReference<?> reference = SpoonUtils.getTypeReference(factory, typeCase.input);

            Assert.assertNotNull(typeCase.input, reference);
            Assert.assertEquals(typeCase.input, typeCase.qualifiedName, reference.getQualifiedName());
        }
    }

    @Example
    void getTypeReferenceCreatesReferencesForShadowJdkAndExternalTypes() {
        Factory factory = new Launcher().getFactory();

        assertCreatedReference(factory, "java.lang.String");
        assertCreatedReference(factory, "java.util.List");
        assertCreatedReference(factory, "com.external.MissingType");
    }

    @Example
    void getTypeReferenceReturnsModelTypeReferences() {
        Factory factory = factoryWithModelType();

        CtTypeReference<?> reference = SpoonUtils.getTypeReference(factory, "com.example.ModelType");

        Assert.assertNotNull(reference);
        Assert.assertEquals("com.example.ModelType", reference.getQualifiedName());
    }

    @Example
    void createParametersClassRendersStringTypedConstructorParameter() {
        CtClass<?> parametersClass = TestParametersFactory.createParametersClass(
            new Launcher().getFactory(),
            Collections.singletonList(new MethodParameter("java.lang.String", "text"))
        );

        String rendered = parametersClass.toString();

        Assert.assertTrue(rendered, rendered.contains("public java.lang.String text;"));
        Assert.assertTrue(rendered, rendered.contains("public TestParameters(java.lang.String text)"));
    }

    private static void assertCreatedReference(Factory factory, String typeName) {
        CtTypeReference<?> reference = SpoonUtils.getTypeReference(factory, typeName);

        Assert.assertNotNull(typeName, reference);
        Assert.assertEquals(typeName, reference.getQualifiedName());
    }

    private static Factory factoryWithModelType() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(
            "package com.example; public class ModelType {}",
            "src/main/java/com/example/ModelType.java"
        ));
        launcher.buildModel();
        return launcher.getFactory();
    }

    private static List<TypeCase> switchTypeCases() {
        return Arrays.asList(
            new TypeCase("boolean", "boolean"),
            new TypeCase("java.lang.Boolean", "java.lang.Boolean"),
            new TypeCase("Boolean", "java.lang.Boolean"),
            new TypeCase("byte", "byte"),
            new TypeCase("java.lang.Byte", "java.lang.Byte"),
            new TypeCase("Byte", "java.lang.Byte"),
            new TypeCase("char", "char"),
            new TypeCase("java.lang.Character", "java.lang.Character"),
            new TypeCase("Character", "java.lang.Character"),
            new TypeCase("double", "double"),
            new TypeCase("java.lang.Double", "java.lang.Double"),
            new TypeCase("Double", "java.lang.Double"),
            new TypeCase("float", "float"),
            new TypeCase("java.lang.Float", "java.lang.Float"),
            new TypeCase("Float", "java.lang.Float"),
            new TypeCase("int", "int"),
            new TypeCase("java.lang.Integer", "java.lang.Integer"),
            new TypeCase("Integer", "java.lang.Integer"),
            new TypeCase("long", "long"),
            new TypeCase("java.lang.Long", "java.lang.Long"),
            new TypeCase("Long", "java.lang.Long"),
            new TypeCase("short", "short"),
            new TypeCase("java.lang.Short", "java.lang.Short"),
            new TypeCase("Short", "java.lang.Short"),
            new TypeCase("void", "void"),
            new TypeCase("java.lang.Void", "java.lang.Void"),
            new TypeCase("Void", "java.lang.Void")
        );
    }

    private static final class TypeCase {
        private final String input;
        private final String qualifiedName;

        private TypeCase(String input, String qualifiedName) {
            this.input = input;
            this.qualifiedName = qualifiedName;
        }
    }
}
