# HN12 malicious PSI (Hazay–Nissim)

Polynomial / ElGamal-in-exponent / Pedersen / ZK / PRF based PSI (`JOC:HazNis12`, Protocol 5 π∩).

**Not** PGT26, ASIACCS:BlaAgu12, EUROCRYPT:PisTri26, CZZ24, or ZCL23.

- **Output:** client (P1) learns `X ∩ Y`; server (P2) no output.
- **Tests/benchmarks:** only `2^5` and `2^20` (`JOC:HazNis12_2p5`, `JOC:HazNis12_2p20`).

## Run tests

```bash
mvn -pl mpc4j-psu-tests -Dmaven.test.skip=false -Dtest=Hn12GroupElGamal2p5Test,Hn12Psi2p5Test test
```

## Config

- `useIdealPrfForTesting=true` (default in tests): ideal PRF for `2^5`; production requires real malicious OPRF (TODO).
- `enableSemiHonestDebug`: skips malicious ZK verification (FNP-style debug).

## PSU

`HN12_PSU` (Protocol 8) is not yet implemented in this module.
