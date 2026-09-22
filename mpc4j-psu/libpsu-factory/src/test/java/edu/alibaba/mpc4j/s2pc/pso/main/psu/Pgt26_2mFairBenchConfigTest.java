package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import edu.alibaba.libpsu.api.ProtocolNames;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fair-bench configs under {@code mpc4j-psu/bench/configs/psu/18_PGT26_2M} must keep proofs enabled.
 */
public class Pgt26_2mFairBenchConfigTest {
  @Test
  public void testFairBenchConfigsDoNotSkipProofs() throws IOException {
    Path root = resolveFairBenchConfigDir();
    Assert.assertTrue("missing fair bench config dir: " + root, Files.isDirectory(root));
    List<Path> confs;
    try (Stream<Path> paths = Files.list(root)) {
      confs = paths
          .filter(p -> p.getFileName().toString().startsWith("fair_bench"))
          .filter(p -> p.getFileName().toString().endsWith(".conf"))
          .sorted()
          .collect(Collectors.toList());
    }
    Assert.assertFalse(
        "expected at least one fair_bench*.conf under " + root + " but found none",
        confs.isEmpty()
    );
    for (Path conf : confs) {
      assertFairConfigEnablesProofs(conf);
    }
  }

  @Test
  public void testFactoryRejectsProofBypassProperties() {
    Properties p = new Properties();
    p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "EUROCRYPT_PuGaoTri26");
    p.setProperty("pgt26_2m_skip_shuffle_proof", "true");
    try {
      PsuConfigUtils.createConfig(p);
      Assert.fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("proof-bypass"));
    }
  }

  private void assertFairConfigEnablesProofs(Path confPath) {
    Properties properties = load(confPath);
    String ptoName = properties.getProperty(PsuConfigUtils.PSU_PTO_NAME_KEY);
    Assert.assertEquals(
        ProtocolNames.EUROCRYPT_PU_GAO_TRI26,
        ProtocolNames.canonicalize(ptoName)
    );
    boolean skipShuffle = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_shuffle_proof", false);
    boolean skipRddh = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_rddh_proof", false);
    Assert.assertFalse("skip shuffle in " + confPath, skipShuffle);
    Assert.assertFalse("skip RDDH in " + confPath, skipRddh);
    PsuConfig config = PsuConfigUtils.createConfig(properties);
    Assert.assertTrue(config instanceof Pgt26_2mPsuConfig);
  }

  private static Path resolveFairBenchConfigDir() {
    List<Path> candidates = new ArrayList<>();
    Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    candidates.add(userDir.resolve(Paths.get("mpc4j-psu", "bench", "configs", "psu", "18_PGT26_2M")));
    candidates.add(userDir.resolve(Paths.get("bench", "configs", "psu", "18_PGT26_2M")));
    candidates.add(userDir.resolve(Paths.get("..", "bench", "configs", "psu", "18_PGT26_2M")).normalize());
    candidates.add(userDir.resolve(Paths.get("..", "..", "bench", "configs", "psu", "18_PGT26_2M")).normalize());
    candidates.add(userDir.resolve(Paths.get("..", "bench", "configs", "psu", "18_PGT26_2M")).normalize());
    for (Path c : candidates) {
      if (Files.isDirectory(c)) {
        return c;
      }
    }
    Assert.fail(
        "cannot locate mpc4j-psu/bench/configs/psu/18_PGT26_2M from user.dir=" + userDir
            + "; tried " + candidates
    );
    return null;
  }

  private static Properties load(Path path) {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      properties.load(in);
    } catch (IOException e) {
      throw new AssertionError("cannot read " + path, e);
    }
    return properties;
  }
}
