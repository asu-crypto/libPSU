package edu.alibaba.mpc4j.s2pc.ba12.core;

/**
 * Instrumentation counters for BA12 runs.
 */
public class Ba12Stats {
  private long eqCount;
  private long geCount;
  private long mulCount;
  private long openCount;

  public void incEq() {
    eqCount++;
  }

  public void incGe() {
    geCount++;
  }

  public void incMul() {
    mulCount++;
  }

  public void incOpen() {
    openCount++;
  }

  public long getEqCount() {
    return eqCount;
  }

  public long getGeCount() {
    return geCount;
  }

  public long getMulCount() {
    return mulCount;
  }

  public long getOpenCount() {
    return openCount;
  }
}
