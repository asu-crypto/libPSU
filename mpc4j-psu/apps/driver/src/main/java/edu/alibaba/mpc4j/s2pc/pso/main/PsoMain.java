package edu.alibaba.mpc4j.s2pc.pso.main;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.ba12.Ba12Main;
import edu.alibaba.mpc4j.s2pc.pso.main.psi.PsiMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.OoPsuMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuBlackIpMain;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * PSU-family driver entry (balanced PSU and offline/online PSU only).
 */
public class PsoMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsoMain.class);

    public static void main(String[] args) throws Exception {
        PropertiesUtils.loadLog4jProperties();
        File inputFile = new File(args[0]);
        String ownName = args[1];
        List<String> allFiles;
        if (inputFile.isDirectory()) {
            allFiles = new LinkedList<>();
            collectConfFiles(inputFile, allFiles);
        } else {
            allFiles = new ArrayList<>();
            allFiles.add(inputFile.getPath());
        }
        ArrayList<String> sortedFiles = new ArrayList<>(allFiles);
        Collections.sort(sortedFiles);
        String[] names = sortedFiles.toArray(new String[0]);
        LOGGER.info(Arrays.toString(names));
        for (String name : names) {
            Properties properties = PropertiesUtils.loadProperties(name);
            String ptoType = MainPtoConfigUtils.readPtoType(properties);
            switch (ptoType) {
                case OoPsuMain.PTO_TYPE_NAME: {
                    OoPsuMain main = new OoPsuMain(properties, ownName);
                    main.runNetty();
                    break;
                }
                case PsuMain.PTO_TYPE_NAME: {
                    PsuMain main = new PsuMain(properties, ownName);
                    main.runNetty();
                    break;
                }
                case PsuBlackIpMain.PTO_TYPE_NAME: {
                    PsuBlackIpMain psuBlackIpMain = new PsuBlackIpMain(properties, ownName);
                    psuBlackIpMain.runNetty();
                    break;
                }
                case PsiMain.PTO_TYPE_NAME: {
                    PsiMain psiMain = new PsiMain(properties, ownName);
                    psiMain.runNetty();
                    break;
                }
                case Ba12Main.PTO_TYPE_NAME: {
                    Ba12Main ba12Main = new Ba12Main(properties, ownName);
                    ba12Main.runNetty();
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                        "Invalid " + MainPtoConfigUtils.PTO_TYPE_KEY + ": " + ptoType
                            + " (PSO driver supports PSU, OO_PSU, PSU_BLACK_IP, PSI, BA12)"
                    );
            }
        }
        System.exit(0);
    }

    private static boolean shouldSkipFairBenchConf(File f) {
        String path = f.getPath();
        return path.contains("11_DGG25") || path.contains("psu_fair_11_DGG25");
    }

    private static void collectConfFiles(File dir, List<String> allFiles) {
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                collectConfFiles(f, allFiles);
            } else if (f.getPath().endsWith(".conf") && !shouldSkipFairBenchConf(f)) {
                allFiles.add(f.getPath());
            }
        }
    }
}
