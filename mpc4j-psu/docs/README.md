# libPSU documentation

Documentation for the `mpc4j-psu` reactor: protocol implementations, fair benchmarks, and integration.

## Quick links

| Topic | Document |
|-------|----------|
| **All protocols (by display name)** | [protocols/README.md](protocols/README.md) |
| Enum → module path | [PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md) |
| Implementation + benchmark cost | [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) |
| OT / Base OT audit | [OT_BASE_COST_AUDIT.md](OT_BASE_COST_AUDIT.md) |
| Add a new protocol | [ADD_NEW_PROTOCOL.md](ADD_NEW_PROTOCOL.md) |
| Library usage | [LIBRARY_USAGE.md](LIBRARY_USAGE.md) |
| Reactor layout | [ARCHITECTURE.md](ARCHITECTURE.md) |

## Display names

Benchmark CSVs and summaries use paper-style labels (e.g. `PKC:CheZhaZha24`, `USENIX:HaoWan26`). The mapping from internal enum IDs is in `scripts/libpsu/protocol_display_names.py`.

## Balanced PSU (display names)

- [AC:KRTW19](protocols/AC-KRTW19.md) — `AC:KRTW19`
- [PKC:GMRSS21](protocols/PKC-GMRSS21.md) — `PKC:GMRSS21`
- [USENIX:JSZDG22](protocols/USENIX-JSZDG22.md) — `USENIX:JSZDG22`
- [USENIX:JSZDG22_SFS](protocols/USENIX-JSZDG22_SFS.md) — `USENIX:JSZDG22_SFS`
- [USENIX:ConYuWeiminDon23_PKE](protocols/USENIX-ConYuWeiminDon23_PKE.md) — `USENIX:ConYuWeiminDon23_PKE`
- [USENIX:ConYuWeiminDon23_SKE](protocols/USENIX-ConYuWeiminDon23_SKE.md) — `USENIX:ConYuWeiminDon23_SKE`
- [PKC:CheZhaZha24](protocols/PKC-CheZhaZha24.md) — `PKC:CheZhaZha24`
- [ASIACCS:CSSW25](protocols/ASIACCS-CSSW25.md) — `ASIACCS:CSSW25`
- [USENIX:BinYujConYanYu25](protocols/USENIX-BinYujConYanYu25.md) — `USENIX:BinYujConYanYu25`
- [USENIX:HaoWan26](protocols/USENIX-HaoWan26.md) — `USENIX:HaoWan26`
- [EUROCRYPT:PisTri26](protocols/EUROCRYPT-PisTri26.md) — `EUROCRYPT:PisTri26`
- [ACISP:DavCid17](protocols/ACISP-DavCid17.md) — `ACISP:DavCid17`
- [ACNS:Frikken07](protocols/ACNS-Frikken07.md) — `ACNS:Frikken07`
- [USENIX:YanShiHonDaw24](protocols/USENIX-YanShiHonDaw24.md) — `USENIX:YanShiHonDaw24`
- [EUROCRYPT:PuGaoTri26](protocols/EUROCRYPT-PuGaoTri26.md) — `EUROCRYPT:PuGaoTri26`
- [Ours](protocols/Ours.md) — `Ours`
- [C:KisSon05](protocols/C-KisSon05.md) — `C:KisSon05`
- [JOC:HazNis12](protocols/JOC-HazNis12.md) — `JOC:HazNis12`

## Other families

- **Unbalanced UPSU:** [CCS:TCLZ23](protocols/CCS-TCLZ23.md) — `CCS:TCLZ23`
- **Garbled-circuit set ops:** [ASIACCS:BlaAgu12](protocols/ASIACCS-BlaAgu12.md) — `ASIACCS:BlaAgu12`
