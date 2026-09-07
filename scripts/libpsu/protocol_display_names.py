"""Canonical protocol identifiers for benchmark summaries and driver logs."""

from __future__ import annotations

from pathlib import Path

# Single source of truth (mirrors edu.alibaba.libpsu.api.ProtocolNames in Java).
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
    "C:KisSon05",
    "JOC:HazNis12",
    "USENIX:YanShiHonDaw24",
    "EUROCRYPT:PuGaoTri26",
    "Ours",
    "CCS:TCLZ23",
    "ASIACCS:BlaAgu12",
)

# Legacy enum / file-token aliases -> canonical wire id.
# NOTE: PGT26_1M is intentionally NOT aliased to two-sided EUROCRYPT:PuGaoTri26.
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
    "SMALL_EC_ELLIGATOR_PSU": "Ours",
    "CUSTOM_SMALL_EC_ELLIGATOR_PSU": "Ours",
    "KS05": "C:KisSon05",
    "HN12": "JOC:HazNis12",
    "HN12_PSI": "JOC:HazNis12",
    "TCL23": "CCS:TCLZ23",
    "BA12": "ASIACCS:BlaAgu12",
}

# Historical one-sided PGT26 file tokens (summaries only; never merge with 2M).
HISTORICAL_PGT26_1M_TOKENS: frozenset[str] = frozenset({"PGT26_1M", "PGT26-1M"})

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
    if trimmed in HISTORICAL_PGT26_1M_TOKENS:
        return "PGT26_1M"
    return LEGACY_ALIASES.get(trimmed, trimmed)


def normalize_css25_mode(raw: str | None) -> str:
    """Normalize css25_mode / legacy boolean flags without inventing paper-exact."""
    if raw is None or str(raw).strip() == "":
        return "PROXY"
    token = str(raw).strip().upper()
    if token in {"TRUE", "1", "YES", "PAPER_COMPARISON_PROXY", "RS21", "COMPARISON"}:
        return "PAPER_COMPARISON_PROXY"
    if token in {"FALSE", "0", "NO", "PROXY", "PSTY19"}:
        return "PROXY"
    if token in {"PAPER_EXACT", "EXACT"}:
        return "PAPER_EXACT"
    return token


def display_protocol_name(value: str) -> str:
    """Return canonical wire id; accepts legacy names and file tokens."""
    return canonicalize(value)


def fidelity_label(protocol_id: str, *, css25_mode: str | None = None, functionality: str | None = None) -> str:
    """Human-visible fidelity label for summaries (never implies unsupported paper-exact)."""
    canonical = canonicalize(protocol_id)
    if canonical == "PGT26_1M":
        return "PGT26_1M [internal one-sided; not public PGT26-2M]"
    if canonical == "ASIACCS:CSSW25":
        mode = normalize_css25_mode(css25_mode)
        if mode == "PAPER_EXACT":
            return "ASIACCS:CSSW25 [paper-exact UNSUPPORTED]"
        if mode == "PAPER_COMPARISON_PROXY":
            return "ASIACCS:CSSW25 [RS21 paper-comparison proxy]"
        return "ASIACCS:CSSW25 [PSTY19 runnable proxy]"
    if canonical == "USENIX:BinYujConYanYu25" and (functionality or "").upper() == "UPSU":
        return "USENIX:BinYujConYanYu25 [linear balanced-pnMCRG UPSU wrapper; not paper FHE Fig.14]"
    if canonical == "USENIX:HaoWan26":
        return "USENIX:HaoWan26 [ePSU-fast only; not ePSU-low]"
    if canonical == "Ours":
        return "Ours [leakage baseline; not standard one-sided PSU]"
    if canonical == "JOC:HazNis12":
        return "JOC:HazNis12 [experimental ideal-PRF debug; two-sided]"
    return canonical


def should_omit_from_summary(_internal: str) -> bool:
    return False


def apply_display_to_meta(meta: dict[str, str]) -> None:
    for key in ("psu_type", "upsu_type", "psi_type", "protocol"):
        if key in meta and meta[key]:
            meta[key] = display_protocol_name(meta[key])
    proto = internal_protocol_from_meta(meta)
    if not proto:
        return
    css25_mode = meta.get("css25_mode")
    if css25_mode is None and "css25_paper_comparison" in meta:
        css25_mode = meta.get("css25_paper_comparison")
    # Absent/false legacy flags normalize to PSTY19 PROXY; never invent paper-exact.
    if canonicalize(proto) == "ASIACCS:CSSW25":
        meta["css25_mode"] = normalize_css25_mode(css25_mode)
    elif css25_mode is not None:
        meta["css25_mode"] = normalize_css25_mode(css25_mode)
    meta["fidelity_label"] = fidelity_label(
        proto,
        css25_mode=meta.get("css25_mode"),
        functionality=meta.get("functionality") or ("UPSU" if meta.get("upsu_type") else "PSU"),
    )


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
    legacy_tokens = sorted(
        {
            k.replace(":", "-")
            for k in list(LEGACY_ALIASES) + list(HISTORICAL_PGT26_1M_TOKENS)
            if ":" not in k and "_" in k or k.endswith("_PROXY") or k in HISTORICAL_PGT26_1M_TOKENS
        },
        key=len,
        reverse=True,
    )
    merged: list[str] = []
    for token in [*tokens, *legacy_tokens, "PGT26_1M"]:
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
