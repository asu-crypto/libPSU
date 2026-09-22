package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.aprr24.Aprr24F32SowOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26AltModExpand.ExpandProfile;
import edu.alibaba.mpc4j.s2pc.pcg.ot.conv32.Conv32Factory.Conv32Type;
import org.junit.Assert;
import org.junit.Test;

/**
 * Profile consistency: G expand follows F32 SOW A/B public-parameter type.
 */
public class HaoWan26SsPmtFastConfigTest {

    @Test
    public void defaultUsesSecureJoinForGAndAB() {
        HaoWan26SsPmtFastConfig config = HaoWan26SsPmtFastConfig.createDefault(SecurityModel.SEMI_HONEST, false);
        Assert.assertEquals(F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN, config.getPublicParamsType());
        Assert.assertEquals(ExpandProfile.HAO_WAN_SECURE_JOIN, config.getExpandProfile());
        Assert.assertEquals(
            F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN,
            config.getF32SowOprfConfig().getPublicParamsType()
        );
    }

    @Test
    public void explicitNativeF32UsesNativeExpand() {
        F32SowOprfConfig f32 = new Aprr24F32SowOprfConfig.Builder(Conv32Type.CCOT)
            .setPublicParamsType(F32WprfPublicParamsType.MPC4J_NATIVE)
            .build();
        PeqtConfig peqt = new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).build();
        HaoWan26SsPmtFastConfig config = new HaoWan26SsPmtFastConfig.Builder(f32, peqt).build();
        Assert.assertEquals(F32WprfPublicParamsType.MPC4J_NATIVE, config.getPublicParamsType());
        Assert.assertEquals(ExpandProfile.MPC4J_NATIVE, config.getExpandProfile());
    }

    @Test
    public void mixedSecureF32WithNativeExpandRejectedInBuild() {
        F32SowOprfConfig f32 = new Aprr24F32SowOprfConfig.Builder(Conv32Type.CCOT)
            .setPublicParamsType(F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN)
            .build();
        PeqtConfig peqt = new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).build();
        try {
            new HaoWan26SsPmtFastConfig.Builder(f32, peqt)
                .setExpandProfile(ExpandProfile.MPC4J_NATIVE)
                .build();
            Assert.fail("expected IllegalArgumentException for mixed G/A/B profiles");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("G profile"));
        }
    }
}
