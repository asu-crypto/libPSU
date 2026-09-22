package edu.alibaba.libpsu.cli;

import edu.alibaba.mpc4j.s2pc.pso.main.PsoMain;
import edu.alibaba.mpc4j.s2pc.upso.main.UpsoMain;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exercises command-line selection without creating RPC sessions or loading native protocol libraries.
 */
public class LibPsuMainTest {
    private static final LibPsuMain.ProtocolRunner NO_RUN = (family, properties, ownName) ->
        Assert.fail("Protocol execution was not expected");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void helpDoesNotLoadConfigsOrRunProtocols() throws Exception {
        for (String flag : List.of("--help", "-h")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Assert.assertFalse(LibPsuMain.run(new String[] {flag}, new PrintStream(output), NO_RUN));
            String help = output.toString(StandardCharsets.UTF_8);
            Assert.assertTrue(help.contains("<config-file-or-directory> <own-name>"));
            Assert.assertTrue(help.contains("PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12, UPSU"));
        }
    }

    @Test
    public void legacyEntrypointsDelegateHelp() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output));
            PsoMain.main(new String[] {"--help"});
            String psoHelp = output.toString(StandardCharsets.UTF_8);
            output.reset();
            UpsoMain.main(new String[] {"--help"});
            Assert.assertEquals(psoHelp, output.toString(StandardCharsets.UTF_8));
            Assert.assertTrue(psoHelp.contains("PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12, UPSU"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void rejectsInvalidArguments() {
        List<String[]> invalidArguments = Arrays.asList(
            null, new String[0], new String[] {"config.conf"},
            new String[] {"config.conf", "server", "extra"},
            new String[] {"", "server"}, new String[] {"config.conf", " "},
            new String[] {null, "server"}, new String[] {"config.conf", null},
            new String[] {"--unknown", "server"}
        );
        for (String[] args : invalidArguments) {
            IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> LibPsuMain.run(args, System.out, NO_RUN));
            Assert.assertTrue(error.getMessage().contains("Usage:"));
        }
    }

    @Test
    public void rejectsMissingPathsAndEmptyDirectories() throws Exception {
        Path directory = temporaryFolder.newFolder("empty").toPath();
        Assert.assertThrows(IllegalArgumentException.class,
            () -> LibPsuMain.run(new String[] {directory.resolve("missing.conf").toString(), "server"},
                System.out, NO_RUN));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> LibPsuMain.run(new String[] {directory.toString(), "server"}, System.out, NO_RUN));
    }

    @Test
    public void runsAllFamiliesFromOneDirectoryInSortedOrder() throws Exception {
        Path directory = temporaryFolder.newFolder("mixed").toPath();
        writeConfig(directory.resolve("f.conf"), "UPSU");
        writeConfig(directory.resolve("e.conf"), "BA12");
        writeConfig(directory.resolve("d/nested.conf"), "PSI");
        writeConfig(directory.resolve("c.conf"), "PSU_BLACK_IP");
        writeConfig(directory.resolve("b.conf"), "OO_PSU");
        writeConfig(directory.resolve("a.conf"), "PSU");
        writeConfig(directory.resolve("disabled.conf.disabled"), "UNSUPPORTED");
        writeConfig(directory.resolve("README.txt"), "UNSUPPORTED");
        writeConfig(directory.resolve("11_DGG25/disabled.conf"), "UNSUPPORTED");
        List<LibPsuMain.Family> families = new ArrayList<>();
        Assert.assertTrue(LibPsuMain.run(new String[] {directory.toString(), "custom-client"}, System.out,
            (family, properties, ownName) -> {
                families.add(family);
                Assert.assertEquals("custom-client", ownName);
                Assert.assertEquals("unchanged", properties.getProperty("driver_option"));
            }));
        Assert.assertEquals(Arrays.asList(LibPsuMain.Family.values()), families);
    }

    @Test
    public void explicitConfigKeepsPropertiesAndBypassesDirectoryExclusions() throws Exception {
        Path config = temporaryFolder.getRoot().toPath().resolve("11_DGG25/single.conf");
        writeConfig(config, "UPSU");
        List<LibPsuMain.Family> families = new ArrayList<>();
        LibPsuMain.run(new String[] {config.toString(), "server"}, System.out,
            (family, properties, ownName) -> {
                families.add(family);
                Assert.assertEquals("server", ownName);
                Assert.assertEquals("unchanged", properties.getProperty("driver_option"));
            });
        Assert.assertEquals(List.of(LibPsuMain.Family.UPSU), families);
    }

    @Test
    public void acceptsBa12PaperLabelInExistingBenchmarkConfigs() throws Exception {
        Path config = temporaryFolder.newFile("ba12.conf").toPath();
        writeConfig(config, "ASIACCS:BlaAgu12");
        List<LibPsuMain.Family> families = new ArrayList<>();
        LibPsuMain.run(new String[] {config.toString(), "server"}, System.out,
            (family, properties, ownName) -> {
                families.add(family);
                Assert.assertEquals("ASIACCS:BlaAgu12", properties.getProperty("pto_type"));
            });
        Assert.assertEquals(List.of(LibPsuMain.Family.BA12), families);
    }

    @Test
    public void rejectsUnknownOrMissingFamilyBeforeExecution() throws Exception {
        Path config = temporaryFolder.newFile("invalid.conf").toPath();
        for (String family : List.of("UNSUPPORTED", "")) {
            writeConfig(config, family);
            IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> LibPsuMain.run(new String[] {config.toString(), "server"}, System.out, NO_RUN));
            Assert.assertTrue(error.getMessage().contains("Invalid pto_type"));
            Assert.assertTrue(error.getMessage().contains(config.toString()));
        }
    }

    @Test
    public void rejectsUnknownPartyBeforeExecution() throws Exception {
        Path config = temporaryFolder.newFile("party.conf").toPath();
        writeConfig(config, "PSU");
        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
            () -> LibPsuMain.run(new String[] {config.toString(), "unknown"}, System.out, NO_RUN));
        Assert.assertTrue(error.getMessage().contains("must match server_name or client_name"));
    }

    private static void writeConfig(Path config, String family) throws Exception {
        Files.createDirectories(config.getParent());
        Files.writeString(config, "pto_type=" + family + "\nserver_name=server\nclient_name=custom-client\n"
            + "driver_option=unchanged\n", StandardCharsets.ISO_8859_1);
    }
}
