"""Canonical protocol identifiers for benchmark summaries and driver logs."""

from __future__ import annotations

from pathlib import Path

# Single source of truth (mirrors edu.alibaba.libpsu.spi.ProtocolNames in Java).
CANONICAL_PROTOCOL_IDS: tuple[str, ...] = (
    "AC:KRTW19",
    "PKC:GMRSS21",
    "USENIX:JSZDG22",
    "USENIX:JSZDG22_SFS",
    "USENIX:ConYuWeiminDon23_PKE",
    "USENIX:ConYuWeiminDon23_SKE",
    "PKC:CheZhaZha24",
    "ASIACCS:CSSW25",
    "USENIX:BinYujConYanYu25",
    "USENIX:HaoWan26",
    "EUROCRYPT:PisTri26",
    "ACISP:DavCid17",
    "ACNS:Frikken07",
    "USENIX:YanShiHonDaw24",
    "EUROCRYPT:PuGaoTri26",
    "Ours",
    "C:KisSon05",
    "JOC:HazNis12",
    "CCS:TCLZ23",
    "ASIACCS:BlaAgu12",
)

# Legacy enum / file-token aliases -> canonical wire id.
LEGACY_ALIASES: dict[str, str] = {
    "KRTW19": "AC:KRTW19",
    "GMR21": "PKC:GMRSS21",
    "GMR21_PROXY": "PKC:GMRSS21",
    "JSZ22_SFC": "USENIX:JSZDG22",
    "JSZ22_SFC_PROXY": "USENIX:JSZDG22",
    "JSZ22_SFS": "USENIX:JSZDG22_SFS",
    "JSZ22_SFS_PROXY": "USENIX:JSZDG22_SFS",
    "ZCL23_PKE": "USENIX:ConYuWeiminDon23_PKE",
    "ZCL23_SKE": "USENIX:ConYuWeiminDon23_SKE",
    "CZZ24_CW_OPRF": "PKC:CheZhaZha24",
    "CSS25": "ASIACCS:CSSW25",
    "CSS25_PROXY": "ASIACCS:CSSW25",
    "TBZ25": "USENIX:BinYujConYanYu25",
    "HAO_WAN2026": "USENIX:HaoWan26",
    "PT26": "EUROCRYPT:PisTri26",
    "DC17": "ACISP:DavCid17",
    "F07": "ACNS:Frikken07",
    "JSZG24_BECRG_PSU": "USENIX:YanShiHonDaw24",
    "PGT26_2M": "EUROCRYPT:PuGaoTri26",
    "PGT26_1M": "EUROCRYPT:PuGaoTri26",
    "SMALL_EC_ELLIGATOR_PSU": "Ours",
    "CUSTOM_SMALL_EC_ELLIGATOR_PSU": "Ours",
    "KS05": "C:KisSon05",
    "HN12": "JOC:HazNis12",
    "HN12_PSI": "JOC:HazNis12",
    "TCL23": "CCS:TCLZ23",
    "BA12": "ASIACCS:BlaAgu12",
}

# Build file-token aliases (colon -> hyphen) for old bench outputs.
for _canonical in CANONICAL_PROTOCOL_IDS:
    LEGACY_ALIASES.setdefault(_canonical.replace(":", "-"), _canonical)
for _legacy, _canonical in list(LEGACY_ALIASES.items()):
    LEGACY_ALIASES.setdefault(_legacy.replace(":", "-"), _canonical)


def to_file_token(protocol_id: str) -> str:
    return protocol_id.replace(":", "-")


def canonicalize(raw: str) -> str:
    if not raw:
        return raw
    trimmed = raw.strip()
    return LEGACY_ALIASES.get(trimmed, trimmed)


def display_protocol_name(value: str) -> str:
    """Return canonical wire id; accepts legacy names and file tokens."""
    return canonicalize(value)


def should_omit_from_summary(_internal: str) -> bool:
    return False


def apply_display_to_meta(meta: dict[str, str]) -> None:
    for key in ("psu_type", "upsu_type", "psi_type", "protocol"):
        if key in meta and meta[key]:
            meta[key] = display_protocol_name(meta[key])


def internal_protocol_from_meta(meta: dict[str, str]) -> str:
    for key in ("psu_type", "upsu_type", "psi_type", "protocol"):
        if meta.get(key):
            return meta[key]
    return ""


def summary_protocol_name(value: str) -> str | None:
    if not value:
        return None
    return display_protocol_name(value)


def bench_output_prefixes() -> tuple[str, ...]:
    """Longest-first file tokens for parsing PSU_/PSI_/UPSU_ output basenames."""
    tokens = sorted({to_file_token(pid) for pid in CANONICAL_PROTOCOL_IDS}, key=len, reverse=True)
    # Legacy tokens still present in old trial archives.
    legacy_tokens = sorted(
        {
            k.replace(":", "-")
            for k in LEGACY_ALIASES
            if ":" not in k and "_" in k or k.endswith("_PROXY")
        },
        key=len,
        reverse=True,
    )
    merged: list[str] = []
    for token in [*tokens, *legacy_tokens]:
        if token not in merged:
            merged.append(token)
    return tuple(merged)


def main() -> int:
    import sys

    value = sys.argv[1] if len(sys.argv) > 1 else ""
    print(display_protocol_name(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
