# libPSU protocols

Per-paper protocol implementations. **Maven artifact IDs are unchanged** (`mpc4j-psu-protocol-*`) for backward compatibility; only directory paths moved.

## Layout

| Tree | Protocols |
|------|-----------|
| `balanced/` | AC:KRTW19, PKC:GMRSS21, JSZ22, ZCL23, CZZ24, ASIACCS:CSSW25, EUROCRYPT:PisTri26, ACISP:DavCid17, ACNS:Frikken07, USENIX:BinYujConYanYu25, Ours |
| `unbalanced/` | CCS:TCLZ23, USENIX:BinYujConYanYu25 (UPSU) |
| `psi/` | C:KisSon05 (PSU + legacy PSI), JOC:HazNis12 (PSU + legacy PSI), ASIACCS:BlaAgu12 |
| `malicious/` | USENIX:YanShiHonDaw24, EUROCRYPT:PuGaoTri26 |

Removed from this fork (no longer built): ZCL24/DGG25/LBL26/ZLP24 UPSU variants.

## Dependency rules

- May depend on: `libpsu-spi`, `libpsu-core`, `plugins/*`, `mpc4j-*-spi`, RPC, crypto, S2PC.
- Must **not** depend on: `apps/*`, `benchmarks/*`, `tests/*`, `libpsu-factory`.

## Adding a protocol

See `docs/ADD_NEW_PROTOCOL.md` and `docs/PROTOCOL_MAPPING.md`.
