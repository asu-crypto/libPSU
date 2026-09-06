# ASIACCS:BlaAgu12 — Blanton–Aguiar private set / multiset operations

Secret-sharing MPC (not PSU/PSI). Implements oblivious set operations via **sorting + comparisons** (Protocols 1–7 in the paper).

## Security

- Semi-honest: `Bea91Z2c` + `Z2IntegerCircuit` (2-party XOR shares; extends to MPC4J’s `t < n/2` model when `n = 2`).
- Malicious: only when underlying Z2 multiplication/comparison is configured malicious (not enabled by default).

## Dummy element

`0` is reserved (erased slot). Valid elements are in `[1, 2^ℓ − 1]`.

## Tests (required sizes)

```bash
mvn -pl mpc4j-psu/tests -Dtest=Ba12SetOps2p5Test test
```

## Fair benchmark (PSU driver)

ASIACCS:BlaAgu12 runs through `PsoMain` with `pto_type = ASIACCS:BlaAgu12` and writes `PSU_ASIACCS:BlaAgu12_<append>_<ell>_<party>_<threads>.output` (same TSV format as balanced PSU).

Configs:

- `mpc4j-psu-tests/src/test/resources/ba12/01_ASIACCS:BlaAgu12/fair_bench_2p5.conf`
- `mpc4j-psu-tests/src/test/resources/ba12/01_ASIACCS:BlaAgu12/fair_bench_2p20.conf`

```bash
mvn -f mpc4j-psu/pom.xml install -DskipTests
scripts/run_psu_fair.sh --only ba12
python3 scripts/summarize_psu_fair_outputs.py --only-append fair_bench_2p5
```

Default operation is `ASIACCS:BlaAgu12_UNION` (MPC set union). This is **not** DH/OPRF/OT PSU.

See `ASIACCS:BlaAgu12_IMPLEMENTATION_NOTES.md` for primitive mapping and package layout.
