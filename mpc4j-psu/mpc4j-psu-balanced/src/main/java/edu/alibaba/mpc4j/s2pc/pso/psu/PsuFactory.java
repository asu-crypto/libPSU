package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.*;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.*;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.*;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuServer;

/**
 * PSU factory.
 */
public class PsuFactory implements PtoFactory {
    private PsuFactory() {
        // empty
    }

    public static PsuServer createServer(Rpc serverRpc, Party clientParty, PsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case AC_KRTW19:
                return new Krtw19PsuServer(serverRpc, clientParty, (Krtw19PsuConfig) config);
            case PKC_GMRSS21:
                return new Gmr21PsuServer(serverRpc, clientParty, (Gmr21PsuConfig) config);
            case USENIX_ConYuWeiminDon23_SKE:
                return new Zcl23SkePsuServer(serverRpc, clientParty, (Zcl23SkePsuConfig) config);
            case USENIX_ConYuWeiminDon23_PKE:
                return new Zcl23PkePsuServer(serverRpc, clientParty, (Zcl23PkePsuConfig) config);
            case USENIX_JSZDG22:
                return new Jsz22SfcPsuServer(serverRpc, clientParty, (Jsz22SfcPsuConfig) config);
            case USENIX_JSZDG22_SFS:
                return new Jsz22SfsPsuServer(serverRpc, clientParty, (Jsz22SfsPsuConfig) config);
            case PKC_CheZhaZha24:
                return new Czz24CwOprfPsuServer(serverRpc, clientParty, (Czz24CwOprfPsuConfig) config);
            case ASIACCS_CSSW25:
                return new Css25PsuServer(serverRpc, clientParty, (Css25PsuConfig) config);
            case EUROCRYPT_PisTri26:
                return new Pt26PsuServer(serverRpc, clientParty, (Pt26PsuConfig) config);
            case ACISP_DavCid17:
                return new Dc17PsuServer(serverRpc, clientParty, (Dc17PsuConfig) config);
            case ACNS_Frikken07:
                return new F07PsuServer(serverRpc, clientParty, (F07PsuConfig) config);
            case C_KisSon05:
                return new Ks05PsuServer(serverRpc, clientParty, (Ks05PsuConfig) config);
            case JOC_HazNis12:
                return new Hn12PsuServer(serverRpc, clientParty, (Hn12PsuConfig) config);
            case USENIX_BinYujConYanYu25:
                return new Tbz25PsuServer(serverRpc, clientParty, (Tbz25PsuConfig) config);
            case USENIX_HaoWan26:
                return new HaoWan2026PsuServer(serverRpc, clientParty, (HaoWan2026PsuConfig) config);
            case USENIX_YanShiHonDaw24:
                return new Jszg24BecrgPsuServer(serverRpc, clientParty, (Jszg24BecrgPsuConfig) config);
            case Ours:
                return new SmallEcElligatorPsuServer(serverRpc, clientParty, (SmallEcElligatorPsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static OoPsuServer createOoPsuServer(Rpc serverRpc, Party clientParty, OoPsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case PKC_GMRSS21:
                return new Gmr21PsuServer(serverRpc, clientParty, (Gmr21PsuConfig) config);
            case USENIX_JSZDG22:
                return new Jsz22SfcPsuServer(serverRpc, clientParty, (Jsz22SfcPsuConfig) config);
            case USENIX_JSZDG22_SFS:
                return new Jsz22SfsPsuServer(serverRpc, clientParty, (Jsz22SfsPsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static PsuServer createServer(Rpc serverRpc, Party clientParty, Party aiderParty, PsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case USENIX_ConYuWeiminDon23_SKE:
                return new Zcl23SkePsuServer(serverRpc, clientParty, aiderParty, (Zcl23SkePsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static PsuClient createClient(Rpc clientRpc, Party serverParty, PsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case AC_KRTW19:
                return new Krtw19PsuClient(clientRpc, serverParty, (Krtw19PsuConfig) config);
            case PKC_GMRSS21:
                return new Gmr21PsuClient(clientRpc, serverParty, (Gmr21PsuConfig) config);
            case USENIX_ConYuWeiminDon23_SKE:
                return new Zcl23SkePsuClient(clientRpc, serverParty, (Zcl23SkePsuConfig) config);
            case USENIX_ConYuWeiminDon23_PKE:
                return new Zcl23PkePsuClient(clientRpc, serverParty, (Zcl23PkePsuConfig) config);
            case USENIX_JSZDG22:
                return new Jsz22SfcPsuClient(clientRpc, serverParty, (Jsz22SfcPsuConfig) config);
            case USENIX_JSZDG22_SFS:
                return new Jsz22SfsPsuClient(clientRpc, serverParty, (Jsz22SfsPsuConfig) config);
            case PKC_CheZhaZha24:
                return new Czz24CwOprfPsuClient(clientRpc, serverParty, (Czz24CwOprfPsuConfig) config);
            case ASIACCS_CSSW25:
                return new Css25PsuClient(clientRpc, serverParty, (Css25PsuConfig) config);
            case EUROCRYPT_PisTri26:
                return new Pt26PsuClient(clientRpc, serverParty, (Pt26PsuConfig) config);
            case ACISP_DavCid17:
                return new Dc17PsuClient(clientRpc, serverParty, (Dc17PsuConfig) config);
            case ACNS_Frikken07:
                return new F07PsuClient(clientRpc, serverParty, (F07PsuConfig) config);
            case C_KisSon05:
                return new Ks05PsuClient(clientRpc, serverParty, (Ks05PsuConfig) config);
            case JOC_HazNis12:
                return new Hn12PsuClient(clientRpc, serverParty, (Hn12PsuConfig) config);
            case USENIX_BinYujConYanYu25:
                return new Tbz25PsuClient(clientRpc, serverParty, (Tbz25PsuConfig) config);
            case USENIX_HaoWan26:
                return new HaoWan2026PsuClient(clientRpc, serverParty, (HaoWan2026PsuConfig) config);
            case USENIX_YanShiHonDaw24:
                return new Jszg24BecrgPsuClient(clientRpc, serverParty, (Jszg24BecrgPsuConfig) config);
            case Ours:
                return new SmallEcElligatorPsuClient(clientRpc, serverParty, (SmallEcElligatorPsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static PsuTwoSidedClient createTwoSidedClient(Rpc clientRpc, Party serverParty, PsuConfig config) {
        if (config.getPtoType() != PsuType.EUROCRYPT_PuGaoTri26) {
            throw new IllegalArgumentException("two-sided factory only supports EUROCRYPT:PuGaoTri26");
        }
        return new Pgt26_2mPsuClient(clientRpc, serverParty, (Pgt26_2mPsuConfig) config);
    }

    public static PsuTwoSidedServer createTwoSidedServer(Rpc serverRpc, Party clientParty, PsuConfig config) {
        if (config.getPtoType() != PsuType.EUROCRYPT_PuGaoTri26) {
            throw new IllegalArgumentException("two-sided factory only supports EUROCRYPT:PuGaoTri26");
        }
        return new Pgt26_2mPsuServer(serverRpc, clientParty, (Pgt26_2mPsuConfig) config);
    }

    public static OoPsuClient createOoPsuClient(Rpc clientRpc, Party serverParty, OoPsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case PKC_GMRSS21:
                return new Gmr21PsuClient(clientRpc, serverParty, (Gmr21PsuConfig) config);
            case USENIX_JSZDG22:
                return new Jsz22SfcPsuClient(clientRpc, serverParty, (Jsz22SfcPsuConfig) config);
            case USENIX_JSZDG22_SFS:
                return new Jsz22SfsPsuClient(clientRpc, serverParty, (Jsz22SfsPsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static PsuClient createClient(Rpc clientRpc, Party serverParty, Party aiderParty, PsuConfig config) {
        PsuType type = config.getPtoType();
        switch (type) {
            case USENIX_ConYuWeiminDon23_SKE:
                return new Zcl23SkePsuClient(clientRpc, serverParty, aiderParty, (Zcl23SkePsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    public static PsuConfig createDefaultConfig(SecurityModel securityModel) {
        switch (securityModel) {
            case IDEAL:
            case TRUSTED_DEALER:
                return new Zcl23SkePsuConfig.Builder(SecurityModel.TRUSTED_DEALER, true).build();
            case SEMI_HONEST:
                return new Gmr21PsuConfig.Builder(false).build();
            case MALICIOUS:
            default:
                throw new IllegalArgumentException("Invalid " + SecurityModel.class.getSimpleName() + ": " + securityModel.name());
        }
    }
}
