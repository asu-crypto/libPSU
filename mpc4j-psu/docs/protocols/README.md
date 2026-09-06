# Protocol documentation

One page per runnable protocol, titled with the **bench display name** (same labels as fair-bench CSV summaries). Internal enum IDs appear in each page.

Regenerate:

```bash
python3 scripts/gen_protocol_docs.py
```

Display names are defined in `scripts/libpsu/protocol_display_names.py`.

## Balanced PSU

- [AC:KRTW19](AC-KRTW19.md) (`AC:KRTW19`)
- [ACISP:DavCid17](ACISP-DavCid17.md) (`ACISP:DavCid17`)
- [ACNS:Frikken07](ACNS-Frikken07.md) (`ACNS:Frikken07`)
- [ASIACCS:CSSW25](ASIACCS-CSSW25.md) (`ASIACCS:CSSW25`)
- [C:KisSon05](C-KisSon05.md) (`C:KisSon05`)
- [EUROCRYPT:PisTri26](EUROCRYPT-PisTri26.md) (`EUROCRYPT:PisTri26`)
- [EUROCRYPT:PuGaoTri26](EUROCRYPT-PuGaoTri26.md) (`EUROCRYPT:PuGaoTri26`)
- [JOC:HazNis12](JOC-HazNis12.md) (`JOC:HazNis12`)
- [Ours](Ours.md) (`Ours`) — [detailed guide](../Ours_IMPLEMENTATION.md)
- [PKC:CheZhaZha24](PKC-CheZhaZha24.md) (`PKC:CheZhaZha24`)
- [PKC:GMRSS21](PKC-GMRSS21.md) (`PKC:GMRSS21`)
- [USENIX:BinYujConYanYu25](USENIX-BinYujConYanYu25.md) (`USENIX:BinYujConYanYu25`)
- [USENIX:ConYuWeiminDon23_PKE](USENIX-ConYuWeiminDon23_PKE.md) (`USENIX:ConYuWeiminDon23_PKE`)
- [USENIX:ConYuWeiminDon23_SKE](USENIX-ConYuWeiminDon23_SKE.md) (`USENIX:ConYuWeiminDon23_SKE`)
- [USENIX:HaoWan26](USENIX-HaoWan26.md) (`USENIX:HaoWan26`)
- [USENIX:JSZDG22](USENIX-JSZDG22.md) (`USENIX:JSZDG22`)
- [USENIX:JSZDG22_SFS](USENIX-JSZDG22_SFS.md) (`USENIX:JSZDG22_SFS`)
- [USENIX:YanShiHonDaw24](USENIX-YanShiHonDaw24.md) (`USENIX:YanShiHonDaw24`)

## Unbalanced UPSU

- [CCS:TCLZ23](CCS-TCLZ23.md) (`CCS:TCLZ23`)

## Garbled-circuit set ops

- [ASIACCS:BlaAgu12](ASIACCS-BlaAgu12.md) (`ASIACCS:BlaAgu12`)

## Aggregate references

- [Docs index](../README.md)
- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md) — enum → path
- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) — cost accounting
- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)
- [ADD_NEW_PROTOCOL.md](../ADD_NEW_PROTOCOL.md)
