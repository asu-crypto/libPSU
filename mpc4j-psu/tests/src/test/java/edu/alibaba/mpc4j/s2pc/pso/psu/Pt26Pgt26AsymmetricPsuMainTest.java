package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Small asymmetric PsuMain smoke for PT26 and PGT26-2M (server-first init through the driver).
 * <p>
 * Sizes: {@code server_log_set_size=3,2} with {@code client_log_set_size=2,3}
 * (8×4 then 4×8). Input files are generated before party threads start.
 * </p>
 */
public class Pt26Pgt26AsymmetricPsuMainTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    private static final int SIZE_8 = 1 << 3;
    private static final int SIZE_4 = 1 << 2;

    public Pt26Pgt26AsymmetricPsuMainTest() {
        super("PT26_PGT26_ASYMMETRIC_PSU_MAIN");
    }

    @Test
    public void pt26AsymmetricPsuMain() throws Exception {
        runAsymmetricMain("PT26", "pt26");
    }

    @Test
    public void pgt26_2mAsymmetricPsuMain() throws Exception {
        runAsymmetricMain("PGT26_2M", "pgt26_2m");
    }

    private void runAsymmetricMain(String psuPtoName, String label) throws Exception {
        File targetDir = new File("target").getAbsoluteFile();
        if (!targetDir.exists()) {
            Assert.assertTrue("mkdir target/", targetDir.mkdirs());
        }
        File saveDir = Files.createTempDirectory(targetDir.toPath(), "psu-main-" + label + "-").toFile();
        Assert.assertTrue(saveDir.isDirectory());

        File tempDir = new File(MainPtoConfigUtils.getFileFolderName());
        if (!tempDir.exists()) {
            Assert.assertTrue("mkdir temp/", tempDir.mkdirs());
        }
        // Generate both asymmetric layouts before starting party threads.
        PsuBenchmarkUtils.generateBytesInputFiles(SIZE_8, SIZE_4, ELEMENT_BYTE_LENGTH);
        PsuBenchmarkUtils.generateBytesInputFiles(SIZE_4, SIZE_8, ELEMENT_BYTE_LENGTH);
        Assert.assertTrue(new File(PsuBenchmarkUtils.getBytesFileName(
            PsuBenchmarkUtils.BYTES_SERVER_PREFIX, SIZE_8, ELEMENT_BYTE_LENGTH)).isFile());
        Assert.assertTrue(new File(PsuBenchmarkUtils.getBytesFileName(
            PsuBenchmarkUtils.BYTES_CLIENT_PREFIX, SIZE_4, ELEMENT_BYTE_LENGTH)).isFile());
        Assert.assertTrue(new File(PsuBenchmarkUtils.getBytesFileName(
            PsuBenchmarkUtils.BYTES_SERVER_PREFIX, SIZE_4, ELEMENT_BYTE_LENGTH)).isFile());
        Assert.assertTrue(new File(PsuBenchmarkUtils.getBytesFileName(
            PsuBenchmarkUtils.BYTES_CLIENT_PREFIX, SIZE_8, ELEMENT_BYTE_LENGTH)).isFile());

        Properties properties = baseProperties(psuPtoName, saveDir.getAbsolutePath());
        PsuMain serverMain = new PsuMain(properties, "server");
        PsuMain clientMain = new PsuMain(properties, "client");
        runMain(serverMain, clientMain);

        File[] outputs = saveDir.listFiles((dir, name) -> name.endsWith(".output"));
        Assert.assertNotNull(outputs);
        Assert.assertTrue("expected PsuMain .output under " + saveDir, outputs.length >= 2);
    }

    private static Properties baseProperties(String psuPtoName, String savePath) {
        Properties p = new Properties();
        p.setProperty("server_name", "server");
        p.setProperty("server_ip", "127.0.0.1");
        p.setProperty("server_port", "19102");
        p.setProperty("client_name", "client");
        p.setProperty("client_ip", "127.0.0.1");
        p.setProperty("client_port", "19103");
        p.setProperty("append_string", "asym_smoke");
        p.setProperty("save_path", savePath);
        p.setProperty(MainPtoConfigUtils.PTO_TYPE_KEY, PsuMain.PTO_TYPE_NAME);
        p.setProperty("element_byte_length", Integer.toString(ELEMENT_BYTE_LENGTH));
        // 8×4 then reverse 4×8 in one driver run.
        p.setProperty("server_log_set_size", "3,2");
        p.setProperty("client_log_set_size", "2,3");
        p.setProperty("parallel", "false");
        p.setProperty("skip_warmup", "true");
        p.setProperty("skip_gc", "true");
        p.setProperty(PsuMain.PTO_NAME_KEY, psuPtoName);
        p.setProperty("silent_cot", "true");
        return p;
    }
}
