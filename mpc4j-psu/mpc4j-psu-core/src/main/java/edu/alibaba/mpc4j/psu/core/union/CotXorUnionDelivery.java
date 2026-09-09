package edu.alibaba.mpc4j.psu.core.union;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;
import edu.alibaba.mpc4j.psu.api.plugin.UnionDeliveryInput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared PRG-masked union reconstruction used by mqRPMT-style balanced PSU protocols.
 *
 * <p>Encrypted slots carry an explicit validity tag so padding is out-of-band from the
 * user element domain (no reserved all-{@code 0xFF} sentinel).
 */
public final class CotXorUnionDelivery {
    /** Padding / absent slot. */
    public static final byte TAG_PADDING = 0;
    /** Real user element follows. */
    public static final byte TAG_REAL = 1;

    private CotXorUnionDelivery() {
    }

    public static int wireByteLength(int elementByteLength) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        return elementByteLength + 1;
    }

    public static byte[] encodeRealElement(byte[] element) {
        byte[] wire = new byte[element.length + 1];
        wire[0] = TAG_REAL;
        System.arraycopy(element, 0, wire, 1, element.length);
        return wire;
    }

    public static byte[] encodePadding(int elementByteLength) {
        return new byte[wireByteLength(elementByteLength)];
    }

    /**
     * Reconstructs union on the small-set (client) side from COT outputs and encrypted payloads.
     */
    public static PsuUnionOutput unionFromCotXor(
        EnvType envType, int elementByteLength,
        CotReceiverOutput cotReceiverOutput, UnionDeliveryInput input
    ) {
        boolean[] choices = input.getCotChoices();
        List<byte[]> encPayload = input.getEncPayload();
        int coreCotNum = choices.length;
        if (encPayload.size() != coreCotNum) {
            throw new IllegalArgumentException("enc payload size mismatch: " + encPayload.size() + " vs " + coreCotNum);
        }
        int wireLen = wireByteLength(elementByteLength);
        Prg encPrg = PrgFactory.createInstance(envType, wireLen);
        Set<ByteBuffer> union = new HashSet<>(coreCotNum + input.getSmallSet().size());
        for (int index = 0; index < coreCotNum; index++) {
            if (!choices[index]) {
                byte[] message = encPrg.extendToBytes(cotReceiverOutput.getRb(index));
                BytesUtils.xori(message, encPayload.get(index));
                if (message.length != wireLen) {
                    throw new IllegalArgumentException("union wire length mismatch");
                }
                if (message[0] == TAG_PADDING) {
                    continue;
                }
                if (message[0] != TAG_REAL) {
                    throw new IllegalArgumentException("unknown union wire tag: " + (message[0] & 0xFF));
                }
                byte[] element = Arrays.copyOfRange(message, 1, wireLen);
                union.add(ByteBuffer.wrap(element));
            }
        }
        union.addAll(input.getSmallSet());
        int psica = input.getSmallSet().size() + input.getLargeSetSize() - union.size();
        return new PsuUnionOutput(union, psica);
    }

    /**
     * @deprecated Use {@link #unionFromCotXor(EnvType, int, CotReceiverOutput, UnionDeliveryInput)}.
     *             The bot sentinel argument is ignored; validity is carried in the wire tag.
     */
    @Deprecated
    public static PsuUnionOutput unionFromCotXor(
        EnvType envType, int elementByteLength, ByteBuffer botElement,
        CotReceiverOutput cotReceiverOutput, UnionDeliveryInput input
    ) {
        return unionFromCotXor(envType, elementByteLength, cotReceiverOutput, input);
    }
}
