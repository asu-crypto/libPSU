# Adding a new protocol to libPSU

See also [../../docs/ARCHITECTURE.md §13 and §17](../../docs/ARCHITECTURE.md#17-adding-a-new-protocol-libpsu-checklist).

## 0. Where code lives (current vs target)

| | |
|--|--|
| **Current physical protocol path** | `mpc4j-psu/protocols/<category>/<shortname>/` |
| **Target libPSU Stage-2 path** | `protocols/<category>/<shortname>/` (same tree under `mpc4j-psu/`) |
| **Artifact ID** | `mpc4j-psu-protocol-<shortname>` (unchanged Maven coordinate) |
| **Driver** | `mpc4j-psu/apps/driver/` (legacy name `mpc4j-psu-driver`) |
| **Config utils** | `mpc4j-psu/libpsu-factory/` |
| **Tests** | `mpc4j-psu/tests/` |
| **Status** | New protocols should be added directly under `protocols/`; do not recreate top-level `mpc4j-psu-protocol-*` directories outside `protocols/`. |

Full table: [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md §0](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md#0-module-path-convention).

## 1. Register metadata (`libpsu-api`)

Add a row in `ProtocolMetadataRegistry`:

- `protocolName` — must match the benchmark config enum string exactly
- `citationKey`, `year`, `ProtocolFamily`, `OutputModel`, `LibPsuSecurityModel`, `SetSizeMode`, `PrimitiveKind`
- `ProtocolReadiness` — use `STABLE` for default library selection, `EXPERIMENTAL` for opt-in variants, and `DISABLED` for known-incomplete variants
- `minimumInputSetSize` — declare protocol-specific limits; the public API itself supports empty and singleton inputs
- `mavenModule` — e.g. `mpc4j-psu-protocol-myproto` (artifact ID; path under `protocols/balanced/myproto/`)

## 2. SPI type enum

Add to `PsuType`, `PsiType`, or `UpsuType` in `libpsu-spi` (legacy `mpc4j-psu-*-spi` modules are compatibility shims).

## 3. Protocol module

Create `protocols/<category>/<shortname>/` with:

- `*PtoDesc`, `*Config`, `*Server` / `*Client` (or UPSU Sender/Receiver)
- Register in `protocols/<category>/pom.xml`
- **Do not** depend on `libpsu-factory`, `apps/*`, or `tests/*`

## 4. Factory dispatch

- `PsuFactory` / `PsiFactory` / `UpsuFactory` in `mpc4j-psu-balanced` / `psi` / `unbalanced`
- `PsuConfigUtils` / … in **`libpsu-factory`**
- `ProtocolRegistry` descriptor entry with metadata, aliases, config type, and config parser wiring

## 5. Tests and benchmarks

- Correctness: `tests/src/test/java/...` at \(2^5\)
- Fair bench: `bench/configs/psu/NN_<NAME>/fair_bench_2p5.conf`
- Unique TCP ports

## 6. Documentation

1. Add a **display name** in `scripts/libpsu/protocol_display_names.py` (bench CSV label, e.g. `PKC:MyProto24`).
2. Add an entry to `scripts/gen_protocol_docs.py` (`PROTOCOLS` list) and run:
   ```bash
   python3 scripts/gen_protocol_docs.py
   ```
3. Add a row in [PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md).
4. If OT-bearing, add rows to [OT_BASE_COST_AUDIT.md](OT_BASE_COST_AUDIT.md) and a cost subsection in [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md).
