/**
 * One-sided PGT26-1M is <strong>not supported</strong> on the public production surface.
 * <p>
 * Only two-sided PGT26-2M ({@code EUROCRYPT:PuGaoTri26} / {@code PGT26_2M}) is supported.
 * Public factory and protocol-name parsing reject {@code PGT26_1M}. Production client/server/
 * config/ptoDesc implementations have been removed from this package; do not resurrect them
 * into {@code src/main} without a dedicated one-sided public API and correctness suite.
 */
package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided;
