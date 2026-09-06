package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26BatchedRddhProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26PublicParams;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26InvertibleMap;
import edu.alibaba.mpc4j.common.tool.utils.SerializeUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.shuffled.Pgt26ShuffledHashDh;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Set;

/** Shuffle AoK with real {@link Pgt26_2mParty#gen()} points (fair-bench items). */
public class Pgt26_2mMappingShuffleTest {
  @Test
  public void genCofactorClearsMappedPoint() {
    int n = 1;
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    byte[] key = Pgt26ProtocolTag.feistelKey(Pgt26_2mPsuPtoDesc.getInstance(), n, n);
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(key);
    byte[] item = new byte[Pgt26Constants.ITEM_BYTE_LENGTH];
    Pgt26_2mParty party = Pgt26_2mParty.create(new byte[][]{item}, key, pp, n, new FixedSecureRandom());
    Pgt26_2mParty.GenOutput gen = party.gen();
    byte[] expected = Pgt26EdwardsMath.canonicalizePoint(
        Pgt26EdwardsMath.pointMul(
            Pgt26EdwardsMath.cofactorClear(Pgt26InvertibleMap.hashToPoint(item, prp)), party.secret
        )
    );
    Assert.assertTrue(Pgt26EdwardsMath.pointEquals(expected, gen.points[0]));

    boolean sawTorsionDifference = false;
    for (int i = 0; i < 256; i++) {
      item[item.length - 1] = (byte) i;
      byte[] mapped = Pgt26InvertibleMap.hashToPoint(item, prp);
      byte[] reducedSk8Point = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.pointMul(mapped, party.sk8));
      byte[] cofactorPoint = Pgt26EdwardsMath.canonicalizePoint(
          Pgt26EdwardsMath.pointMul(Pgt26EdwardsMath.cofactorClear(mapped), party.secret)
      );
      if (!Pgt26EdwardsMath.pointEquals(reducedSk8Point, cofactorPoint)) {
        sawTorsionDifference = true;
        break;
      }
    }
    Assert.assertTrue("reduced sk_8 multiplication must differ for some mapped torsion coset", sawTorsionDifference);
  }

  @Test
  public void fullIntersectionProducesEmptyRddhBatch() {
    int n = 8;
    SecureRandom random = new SecureRandom();
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    byte[] key = Pgt26ProtocolTag.feistelKey(Pgt26_2mPsuPtoDesc.getInstance(), n, n);
    byte[][] items = new byte[n][Pgt26Constants.ITEM_BYTE_LENGTH];
    for (int i = 0; i < n; i++) {
      items[i][items[i].length - 1] = (byte) i;
    }
    Pgt26_2mParty client = Pgt26_2mParty.create(items, key, pp, n, random);
    Pgt26_2mParty server = Pgt26_2mParty.create(items, key, pp, n, random);
    Pgt26_2mParty.GenOutput clientGen = client.gen();
    Pgt26_2mParty.GenOutput serverGen = server.gen();

    byte[][] clientDecompressed = Pgt26EdwardsMath.decompressPoints(clientGen.points);
    Pgt26_2mParty.ShuffleOutput serverShuffle = server.blindShuffle(pp, clientDecompressed, clientGen.points);
    Pgt26_2mParty.UnblindOutput clientUnblind = client.finalResponse(
        pp, server.publicKey, clientDecompressed, Pgt26EdwardsMath.decompressPoints(serverShuffle.shuffled),
        clientGen.points, serverShuffle.shuffled, serverGen.points, serverShuffle.proof, false, random
    );
    Assert.assertNotNull(clientUnblind);
    Assert.assertEquals(0, clientUnblind.unblinded.length);
    Assert.assertTrue(Pgt26BatchedRddhProof.verify(
        client.publicKey, clientUnblind.unblinded, clientUnblind.unblinded,
        clientUnblind.unblinded, clientUnblind.unblinded, clientUnblind.proof
    ));
  }

  @Test
  public void fairBenchGenPointsShuffleProofRoundTrip() throws Exception {
    int n = 32;
    SecureRandom random = new SecureRandom();
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    byte[] key = Pgt26ProtocolTag.feistelKey(Pgt26_2mPsuPtoDesc.getInstance(), n, n);
    Set<ByteBuffer> serverSet = PsuBenchmarkUtils.generateBytesSets(n, n, Pgt26Constants.ITEM_BYTE_LENGTH).get(0);
    byte[][] serverItems = Pgt26ShuffledHashDh.itemsFromByteBuffers(
        serverSet.stream().toList(), Pgt26Constants.ITEM_BYTE_LENGTH
    );
    Set<ByteBuffer> clientSet = PsuBenchmarkUtils.generateBytesSets(n, n, Pgt26Constants.ITEM_BYTE_LENGTH).get(1);
    byte[][] clientItems = Pgt26ShuffledHashDh.itemsFromByteBuffers(
        clientSet.stream().toList(), Pgt26Constants.ITEM_BYTE_LENGTH
    );
    Pgt26_2mParty client = Pgt26_2mParty.create(clientItems, key, pp, n, random);
    Pgt26_2mParty server = Pgt26_2mParty.create(serverItems, key, pp, n, random);
    Pgt26_2mParty.GenOutput clientGen = client.gen();
    Pgt26_2mParty.GenOutput serverGen = server.gen();
    List<byte[]> r2Wire = Pgt26_2mWire.round2Payload(clientGen.pk, clientGen.points);
    List<byte[]> r2Back = SerializeUtils.decompressEqual(
        SerializeUtils.compressEqual(r2Wire, r2Wire.get(0).length),
        r2Wire.get(0).length
    );
    byte[][] clientPointsOnWire = Pgt26_2mWire.clonePoints(
        r2Back.subList(1, r2Back.size()).toArray(new byte[0][])
    );
    for (int j = 0; j < n; j++) {
      Assert.assertArrayEquals("round2 point " + j, clientGen.points[j], clientPointsOnWire[j]);
    }
    byte[][] clientDecompressed = Pgt26EdwardsMath.decompressPoints(clientPointsOnWire);
    Pgt26_2mParty.ShuffleOutput serverShuffle = server.blindShuffle(pp, clientDecompressed, clientPointsOnWire);
    byte[] wire = Pgt26_2mWire.packAdapted(serverShuffle.proof, n);
    Pgt26AdaptedShuffleProof decoded = Pgt26_2mWire.unpackAdapted(wire);
    byte[][] ownDecompressed = Pgt26EdwardsMath.decompressPoints(clientGen.points);
    byte[][] shuffledDecompressed = Pgt26EdwardsMath.decompressPoints(serverShuffle.shuffled);
    String fail = Pgt26AdaptedShuffleProof.verifyFailureStage(
        pp, server.publicKey, ownDecompressed, shuffledDecompressed,
        clientGen.points, serverShuffle.shuffled, decoded, random
    );
    Assert.assertNull("wire-round2 + local verify failed at: " + fail, fail);
    List<byte[]> shuffledWire = Arrays.asList(serverShuffle.shuffled);
    List<byte[]> shuffledBack = SerializeUtils.decompressEqual(
        SerializeUtils.compressEqual(shuffledWire, shuffledWire.get(0).length),
        shuffledWire.get(0).length
    );
    byte[][] shuffledOnWire = Pgt26_2mWire.clonePoints(shuffledBack.toArray(new byte[0][]));
    byte[][] shuffledOnWireDecompressed = Pgt26EdwardsMath.decompressPoints(shuffledOnWire);
    fail = Pgt26AdaptedShuffleProof.verifyFailureStage(
        pp, server.publicKey, ownDecompressed, shuffledOnWireDecompressed,
        clientGen.points, shuffledOnWire, decoded, random
    );
    Assert.assertNull("server shuffle verify failed at: " + fail, fail);
    byte[][] serverDecompressed = Pgt26EdwardsMath.decompressPoints(serverGen.points);
    Pgt26_2mParty.ShuffleOutput clientShuffle = client.blindShuffle(pp, serverDecompressed, serverGen.points);
    wire = Pgt26_2mWire.packAdapted(clientShuffle.proof, n);
    decoded = Pgt26_2mWire.unpackAdapted(wire);
    byte[][] serverOwnDecompressed = Pgt26EdwardsMath.decompressPoints(serverGen.points);
    byte[][] clientShuffledDecompressed = Pgt26EdwardsMath.decompressPoints(clientShuffle.shuffled);
    fail = Pgt26AdaptedShuffleProof.verifyFailureStage(
        pp, client.publicKey, serverOwnDecompressed, clientShuffledDecompressed,
        serverGen.points, clientShuffle.shuffled, decoded, random
    );
    Assert.assertNull("client shuffle verify failed at: " + fail, fail);
  }

  private static final class FixedSecureRandom extends SecureRandom {
    @Override
    public void nextBytes(byte[] bytes) {
      Arrays.fill(bytes, (byte) 1);
    }
  }
}
