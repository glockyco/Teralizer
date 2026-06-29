package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class JqwikValueLogEvidenceTest {

    @Example
    void missingLogHasNoEvidence() throws Exception {
        Path path = Files.createTempDirectory("jqwik-evidence").resolve("missing.tsv");

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertFalse(evidence.isReadable());
        Assert.assertNull(evidence.getDistinctNewTuples());
    }

    @Example
    void emptyLogHasNoEvidence() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertFalse(evidence.isReadable());
        Assert.assertNull(evidence.getDistinctNewTuples());
    }

    @Example
    void seedOnlyCountsZeroNewTuples() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(0), evidence.getDistinctNewTuples());
    }

    @Example
    void duplicateSeedStillCountsZeroNewTuples() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000", "ch=\\u0000", "ch=\\u0000"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(0), evidence.getDistinctNewTuples());
    }

    @Example
    void oneDistinctTupleBeyondSeedCountsOne() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList("ch=\\u0000", "ch=\\u0001", "ch=\\u0001"), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(1), evidence.getDistinctNewTuples());
    }

    @Example
    void multiParameterRowsUseFullTupleIdentity() throws Exception {
        Path path = Files.createTempFile("jqwik-evidence", ".tsv");
        Files.write(path, Arrays.asList(
            "a=1\tb=2",
            "a=1\tb=3",
            "a=1\tb=3",
            "a=2\tb=2"
        ), StandardCharsets.UTF_8);

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(path);

        Assert.assertTrue(evidence.isReadable());
        Assert.assertEquals(Integer.valueOf(2), evidence.getDistinctNewTuples());
    }
}
