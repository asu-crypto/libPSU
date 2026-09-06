package edu.alibaba.mpc4j.s2pc.pso.main.ba12;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Config;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Operation;
import java.util.Properties;

/**
 * Reads BA12 driver properties from fair-bench {@code .conf} files.
 */
public class Ba12ConfigUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(Ba12ConfigUtils.class);

  public static final String BA12_PTO_NAME_KEY = "ba12_pto_name";
  public static final String BA12_OPERATION_KEY = "ba12_operation";

  private Ba12ConfigUtils() {
    // empty
  }

  public static String readBa12PtoName(Properties properties) {
    return PropertiesUtils.readString(properties, BA12_PTO_NAME_KEY, "BA12");
  }

  public static Ba12Operation readOperation(Properties properties) {
    String name = PropertiesUtils.readString(properties, BA12_OPERATION_KEY, Ba12Operation.BA12_UNION.name());
    return Ba12Operation.valueOf(name);
  }

  public static Ba12Config createConfig(Properties properties, int elementByteLength) {
    int requestedEll = elementByteLength * Byte.SIZE;
    int ell = Math.min(requestedEll, LongUtils.MAX_L_FOR_MODULE_N);
    if (ell < requestedEll) {
      LOGGER.warn(
        "BA12 uses Zl64 bit-decomposition: element bits {} capped to {} (max for long database)",
        requestedEll, ell
      );
    }
    boolean silent = PropertiesUtils.readBoolean(properties, "silent_cot", true);
    return new Ba12Config.Builder()
      .setEll(ell)
      .setSecurityModel(SecurityModel.SEMI_HONEST)
      .setSilent(silent)
      .build();
  }
}
