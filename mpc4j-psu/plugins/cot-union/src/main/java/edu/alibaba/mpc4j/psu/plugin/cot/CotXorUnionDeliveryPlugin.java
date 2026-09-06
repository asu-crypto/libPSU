package edu.alibaba.mpc4j.psu.plugin.cot;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.psu.api.PluginId;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;
import edu.alibaba.mpc4j.psu.api.plugin.PsuPluginInit;
import edu.alibaba.mpc4j.psu.api.plugin.PsuUnionDeliveryPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.UnionDeliveryInput;
import edu.alibaba.mpc4j.psu.core.union.CotXorUnionDelivery;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

import java.nio.ByteBuffer;

/**
 * Union delivery via Core COT + PRG XOR (PKC_GMRSS21 / CZZ24 / ZCL23-PKE family).
 */
public class CotXorUnionDeliveryPlugin implements PsuUnionDeliveryPlugin {
    private final CoreCotReceiver coreCotReceiver;
    private final EnvType envType;
    private final ByteBuffer botElement;
    private PsuPluginInit init;

    public CotXorUnionDeliveryPlugin(Rpc rpc, Party otherParty, CoreCotConfig config, EnvType envType, ByteBuffer botElement) {
        coreCotReceiver = CoreCotFactory.createReceiver(rpc, otherParty, config);
        this.envType = envType;
        this.botElement = botElement;
    }

    @Override
    public PluginId getPluginId() {
        return PluginId.COT_XOR_UNION;
    }

    @Override
    public void init(PsuPluginInit init) throws MpcAbortException {
        this.init = init;
        coreCotReceiver.init();
    }

    @Override
    public PsuUnionOutput deliverUnion(UnionDeliveryInput input) throws MpcAbortException {
        CotReceiverOutput cotOut = coreCotReceiver.receive(input.getCotChoices());
        ByteBuffer bot = botElement;
        int elementByteLength = input.getElementByteLength();
        if (bot == null) {
            byte[] botBytes = new byte[elementByteLength];
            java.util.Arrays.fill(botBytes, (byte) 0xFF);
            bot = ByteBuffer.wrap(botBytes);
        }
        return CotXorUnionDelivery.unionFromCotXor(
            envType, elementByteLength, bot, cotOut, input
        );
    }

    public CoreCotReceiver getCoreCotReceiver() {
        return coreCotReceiver;
    }
}
