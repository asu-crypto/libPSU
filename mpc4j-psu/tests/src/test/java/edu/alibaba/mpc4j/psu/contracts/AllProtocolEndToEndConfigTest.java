package edu.alibaba.mpc4j.psu.contracts;

import edu.alibaba.libpsu.api.ProtocolFunctionality;
import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.libpsu.api.ProtocolMetadataRegistry;
import edu.alibaba.libpsu.factory.ProtocolRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** End-to-end deployment contract: every protocol ships a small runnable benchmark configuration. */
public class AllProtocolEndToEndConfigTest {
    @Test
    public void testEveryProtocolHasAParseableSmallEndToEndConfig() throws IOException {
        Path configRoot = Path.of("..", "bench", "configs").normalize();
        Assert.assertTrue("missing benchmark config root: " + configRoot.toAbsolutePath(), Files.isDirectory(configRoot));
        List<Path> configs;
        try (Stream<Path> paths = Files.walk(configRoot)) {
            configs = paths.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.equals("fair_bench_2p4.conf")
                        || name.equals("fair_bench_unbalanced_2p4x2p4.conf");
                })
                .filter(path -> !path.toString().contains("_archive"))
                .collect(Collectors.toList());
        }
        Assert.assertFalse("no end-to-end configs found", configs.isEmpty());

        for (ProtocolInfo info : ProtocolMetadataRegistry.allEntries()) {
            Path config = findConfig(info, configs);
            Assert.assertNotNull("No 2^4 end-to-end config for " + key(info), config);
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(config)) {
                properties.load(reader);
            }
            Assert.assertEquals(info.getProtocolName(), properties.getProperty(info.getConfigPropertyName()));
            if (info.getFunctionality() == ProtocolFunctionality.PSU) {
                Assert.assertEquals(info.getProtocolName(), ProtocolRegistry.createPsuConfig(properties).getPtoType().protocolId());
            }
        }
    }

    private static Path findConfig(ProtocolInfo info, List<Path> configs) throws IOException {
        for (Path path : configs) {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            if (info.getProtocolName().equals(properties.getProperty(info.getConfigPropertyName()))) {
                return path;
            }
        }
        return null;
    }

    private static String key(ProtocolInfo info) {
        return info.getProtocolName() + " (" + info.getFunctionality() + ")";
    }
}
