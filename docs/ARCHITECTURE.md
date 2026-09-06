# libPSU architecture

Module layering for the `mpc4j-psu` reactor.

## Layers

| Module | Role |
|--------|------|
| `libpsu-api` | Protocol metadata (`ProtocolInfo`, SoK registry) |
| `libpsu-spi` | `PsuType` / `PsiType` / `UpsuType`, protocol interfaces |
| `libpsu-core` | Set elements, validation, bench helpers |
| `libpsu-factory` | `ProtocolRegistry`, config parsing |
| `plugins/` | Shared building blocks (COT, CCPSI, …) |
| `protocols/` | Protocol implementations |
| `apps/driver` | `PsuMain`, `PsiMain`, `UpsuMain`, `Ba12Main` (fat JAR) |
| `bench/configs/` | Fair-benchmark `.conf` trees |
| `tests/` | Integration tests |

Lower layers must not depend on higher ones. See also
[PROTOCOL_MAPPING.md](../mpc4j-psu/docs/PROTOCOL_MAPPING.md) and
[ADD_NEW_PROTOCOL.md](../mpc4j-psu/docs/ADD_NEW_PROTOCOL.md).

## Benchmarking

Drivers and multi-trial LAN/WAN workflow:
**[BENCHMARKS.md](BENCHMARKS.md)**.

Configs: `mpc4j-psu/bench/configs/`. Scripts: `scripts/libpsu/` with wrappers under `scripts/`.
