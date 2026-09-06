"""Shared parsing and merge helpers for PSU_/PSI_/UPSU_ *.output files."""

from __future__ import annotations

import csv
import re
import sys
from collections.abc import Callable
from pathlib import Path

from protocol_display_names import CANONICAL_PROTOCOL_IDS, to_file_token, should_omit_from_summary

TIMEOUT_PLACEHOLDER = "--"

_PSI_IDS = ("C:KisSon05", "JOC:HazNis12")
_UPSU_IDS = ("CCS:TCLZ23", "USENIX:BinYujConYanYu25")


def _prefixes(ids: tuple[str, ...]) -> tuple[str, ...]:
    return tuple(sorted((to_file_token(i) for i in ids), key=len, reverse=True))


PSI_TYPE_PREFIXES = _prefixes(_PSI_IDS)
UPSU_TYPE_PREFIXES = _prefixes(_UPSU_IDS)
PSU_TYPE_PREFIXES = _prefixes(
    tuple(i for i in CANONICAL_PROTOCOL_IDS if i not in _PSI_IDS and i not in _UPSU_IDS)
)
# Legacy file tokens for archived bench outputs (longest first).
PSU_TYPE_PREFIXES = PSU_TYPE_PREFIXES + (
    "CZZ24_CW_OPRF",
    "SMALL_EC_ELLIGATOR_PSU",
    "HAO_WAN2026",
    "JSZG24_BECRG_PSU",
    "PGT26_2M",
    "JSZ22_SFC",
    "JSZ22_SFS",
    "ZCL23_PKE",
    "ZCL23_SKE",
    "GMR21",
    "KRTW19",
    "CSS25",
    "TBZ25",
    "PT26",
    "DC17",
    "F07",
)

FAIR_BENCH_LOG_RE = re.compile(r"^fair_bench_2p(\d+)$")

# Mirrors small_ec_fair_lib.sh CASES (longest suffix first).
SMALL_EC_CASE_SPECS: tuple[tuple[str, str, str, str, str, str, str], ...] = (
    (
        "case8_fp_auto_async_parallel",
        "8",
        "TRUNCATED_W_PROBABILISTIC",
        "auto",
        "true",
        "true",
        "fp auto λ, async + parallel",
    ),
    (
        "case7_fp64_no_async_parallel",
        "7",
        "TRUNCATED_W_PROBABILISTIC",
        "64",
        "false",
        "true",
        "fp64, no async, parallel EC",
    ),
    (
        "case6_fp64_async_no_parallel",
        "6",
        "TRUNCATED_W_PROBABILISTIC",
        "64",
        "true",
        "false",
        "fp64, async W, no parallel",
    ),
    (
        "case5_fp64_no_async_no_parallel",
        "5",
        "TRUNCATED_W_PROBABILISTIC",
        "64",
        "false",
        "false",
        "fp64, no async, no parallel",
    ),
    (
        "case4_exact_async_parallel",
        "4",
        "FULL_POINT_EXACT",
        "n/a",
        "true",
        "true",
        "exact, async W + parallel EC",
    ),
    (
        "case3_exact_no_async_parallel",
        "3",
        "FULL_POINT_EXACT",
        "n/a",
        "false",
        "true",
        "exact, no async, parallel EC",
    ),
    (
        "case2_exact_async_no_parallel",
        "2",
        "FULL_POINT_EXACT",
        "n/a",
        "true",
        "false",
        "exact, async W, no parallel",
    ),
    (
        "case1_exact_no_async_no_parallel",
        "1",
        "FULL_POINT_EXACT",
        "n/a",
        "false",
        "false",
        "exact, no async, no parallel",
    ),
)

SMALL_EC_META_COLS: tuple[str, ...] = (
    "small_ec_case",
    "small_ec_w_compare_mode",
    "small_ec_fingerprint_bits",
    "small_ec_async_precompute_w",
    "small_ec_parallel_ec",
    "small_ec_case_description",
)

TIMEOUT_IDENTITY_COLUMNS: dict[str, str] = {
    "Party ID": "party_id",
    "Thread Num": "thread_num",
}

TIMEOUT_STRUCTURE_COLUMNS: dict[str, str] = {
    "Server Set Size": "server_set_size",
    "Client Set Size": "client_set_size",
}


def expand_append_filters(only_append: list[str]) -> list[str]:
    """Also include small_ec_bench_2p<LOG> next to each fair_bench_2p<LOG> filter."""
    expanded: list[str] = []
    seen: set[str] = set()
    for prefix in only_append:
        if prefix not in seen:
            expanded.append(prefix)
            seen.add(prefix)
        match = FAIR_BENCH_LOG_RE.match(prefix)
        if not match:
            continue
        small_ec_prefix = f"small_ec_bench_2p{match.group(1)}"
        if small_ec_prefix not in seen:
            expanded.append(small_ec_prefix)
            seen.add(small_ec_prefix)
    return expanded


def parse_small_ec_meta(append_string: str) -> dict[str, str]:
    empty = {col: "" for col in SMALL_EC_META_COLS}
    if not append_string.startswith("small_ec_bench_"):
        return empty
    for suffix, case_id, w_mode, fp_bits, async_w, parallel_ec, desc in SMALL_EC_CASE_SPECS:
        if suffix in append_string:
            return {
                "small_ec_case": case_id,
                "small_ec_w_compare_mode": w_mode,
                "small_ec_fingerprint_bits": fp_bits,
                "small_ec_async_precompute_w": async_w,
                "small_ec_parallel_ec": parallel_ec,
                "small_ec_case_description": desc,
            }
    return empty


def _parse_prefixed_stem(
    stem: str, family_prefix: str, prefixes: tuple[str, ...]
) -> dict[str, str] | None:
    if not stem.startswith(family_prefix):
        return None
    body = stem[len(family_prefix) :]
    for pto in prefixes:
        token = pto + "_"
        if not body.startswith(token):
            continue
        tail = body[len(token) :]
        parts = tail.rsplit("_", 3)
        if len(parts) != 4:
            return None
        append, bits_s, party_s, threads_s = parts
        if not (bits_s.isdigit() and party_s.isdigit() and threads_s.isdigit()):
            return None
        return {
            "protocol": pto,
            "append_string": append,
            "element_bits": bits_s,
            "party_id": party_s,
            "thread_num": threads_s,
        }
    return None


def parse_output_basename(stem: str) -> dict[str, str] | None:
    """Parse PSU_*/PSI_* stems; protocol is also stored as psu_type for existing CSVs."""
    if stem.startswith("PSI_"):
        meta = _parse_prefixed_stem(stem, "PSI_", PSI_TYPE_PREFIXES)
    elif stem.startswith("PSU_"):
        meta = _parse_prefixed_stem(stem, "PSU_", PSU_TYPE_PREFIXES)
    else:
        return None
    if meta is None:
        return None
    meta["psu_type"] = meta["protocol"]
    meta.update(parse_small_ec_meta(meta["append_string"]))
    return meta


def parse_upsu_basename(stem: str) -> dict[str, str] | None:
    meta = _parse_prefixed_stem(stem, "UPSU_", UPSU_TYPE_PREFIXES)
    if meta is None:
        return None
    meta["upsu_type"] = meta["protocol"]
    return meta


def infer_set_sizes(append_string: str) -> tuple[str, str]:
    match = re.match(r"^fair_bench_2p(\d+)$", append_string)
    if match:
        n = str(1 << int(match.group(1)))
        return n, n
    match = re.match(r"^fair_bench_unbalanced_2p(\d+)x2p(\d+)$", append_string)
    if match:
        return str(1 << int(match.group(1))), str(1 << int(match.group(2)))
    match = re.match(r"^small_ec_bench_2p(\d+)", append_string)
    if match:
        n = str(1 << int(match.group(1)))
        return n, n
    return TIMEOUT_PLACEHOLDER, TIMEOUT_PLACEHOLDER


def build_timeout_row(
    header: list[str],
    meta: dict[str, str],
    source_file: str,
) -> dict[str, str]:
    server_size, client_size = infer_set_sizes(meta.get("append_string", ""))
    enriched = {
        **meta,
        "server_set_size": server_size,
        "client_set_size": client_size,
    }
    rec: dict[str, str] = {"source_file": source_file, **meta}
    for col in header:
        if col in TIMEOUT_IDENTITY_COLUMNS:
            rec[col] = enriched.get(TIMEOUT_IDENTITY_COLUMNS[col], TIMEOUT_PLACEHOLDER)
        elif col in TIMEOUT_STRUCTURE_COLUMNS:
            rec[col] = enriched.get(TIMEOUT_STRUCTURE_COLUMNS[col], TIMEOUT_PLACEHOLDER)
        else:
            rec[col] = TIMEOUT_PLACEHOLDER
    return rec


def read_tsv_rows(path: Path) -> tuple[list[str], list[list[str]]]:
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    if not text:
        return [], []
    lines = text.splitlines()
    if not lines:
        return [], []
    header = lines[0].split("\t")
    rows: list[list[str]] = []
    for line in lines[1:]:
        if not line.strip():
            continue
        rows.append(line.split("\t"))
    return header, rows


def append_matches(append_string: str, only_append: list[str]) -> bool:
    if not only_append:
        return True
    return any(append_string == p or append_string.startswith(p + "_") for p in only_append)


def glob_output_files(temp_dir: Path, patterns: list[str]) -> list[Path]:
    files: list[Path] = []
    seen: set[Path] = set()
    for pattern in patterns:
        for fp in sorted(temp_dir.glob(pattern)):
            if fp not in seen:
                seen.add(fp)
                files.append(fp)
    return files


def collect_records(
    files: list[Path],
    parse_meta: Callable[[str], dict[str, str] | None],
    only_append: list[str],
    *,
    omit_protocol: Callable[[dict[str, str]], str],
    after_parse: Callable[[dict[str, str]], None] | None = None,
) -> tuple[list[str] | None, list[dict[str, str]], list[str], int]:
    all_header: list[str] | None = None
    merged: list[dict[str, str]] = []
    incomplete: list[str] = []
    skipped_append = 0

    for fp in files:
        meta = parse_meta(fp.stem)
        if meta is None:
            print(f"skip (unparsed name): {fp.name}", file=sys.stderr)
            continue
        if should_omit_from_summary(omit_protocol(meta)):
            continue
        if after_parse is not None:
            after_parse(meta)
        if not append_matches(meta["append_string"], only_append):
            skipped_append += 1
            continue
        header, rows = read_tsv_rows(fp)
        if not header:
            print(f"skip (empty file): {fp.name}", file=sys.stderr)
            continue
        if all_header is None:
            all_header = header
        elif header != all_header:
            print(
                f"warning: header mismatch in {fp.name}, using first file's columns",
                file=sys.stderr,
            )
        if not rows:
            incomplete.append(fp.name)
            print(
                f"timeout/incomplete (no data row — using {TIMEOUT_PLACEHOLDER}): {fp.name}",
                file=sys.stderr,
            )
            merged.append(build_timeout_row(header, meta, fp.name))
            continue
        for cells in rows:
            if len(cells) != len(header):
                print(
                    f"warning: column count {len(cells)} != {len(header)} in {fp.name}",
                    file=sys.stderr,
                )
            row_map = dict(zip(header, cells + [""] * max(0, len(header) - len(cells))))
            merged.append({"source_file": fp.name, **meta, **row_map})

    return all_header, merged, incomplete, skipped_append


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for rec in rows:
            writer.writerow({k: rec.get(k, "") for k in fieldnames})
