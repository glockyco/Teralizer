package teralizer.processing.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jooq.generated.tables.records.ProjectRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneralizedSourceRestoreTaskTest {

    @Test
    void derivesArchivePathFromProjectDataProjectIdAndVariant(@TempDir Path workDir) {
        ProjectRecord project = project(workDir.resolve("project"), workDir.resolve("project/src/test/java"), workDir.resolve("data"));

        Path archive = GeneralizedSourceRestoreTask.generalizedSourceArchivePath(project, 7L, "variant-a");

        assertEquals(workDir.resolve("data/project-id-7/generalized-sources/variant-a"), archive);
    }

    @Test
    void archivesAndRestoresGeneralizedSourcesAtIdenticalRelativePaths(@TempDir Path workDir) throws Exception {
        Path projectRoot = workDir.resolve("project");
        Path testSourceRoot = projectRoot.resolve("src/test/java");
        ProjectRecord project = project(projectRoot, testSourceRoot, workDir.resolve("data"));
        Path packageDir = testSourceRoot.resolve("com/example");
        Path nestedPackageDir = packageDir.resolve("nested");
        Path firstGeneralized = packageDir.resolve("_Calculator_Generalized_variant_a_Test.java");
        Path secondGeneralized = nestedPackageDir.resolve("_Parser_Generalized_variant_a_Test.java");
        Path ordinaryTest = packageDir.resolve("CalculatorTest.java");
        Path notCleanupGeneralized = packageDir.resolve("Calculator_Generalized_variant_a_Test.java");
        write(firstGeneralized, "package com.example; class _Calculator_Generalized_variant_a_Test {}\n");
        write(secondGeneralized, "package com.example.nested; class _Parser_Generalized_variant_a_Test {}\n");
        write(ordinaryTest, "package com.example; class CalculatorTest {}\n");
        write(notCleanupGeneralized, "package com.example; class Calculator_Generalized_variant_a_Test {}\n");

        GeneralizedSourceRestoreTask.archiveGeneralizedSources(project, 7L, "variant-a");
        Path archive = GeneralizedSourceRestoreTask.generalizedSourceArchivePath(project, 7L, "variant-a");

        assertEquals(read(firstGeneralized), read(archive.resolve("com/example/_Calculator_Generalized_variant_a_Test.java")));
        assertEquals(read(secondGeneralized), read(archive.resolve("com/example/nested/_Parser_Generalized_variant_a_Test.java")));
        assertFalse(Files.exists(archive.resolve("com/example/CalculatorTest.java")));
        assertFalse(Files.exists(archive.resolve("com/example/Calculator_Generalized_variant_a_Test.java")));

        Files.delete(firstGeneralized);
        Files.delete(secondGeneralized);
        Path staleGeneralized = packageDir.resolve("_Stale_Generalized_old_Test.java");
        write(staleGeneralized, "package com.example; class _Stale_Generalized_old_Test {}\n");

        GeneralizedSourceRestoreTask.restoreGeneralizedSources(project, 7L, "variant-a");

        assertEquals(read(archive.resolve("com/example/_Calculator_Generalized_variant_a_Test.java")), read(firstGeneralized));
        assertEquals(read(archive.resolve("com/example/nested/_Parser_Generalized_variant_a_Test.java")), read(secondGeneralized));
        assertTrue(Files.exists(ordinaryTest));
        assertTrue(Files.exists(notCleanupGeneralized));
        assertFalse(Files.exists(staleGeneralized));
    }

    @Test
    void deletesAllGeneralizedSourcesFromProjectTestTree(@TempDir Path workDir) throws Exception {
        Path projectRoot = workDir.resolve("project");
        Path testSourceRoot = projectRoot.resolve("src/test/java");
        ProjectRecord project = project(projectRoot, testSourceRoot, workDir.resolve("data"));
        Path packageDir = testSourceRoot.resolve("com/example");
        Path generalized = packageDir.resolve("_Calculator_Generalized_variant_a_Test.java");
        Path nestedGeneralized = packageDir.resolve("nested/_Parser_Generalized_variant_b_Test.java");
        Path ordinaryTest = packageDir.resolve("CalculatorTest.java");
        Path notCleanupGeneralized = packageDir.resolve("Calculator_Generalized_variant_a_Test.java");
        write(generalized, "package com.example; class _Calculator_Generalized_variant_a_Test {}\n");
        write(nestedGeneralized, "package com.example.nested; class _Parser_Generalized_variant_b_Test {}\n");
        write(ordinaryTest, "package com.example; class CalculatorTest {}\n");
        write(notCleanupGeneralized, "package com.example; class Calculator_Generalized_variant_a_Test {}\n");

        GeneralizedSourceRestoreTask.deleteAllGeneralizedSources(project);

        assertFalse(Files.exists(generalized));
        assertFalse(Files.exists(nestedGeneralized));
        assertTrue(Files.exists(ordinaryTest));
        assertTrue(Files.exists(notCleanupGeneralized));
    }

    private static ProjectRecord project(Path projectRoot, Path testSourceRoot, Path dataRoot) {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setRootPath(projectRoot);
        project.setTestSourcePath(testSourceRoot);
        project.setDataPath(dataRoot);
        return project;
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
