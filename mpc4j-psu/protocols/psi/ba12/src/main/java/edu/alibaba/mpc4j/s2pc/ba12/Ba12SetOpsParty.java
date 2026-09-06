package edu.alibaba.mpc4j.s2pc.ba12;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.ba12.cardinality.Ba12CardinalityOps;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12MpcEngine;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import edu.alibaba.mpc4j.s2pc.ba12.relation.Ba12RelationOps;
import edu.alibaba.mpc4j.s2pc.ba12.set.Ba12SetOps;

/**
 * Two-party coordinator for BA12 set operations over XOR-shared integers.
 */
public class Ba12SetOpsParty extends AbstractTwoPartyPto {
  private final Ba12Config config;
  private final Z2cParty z2c;
  private final Ba12MpcEngine engine;

  public Ba12SetOpsParty(Rpc rpc, Party otherParty, Ba12Config config) {
    super(Ba12PtoDesc.getInstance(), rpc, otherParty, config);
    this.config = config;
    if (rpc.ownParty().getPartyId() == 0) {
      this.z2c = Z2cFactory.createSender(rpc, otherParty, config.getZ2cConfig());
    } else {
      this.z2c = Z2cFactory.createReceiver(rpc, otherParty, config.getZ2cConfig());
    }
    addSubPto(z2c);
    this.engine = new Ba12MpcEngine(config, z2c);
  }

  public void init() throws MpcAbortException {
    z2c.init();
  }

  public Ba12Share[] inputOwn(long[] values) {
    return engine.shareOwn(values);
  }

  public Ba12Share[] inputOther(int m) throws MpcAbortException {
    return engine.shareOther(m);
  }

  public long[] reveal(Ba12Share[] xs) throws MpcAbortException {
    return engine.reveal(xs);
  }

  public Ba12MpcEngine getEngine() {
    return engine;
  }

  public Ba12Share[] run(Ba12Operation op, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    if (op == Ba12Operation.BA12_ELEMENT_REDUCTION || op == Ba12Operation.BA12_ELEMENT_REDUCTION_CARDINALITY) {
      return runUnary(op, a);
    }
    return runBinary(op, a, b);
  }

  public Ba12Share[] runUnary(Ba12Operation op, Ba12Share[] a) throws MpcAbortException {
    switch (op) {
      case BA12_ELEMENT_REDUCTION:
        return Ba12SetOps.elementReduction(engine, a);
      case BA12_ELEMENT_REDUCTION_CARDINALITY:
        return new Ba12Share[] { Ba12CardinalityOps.elementReductionCardinality(engine, a) };
      default:
        throw new IllegalArgumentException("Unsupported unary BA12 operation: " + op);
    }
  }

  public Ba12Share[] runBinary(Ba12Operation op, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    switch (op) {
      case BA12_UNION:
        return Ba12SetOps.union(engine, a, b);
      case BA12_INTERSECTION:
        return Ba12SetOps.intersection(engine, a, b);
      case BA12_DIFFERENCE:
        return Ba12SetOps.difference(engine, a, b);
      case BA12_SYMMETRIC_DIFFERENCE:
        return Ba12SetOps.symmetricDifference(engine, a, b);
      case BA12_SUBSET:
        return wrapBit(Ba12RelationOps.subsetPadded(engine, a, b, a.length));
      case BA12_SUPERSET:
        return wrapBit(Ba12RelationOps.supersetPadded(engine, a, b, b.length));
      case BA12_EQUALITY:
        return wrapBit(Ba12RelationOps.equalityPadded(engine, a, b, a.length, b.length));
      case BA12_UNION_CARDINALITY:
        return new Ba12Share[] { Ba12CardinalityOps.unionCardinality(engine, a, b) };
      case BA12_INTERSECTION_CARDINALITY:
        return new Ba12Share[] { Ba12CardinalityOps.intersectionCardinality(engine, a, b) };
      case BA12_DIFFERENCE_CARDINALITY:
        return new Ba12Share[] { Ba12CardinalityOps.differenceCardinality(engine, a, b) };
      case BA12_SYMMETRIC_DIFFERENCE_CARDINALITY:
        return new Ba12Share[] { Ba12CardinalityOps.symmetricDifferenceCardinality(engine, a, b) };
      default:
        throw new IllegalArgumentException("Unsupported binary BA12 operation: " + op);
    }
  }

  private Ba12Share[] wrapBit(MpcZ2Vector bit) {
    return new Ba12Share[] { new Ba12Share(new MpcZ2Vector[] { bit }) };
  }
}
