# PGT26 (Pu–Gao–Trieu) malicious PSU

This module implements **PGT26** malicious private set union from [Malicious-Private-Set-Union](https://github.com/sihangpu/Malicious-Private-Set-Union). It is **not** `EUROCRYPT:PisTri26` (Piske–Trieu IBLT in `protocols/balanced/pt26`, artifact `mpc4j-psu-protocol-pt26`).

## Protocols

| Type | Role | Output |
|------|------|--------|
| `EUROCRYPT:PuGaoTri26` | Fully malicious two-sided PSU (Steps 1*–5*) | Two-sided union via the dedicated two-sided factory |

The one-sided PGT26 prototype remains in source for reference, but it is removed from the public
registry, factory, benchmark configs, and docs.

## Layout (maps to Rust reference)

| Java | Rust |
|------|------|
| `pgt26/shuffled/Pgt26ShuffledHashDh.java` | `onesided.rs` (HashDH core) |
| `pgt26/aok/Pgt26DdhKnowledgeProof.java` | `onesided.rs` / `aok.rs` (1M Schnorr AoK) |
| `pgt26/onesided/Pgt26_1mPsu*.java` | `onesided.rs` (1M protocol) |
| `pgt26/mapping/` | `mapping.rs` (2M only — TODO) |
| `pgt26/twosided/` | `twosided.rs` (2M — TODO) |

## Tests and benchmarks (only `2^5` and `2^20`)

One-sided prototype tests are ignored because the public factory surface no longer exposes that ID.

Fair bench configs (after integration):

```bash
# from repo root, after mvn install
scripts/run_psu_fair.sh 5
```

## Run unit tests

```bash
mvn -pl mpc4j-psu-tests -Dmaven.test.skip=false -Dtest=Pgt26_1mPsuTest test
```

## Notes

- Items are **128-bit** (`Pgt26Constants.ITEM_BYTE_LENGTH = 16`).
- Invalid 1M proofs are **ignored** (no abort); see `Pgt26_1mPsuClient`.
- Fiat–Shamir domain: `Pgt26ProtocolTag` (shared tag on both parties).
