package edu.alibaba.mpc4j.psu.core.union;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;
import edu.alibaba.mpc4j.psu.api.plugin.UnionDeliveryInput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared PRG-masked union reconstruction used by mqRPMT-style balanced PSU protocols.
 */
public final class CotXorUnionDelivery {
    private CotXorUnionDelivery() {
    }

    /**
     * Reconstructs union on the small-set (client) side from COT outputs and encrypted payloads.
     *
     * @param envType environment type for PRG
     * @param elementByteLength element byte length
     * @param botElement sentinel element to exclude
     * @param cotReceiverOutput COT receiver output
     * @param input union delivery input
     * @return union output with PSI cardinality estimate
     */
    public static PsuUnionOutput unionFromCotXor(
        EnvType envType, int elementByteLength, ByteBuffer botElement,
        CotReceiverOutput cotReceiverOutput, UnionDeliveryInput input) {
        boolean[] choices = input.getCotChoices();
        List<byte[]> encPayload = input.getEncPayload();
        int coreCotNum = choices.length;
        if (encPayload.size() != coreCotNum) {
            throw new IllegalArgumentException("enc payload size mismatch: " + encPayload.size() + " vs " + coreCotNum);
        }
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        Set<ByteBuffer> union = new HashSet<>(coreCotNum + input.getSmallSet().size());
        for (int index = 0; index < coreCotNum; index++) {
            if (!choices[index]) {
                byte[] message = encPrg.extendToBytes(cotReceiverOutput.getRb(index));
                BytesUtils.xori(message, encPayload.get(index));
                union.add(ByteBuffer.wrap(message));
            }
        }
        union.addAll(input.getSmallSet());
        union.remove(botElement);
        int psica = input.getSmallSet().size() + input.getLargeSetSize() - union.size();
        return new PsuUnionOutput(union, psica);
    }
}
