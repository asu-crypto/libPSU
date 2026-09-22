package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;
import edu.alibaba.libpsu.core.set.SetElementUtils;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

/**
 * Local Fisher–Yates permutations (never sent on the wire).
 */
public final class SmallEcPermUtils {
    private static final int PRG_OUTPUT_BYTES = Integer.BYTES;
    private static final Prg PRG = PrgFactory.createInstance(EnvType.STANDARD_JDK, PRG_OUTPUT_BYTES);

    private SmallEcPermUtils() {
        // empty
    }

    public static int[] generatePermutation(int n, byte[] seed) {
        Preconditions.checkArgument(n >= 0);
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        if (n <= 1) {
            return perm;
        }
        Preconditions.checkNotNull(seed);
        int draw = 0;
        for (int i = n - 1; i >= 1; i--) {
            int j = prgNextInt(seed, draw++, i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        return perm;
    }

    private static int prgNextInt(byte[] seed, int counter, int bound) {
        byte[] prgKey = derivePrgKey(seed, counter);
        byte[] out = PRG.extendToBytes(prgKey);
        return new BigInteger(1, out).mod(BigInteger.valueOf(bound)).intValue();
    }

    private static byte[] derivePrgKey(byte[] seed, int counter) {
        Blake2s256 hasher = new Blake2s256();
        hasher.update(seed);
        hasher.update(IntUtils.intToByteArray(counter));
        byte[] digest = hasher.digest();
        return Arrays.copyOf(digest, CommonConstants.BLOCK_BYTE_LENGTH);
    }

    public static byte[] sampleSeed(java.security.SecureRandom random) {
        byte[] seed = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        random.nextBytes(seed);
        return seed;
    }

    public static byte[][] applyPermutation(byte[][] values, int[] perm) {
        byte[][] out = new byte[perm.length][];
        for (int i = 0; i < perm.length; i++) {
            out[i] = values[perm[i]];
        }
        return out;
    }

    public static ArrayList<ByteBuffer> deduplicateElements(Set<ByteBuffer> elements, int elementByteLength) {
        return SetElementUtils.deduplicateElements(elements, elementByteLength);
    }
}
