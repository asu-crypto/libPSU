#!/usr/bin/env python3
"""
Merge balanced PSU/PSI and unbalanced UPSU fair-bench outputs into one CSV.

Used by scripts/run_psu_fair_matrix.sh after the three benchmark phases complete.

Usage (from repo root):
  python3 scripts/summarize_psu_fair_combined.py
  python3 scripts/summarize_psu_fair_combined.py --out temp/bench/matrix/summary_combined_short.csv
"""

from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from pathlib import Path

MATRIX_BALANCED_LOGS: tuple[int, ...] = (4, 5, 6, 8, 10, 12, 16, 20)
MATRIX_UNBALANCED_APPENDS: tuple[str, ...] = ("fair_bench_unbalanced_2p20x2p10",)

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bench_output import (  # noqa: E402
    collect_records,
    expand_append_filters,
    glob_output_files,
    parse_output_basename,
    parse_small_ec_meta,
    parse_upsu_basename,
    write_csv,
)
from protocol_display_names import (  # noqa: E402
    apply_display_to_meta,
    display_protocol_name,
)


def default_matrix_appends() -> list[str]:
    appends = [f"fair_bench_2p{log}" for log in MATRIX_BALANCED_LOGS]
    appends.extend(MATRIX_UNBALANCED_APPENDS)
    return expand_append_filters(appends)


def bench_shape(append_string: str) -> str:
    match = re.match(r"^fair_bench_2p(\d+)$", append_string)
    if match:
        return f"balanced_2p{match.group(1)}"
    match = re.match(r"^fair_bench_unbalanced_2p(\d+)x2p(\d+)$", append_string)
    if match:
        return f"unbalanced_2p{match.group(1)}x2p{match.group(2)}"
    if append_string.startswith("small_ec_bench_"):
        return append_string.replace("small_ec_bench_", "small_ec_")
    return append_string


def parse_combined(stem: str) -> dict[str, str] | None:
    if stem.startswith("UPSU_"):
        meta = parse_upsu_basename(stem)
        if meta is None:
            return None
        return {
            "family": "UPSU",
            "protocol": meta["upsu_type"],
            "append_string": meta["append_string"],
            "element_bits": meta["element_bits"],
            "party_id": meta["party_id"],
            "thread_num": meta["thread_num"],
        }
    meta = parse_output_basename(stem)
    if meta is None:
        return None
    protocol = meta["psu_type"]
    small_ec = parse_small_ec_meta(meta["append_string"])
    if small_ec.get("small_ec_case"):
        protocol = f"Ours_case{small_ec['small_ec_case']}"
    family = "PSI" if stem.startswith("PSI_") else "PSU"
    return {
        "family": family,
        "protocol": protocol,
        "append_string": meta["append_string"],
        "element_bits": meta["element_bits"],
        "party_id": meta["party_id"],
        "thread_num": meta["thread_num"],
        **small_ec,
    }


def decorate_meta(meta: dict[str, str]) -> None:
    apply_display_to_meta(meta)
    meta["protocol"] = display_protocol_name(meta.get("protocol", ""))
    meta["bench_shape"] = bench_shape(meta["append_string"])


def collect_rows(
    temp_dir: Path,
    only_append: list[str],
) -> tuple[list[str], list[dict[str, str]], list[str]]:
    files = glob_output_files(temp_dir, ["PSU_*.output", "PSI_*.output", "UPSU_*.output"])
    meta_cols = [
        "source_file",
        "family",
        "bench_shape",
        "protocol",
        "fidelity_label",
        "css25_mode",
        "append_string",
        "element_bits",
        "party_id",
        "thread_num",
    ]
    all_header, merged, incomplete, skipped = collect_records(
        files,
        parse_combined,
        only_append,
        omit_protocol=lambda meta: meta.get("protocol", ""),
        after_parse=decorate_meta,
    )
    if skipped:
        print(f"  skipped {skipped} file(s) (append filter)", file=sys.stderr)
    if not merged:
        return meta_cols, [], incomplete
    assert all_header is not None
    fieldnames = meta_cols + [c for c in all_header if c not in meta_cols]
    return fieldnames, merged, incomplete


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--temp-dir", type=Path, default=Path("temp"))
    ap.add_argument(
        "--out",
        type=Path,
        default=Path("temp") / "bench" / "matrix" / "summary_combined.csv",
    )
    ap.add_argument(
        "--short-out",
        type=Path,
        default=Path("temp") / "bench" / "matrix" / "summary_combined_short.csv",
    )
    ap.add_argument(
        "--only-append",
        action="append",
        default=None,
        metavar="APPEND",
        help="Append filter (repeatable). Default: matrix fair_bench_2p* + unbalanced",
    )
    ap.add_argument("--no-short", action="store_true", help="Skip ranked short summary")
    args = ap.parse_args()

    temp_dir = args.temp_dir.resolve()
    only_append = list(args.only_append or default_matrix_appends())
    out_path = args.out.resolve()
    short_path = args.short_out.resolve()

    fieldnames, merged, incomplete = collect_rows(temp_dir, only_append)
    if not merged:
        print("No data rows to write.", file=sys.stderr)
        print(f"  append filter: {', '.join(only_append)}", file=sys.stderr)
        return 1

    write_csv(out_path, fieldnames, merged)
    print(f"Wrote {len(merged)} rows to {out_path}")
    print(f"  append filter: {', '.join(only_append)}")

    if not args.no_short:
        short_script = SCRIPT_DIR / "short_summarize_psu_fair_outputs.py"
        bridge = out_path.with_name(out_path.stem + "_for_short.csv")
        with out_path.open(newline="", encoding="utf-8") as src, bridge.open(
            "w", newline="", encoding="utf-8"
        ) as dst:
            reader = csv.DictReader(src)
            out_fields = list(reader.fieldnames or [])
            if "psu_type" not in out_fields:
                out_fields.insert(out_fields.index("protocol") + 1, "psu_type")
            writer = csv.DictWriter(dst, fieldnames=out_fields, extrasaction="ignore")
            writer.writeheader()
            for row in reader:
                row["psu_type"] = row.get("protocol", "")
                writer.writerow(row)
        subprocess.run(
            [sys.executable, str(short_script), str(bridge), str(short_path)],
            check=False,
        )
        bridge.unlink(missing_ok=True)
        if short_path.is_file():
            print(f"Short summary: {short_path}")

    if incomplete:
        print(
            f"Incomplete outputs ({len(incomplete)}): " + ", ".join(sorted(incomplete)),
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
