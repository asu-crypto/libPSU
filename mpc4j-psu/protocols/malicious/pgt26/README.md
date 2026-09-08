# PGT26 (Pu–Gao–Trieu) malicious PSU

This module implements **PGT26** malicious private set union from [Malicious-Private-Set-Union](https://github.com/sihangpu/Malicious-Private-Set-Union). It is **not** `EUROCRYPT:PisTri26` (Piske–Trieu IBLT in `protocols/balanced/pt26`, artifact `mpc4j-psu-protocol-pt26`).

## Protocols

| Type | Role | Output |
|------|------|--------|
| `EUROCRYPT:PuGaoTri26` / `PGT26_2M` | Fully malicious two-sided PSU (Steps 1*–5*) | Two-sided union via the dedicated two-sided factory |

**Only 2M is supported.** `PGT26_1M` (one-sided) is rejected by `PsuType` / `ProtocolRegistry` / `PsuConfigUtils` and has no production client/server/config under `onesided/`.

## Layout (maps to Rust reference)

| Java | Rust |
|------|------|
| `pgt26/shuffled/Pgt26ShuffledHashDh.java` | `onesided.rs` (HashDH core) |
| `pgt26/aok/Pgt26DdhKnowledgeProof.java` | `onesided.rs` / `aok.rs` (Schnorr AoK primitives) |
| `pgt26/mapping/` | `mapping.rs` (2M) |
| `pgt26/twosided/` | `twosided.rs` (2M) |

## Tests and benchmarks (only `2^5` and `2^20`)

Public-API rejection for the quarantined 1M ID:

```bash
mvn -pl mpc4j-psu-tests -Dmaven.test.skip=false -Dtest=Pgt26_1mPublicApiRejectionTest test
```

Fair bench configs (after integration):

```bash
# from repo root, after mvn install
scripts/run_psu_fair.sh 5
```

## Notes

- Items are **128-bit** (`Pgt26Constants.ITEM_BYTE_LENGTH = 16`).
- Fiat–Shamir domain: `Pgt26ProtocolTag` (shared tag on both parties).
- Figure 6 Round-4 coverage is checked before RDDH verify (`Pgt26_2mCoverageCheck`).
