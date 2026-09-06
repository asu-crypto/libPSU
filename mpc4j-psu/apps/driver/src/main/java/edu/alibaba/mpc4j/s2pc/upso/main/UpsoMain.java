package edu.alibaba.mpc4j.s2pc.upso.main;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.upso.main.upsu.UpsuMain;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * UPSU-family driver entry (unbalanced private set union only).
 */
public class UpsoMain {
    public static void main(String[] args) throws Exception {
        PropertiesUtils.loadLog4jProperties();
        File inputFile = new File(args[0]);
        String ownName = args[1];
        List<String> allFiles;
        if (inputFile.isDirectory()) {
            allFiles = new LinkedList<>();
            collectConfFiles(inputFile, allFiles);
        } else {
            allFiles = List.of(inputFile.getPath());
        }
        String[] names = allFiles.stream().sorted().toArray(String[]::new);
        for (String name : names) {
            Properties properties = PropertiesUtils.loadProperties(name);
            String taskType = MainPtoConfigUtils.readPtoType(properties);
            if (!UpsuMain.PTO_TYPE_NAME.equals(taskType)) {
                throw new IllegalArgumentException(
                    "Invalid task_type: " + taskType + " (UPSU family supports UPSU only)"
                );
            }
            UpsuMain upsuMain = new UpsuMain(properties, ownName);
            upsuMain.runNetty();
        }
        System.exit(0);
    }

    private static void collectConfFiles(File dir, List<String> allFiles) {
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                collectConfFiles(f, allFiles);
            } else if (f.getPath().endsWith(".conf") && !f.getPath().endsWith(".disabled")) {
                allFiles.add(f.getPath());
            }
        }
    }
}
