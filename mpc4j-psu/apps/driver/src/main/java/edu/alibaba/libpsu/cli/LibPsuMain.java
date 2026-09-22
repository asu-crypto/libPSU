package edu.alibaba.libpsu.cli;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.rpc.main.MainTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.ba12.Ba12Main;
import edu.alibaba.mpc4j.s2pc.pso.main.psi.PsiMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.OoPsuMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuBlackIpMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain;
import edu.alibaba.mpc4j.s2pc.upso.main.upsu.UpsuMain;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Single command-line entry point for all libPSU protocol families.
 */
public final class LibPsuMain {
    private static final String USAGE = "Usage: java -jar mpc4j-psu-driver-<version>-jar-with-dependencies.jar"
        + " <config-file-or-directory> <own-name>\n"
        + "       java -jar mpc4j-psu-driver-<version>-jar-with-dependencies.jar --help\n"
        + "Families (pto_type): PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12, UPSU\n"
        + "BA12 also accepts the paper label ASIACCS:BlaAgu12.\n"
        + "own-name must match server_name or client_name in each config.\n"
        + "Directories run enabled .conf files recursively in sorted path order.";

    private LibPsuMain() {
        // utility class
    }

    public static void main(String[] args) throws Exception {
        try {
            if (run(args, System.out, LibPsuMain::runProtocol)) {
                // The benchmark RPC implementation may leave Netty threads running.
                System.exit(0);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("libPSU: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Loads configs independently of the network runner, so CLI dispatch can be verified without native libraries.
     *
     * @return whether protocols ran (false for help).
     */
    static boolean run(String[] args, PrintStream out, ProtocolRunner runner) throws Exception {
        if (args != null && args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            out.println(USAGE);
            return false;
        }
        if (args == null || args.length != 2 || args[0] == null || args[0].isBlank()
            || args[1] == null || args[1].isBlank() || args[0].startsWith("-")) {
            throw new IllegalArgumentException("Expected a config path and party name.\n" + USAGE);
        }
        List<Path> configs = collectConfigs(Path.of(args[0]));
        PropertiesUtils.loadLog4jProperties();
        for (Path config : configs) {
            Properties properties = PropertiesUtils.loadProperties(config.toString());
            String type = properties.getProperty(MainPtoConfigUtils.PTO_TYPE_KEY, "").trim();
            Family family;
            try {
                family = "ASIACCS:BlaAgu12".equals(type) ? Family.BA12 : Family.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid pto_type '" + type + "' in " + config
                    + ". Supported families: PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12, UPSU", e);
            }
            if (!args[1].equals(properties.getProperty("server_name", "").trim())
                && !args[1].equals(properties.getProperty("client_name", "").trim())) {
                throw new IllegalArgumentException("Party name '" + args[1]
                    + "' must match server_name or client_name in " + config);
            }
            runner.run(family, properties, args[1]);
        }
        return true;
    }

    private static List<Path> collectConfigs(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        if (!Files.isDirectory(input)) {
            throw new IllegalArgumentException("Config path does not exist or is not a file/directory: " + input);
        }
        List<Path> configs;
        try (Stream<Path> paths = Files.walk(input)) {
            configs = paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".conf"))
                // Preserve the fair-suite exclusion used by the original PSU driver.
                .filter(path -> !path.toString().contains("11_DGG25"))
                .sorted()
                .collect(Collectors.toList());
        }
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("No enabled .conf files found in: " + input);
        }
        return configs;
    }

    private static void runProtocol(Family family, Properties properties, String ownName) throws Exception {
        MainTwoPartyPto driver;
        switch (family) {
            case PSU:
                driver = new PsuMain(properties, ownName);
                break;
            case OO_PSU:
                driver = new OoPsuMain(properties, ownName);
                break;
            case PSU_BLACK_IP:
                driver = new PsuBlackIpMain(properties, ownName);
                break;
            case PSI:
                driver = new PsiMain(properties, ownName);
                break;
            case BA12:
                driver = new Ba12Main(properties, ownName);
                break;
            case UPSU:
                driver = new UpsuMain(properties, ownName);
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol family: " + family);
        }
        driver.runNetty();
    }

    enum Family {
        PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12, UPSU
    }

    @FunctionalInterface
    interface ProtocolRunner {
        void run(Family family, Properties properties, String ownName) throws Exception;
    }
}
