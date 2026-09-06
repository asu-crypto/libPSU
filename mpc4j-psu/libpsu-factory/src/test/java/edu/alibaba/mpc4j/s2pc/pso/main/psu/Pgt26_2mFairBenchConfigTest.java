package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;

public class Pgt26_2mFairBenchConfigTest {
  @Test
  public void testFairBenchConfigsDoNotSkipProofs() throws IOException {
    Path root = Paths.get("..", "tests", "src", "test", "resources", "psu", "18_PGT26_2M");
    Assert.assertTrue("missing fair bench config dir: " + root.toAbsolutePath(), Files.isDirectory(root));
    try (Stream<Path> paths = Files.list(root)) {
      paths
          .filter(p -> p.getFileName().toString().startsWith("fair_bench"))
          .filter(p -> p.getFileName().toString().endsWith(".conf"))
          .forEach(this::assertFairConfigEnablesProofs);
    }
  }

  @Test
  public void testFairBenchGuardRejectsProofSkipping() {
    Properties p = new Properties();
    p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "EUROCRYPT_PuGaoTri26");
    p.setProperty("append_string", "fair_bench_2p5");
    p.setProperty("pgt26_2m_skip_shuffle_proof", "true");
    try {
      PsuConfigUtils.createConfig(p);
      Assert.fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("EUROCRYPT_PuGaoTri26 fair benchmark"));
    }
  }

  private void assertFairConfigEnablesProofs(Path confPath) {
    Properties properties = load(confPath);
    Assert.assertEquals("EUROCRYPT_PuGaoTri26", properties.getProperty(PsuConfigUtils.PSU_PTO_NAME_KEY));
    boolean skipShuffle = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_shuffle_proof", false);
    boolean skipRddh = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_rddh_proof", false);
    Assert.assertFalse("skip shuffle in " + confPath, skipShuffle);
    Assert.assertFalse("skip RDDH in " + confPath, skipRddh);
    PsuConfigUtils.createConfig(properties);
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
