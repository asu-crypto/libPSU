package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiServer;

/**
 * PSI factory.
 */
public class PsiFactory implements PtoFactory {
    private PsiFactory() {
        // empty
    }

    public static PsiServer createServer(Rpc serverRpc, Party clientParty, PsiConfig config) {
        PsiType type = config.getPtoType();
        switch (type) {
            case C_KisSon05:
                return new Ks05PsiServer(serverRpc, clientParty, (Ks05PsiConfig) config);
            case JOC_HazNis12:
                return new Hn12PsiServer(serverRpc, clientParty, (Hn12PsiConfig) config);
            default:
                throw new IllegalArgumentException("Invalid PsiType: " + type);
        }
    }

    public static PsiClient createClient(Rpc clientRpc, Party serverParty, PsiConfig config) {
        PsiType type = config.getPtoType();
        switch (type) {
            case C_KisSon05:
                return new Ks05PsiClient(clientRpc, serverParty, (Ks05PsiConfig) config);
            case JOC_HazNis12:
                return new Hn12PsiClient(clientRpc, serverParty, (Hn12PsiConfig) config);
            default:
                throw new IllegalArgumentException("Invalid PsiType: " + type);
        }
    }
}
