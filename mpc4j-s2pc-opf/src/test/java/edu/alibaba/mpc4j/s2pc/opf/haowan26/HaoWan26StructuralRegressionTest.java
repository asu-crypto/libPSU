package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Structural regression: HaoWan26 production sources must not contain RS21 / T-share leftovers.
 */
public class HaoWan26StructuralRegressionTest {
    private static final String[] FORBIDDEN = {
        "Rs21MpOprf",
        "MpOprfSenderOutput",
        "CLIENT_SEND_T_SHARES",
    };

    @Test
    public void testNoForbiddenTokensInMainSources() throws IOException {
        Path root = Path.of("src/main/java/edu/alibaba/mpc4j/s2pc/opf/haowan26");
        if (!Files.isDirectory(root)) {
            // Running from repo root via -pl
            root = Path.of("mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/haowan26");
        }
        Assert.assertTrue("haowan26 main sources missing: " + root.toAbsolutePath(), Files.isDirectory(root));
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    for (String token : FORBIDDEN) {
                        Assert.assertFalse(
                            path + " contains forbidden token " + token,
                            text.contains(token)
                        );
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
        }
    }
}
