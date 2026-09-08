# libPSU protocols

Per-paper protocol implementations. **Maven artifact IDs are unchanged** (`mpc4j-psu-protocol-*`) for backward compatibility; only directory paths moved.

## Layout

| Tree | Protocols |
|------|-----------|
| `balanced/` | `krtw19`, `gmr21`, `jsz22`, `zcl23`, `czz24`, `cssw25`, `dc17`, `fri07`, `ours`, `tbz25`, `hwy26`, `pt26` |
| `unbalanced/` | `tcl23`, `tbz25` |
| `psi/` | `ks05`, `hn12`, `ba12` |
| `malicious/` | `jszg24`, `pgt26` |

Removed from this fork (no longer built): ZCL24/DGG25/LBL26/ZLP24 UPSU variants.

## Dependency rules

- May depend on: `libpsu-spi`, `libpsu-core`, `plugins/*`, `mpc4j-*-spi`, RPC, crypto, S2PC.
- Must **not** depend on: `apps/*`, `benchmarks/*`, `tests/*`, `libpsu-factory`.

## Adding a protocol

See `docs/ADD_NEW_PROTOCOL.md` and `docs/PROTOCOL_MAPPING.md`.
