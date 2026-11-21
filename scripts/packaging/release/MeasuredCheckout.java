import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;

/**
 * Streams each requested revision as the pipeline's checkout writes it.
 *
 * <p>The real-world projects were cloned by the pipeline's ProjectDownloadTask with this JGit
 * version on a host whose Git configuration set core.autocrlf=true and the Git LFS smudge filter.
 * This program applies the same configuration and JGit's own checkout conversion to every file of
 * a revision and writes the results to standard output instead of a working tree, so that the
 * caller decides how to lay them out on any file system.
 *
 * <p>Standard input holds one {@code repository<TAB>revision} line per project. For every file,
 * the output holds the byte 'E', the Git mode, the raw path, the blob id and the converted
 * content, each length-prefixed. Each project ends with the byte 'P'.
 */
public final class MeasuredCheckout {
    private MeasuredCheckout() {}

    public static void main(String[] args) throws Exception {
        BufferedReader input =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        DataOutputStream output = new DataOutputStream(System.out);
        String line;
        while ((line = input.readLine()) != null) {
            String[] fields = line.split("\t", 2);
            stream(new File(fields[0]), fields[1], output);
            output.writeByte('P');
            output.flush();
        }
    }

    private static void stream(File repository, String revision, DataOutputStream output)
            throws Exception {
        try (Repository repo =
                        new FileRepositoryBuilder().setGitDir(new File(repository, ".git")).build();
                RevWalk revisions = new RevWalk(repo);
                TreeWalk walk = new TreeWalk(repo)) {
            // The evaluation host's user configuration. The configuration is changed in memory
            // only and never saved to the repository.
            StoredConfig config = repo.getConfig();
            config.setBoolean("core", null, "autocrlf", true);
            config.setString("filter", "lfs", "smudge", "git-lfs smudge -- %f");
            config.setBoolean("filter", "lfs", "required", true);
            WorkingTreeOptions options = config.get(WorkingTreeOptions.KEY);

            RevCommit commit = revisions.parseCommit(repo.resolve(revision));
            walk.setOperationType(OperationType.CHECKOUT_OP);
            walk.addTree(commit.getTree());
            walk.setRecursive(true);
            while (walk.next()) {
                FileMode mode = walk.getFileMode(0);
                if (mode == FileMode.GITLINK) {
                    continue;
                }
                ObjectLoader blob = repo.open(walk.getObjectId(0));
                byte[] content;
                if (mode == FileMode.SYMLINK) {
                    // JGit creates a link through java.nio, which normalizes the target and
                    // drops a trailing slash.
                    String target = RawParseUtils.decode(blob.getBytes());
                    content = Paths.get(target).toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    CheckoutMetadata metadata =
                            new CheckoutMetadata(
                                    walk.getEolStreamType(OperationType.CHECKOUT_OP),
                                    walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_SMUDGE));
                    ByteArrayOutputStream converted = new ByteArrayOutputStream();
                    DirCacheCheckout.getContent(
                            repo, walk.getPathString(), metadata, blob, options, converted);
                    content = converted.toByteArray();
                }
                output.writeByte('E');
                output.writeInt(mode.getBits());
                byte[] path = walk.getRawPath();
                output.writeInt(path.length);
                output.write(path);
                walk.getObjectId(0).copyRawTo((OutputStream) output);
                output.writeLong(content.length);
                output.write(content);
            }
        }
    }
}
