package teralizer.processing;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ConfigIdentity {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ConfigIdentity() {
    }

    public static String hash(String rendered) {
        return sha256(renderIdentity(rendered));
    }

    public static String renderIdentity(String rendered) {
        return renderIdentity(ConfigFactory.parseString(rendered));
    }

    public static String renderIdentity(Config config) {
        // Stored full renders and current renders share this projection before hashing. The
        // phase toggles and pitest.enabled are run-scoped: a workspace is generated with the
        // reduction phase and PIT off, then resumed reduction-only with PIT forced on, so
        // neither may change the project identity.
        return config
            .withoutPath("project.use-test-generation")
            .withoutPath("project.use-test-generalization")
            .withoutPath("project.use-test-reduction")
            .withoutPath("pitest.enabled")
            .root()
            .render(ConfigRenderOptions.concise());
    }

    private static String sha256(String rendered) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rendered.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                hex[i * 2] = HEX[value >>> 4];
                hex[i * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
