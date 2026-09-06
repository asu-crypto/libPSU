#!/usr/bin/env python3
"""
Merge mpc4j UpsuMain benchmark outputs (UPSU_*.output under temp/) into one CSV.

Each .output file is tab-separated with a header row. Filenames follow UpsuMain:

  UPSU_<UpsuMainType>_<append_string>_<element_bits>_<party_id>_<threads>.output

Usage (from repo root):
  python3 scripts/summarize_upsu_fair_outputs.py
  python3 scripts/summarize_upsu_fair_outputs.py --out temp/upsu_fair_summary.csv
  python3 scripts/summarize_upsu_fair_outputs.py --temp-dir ./temp --out ./out.csv
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bench_output import (  # noqa: E402
    collect_records,
    glob_output_files,
    parse_upsu_basename,
    write_csv,
)
from protocol_display_names import apply_display_to_meta  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--temp-dir",
        type=Path,
        default=Path("temp"),
        help="Directory containing UPSU_*.output (default: ./temp)",
    )
    ap.add_argument(
        "--out",
        type=Path,
        default=Path("temp") / "upsu_fair_summary.csv",
        help="Output CSV path (default: temp/upsu_fair_summary.csv)",
    )
    ap.add_argument(
        "--only-append",
        action="append",
        default=None,
        metavar="APPEND",
        help="Keep rows with this append_string (repeatable; prefix match)",
    )
    args = ap.parse_args()

    temp_dir: Path = args.temp_dir.resolve()
    if not temp_dir.is_dir():
        print(f"Not a directory: {temp_dir}", file=sys.stderr)
        return 1

    files = glob_output_files(temp_dir, ["UPSU_*.output"])
    if not files:
        print(f"No UPSU_*.output files under {temp_dir}", file=sys.stderr)
        return 1

    only_append: list[str] = list(args.only_append or [])
    meta_cols = [
        "source_file",
        "upsu_type",
        "append_string",
        "element_bits",
        "party_id",
        "thread_num",
    ]
    all_header, merged, incomplete, skipped_append = collect_records(
        files,
        parse_upsu_basename,
        only_append,
        omit_protocol=lambda meta: meta.get("upsu_type", ""),
        after_parse=apply_display_to_meta,
    )
    if not merged:
        print("No data rows to write.", file=sys.stderr)
        return 1

    assert all_header is not None
    fieldnames = meta_cols + [c for c in all_header if c not in meta_cols]
    out_path = args.out.resolve()
    write_csv(out_path, fieldnames, merged)

    print(f"Wrote {len(merged)} rows from {len(files)} file(s) to {out_path}")
    if skipped_append:
        print(f"  skipped {skipped_append} file(s) (append filter)", file=sys.stderr)
    if incomplete:
        print(
            f"Incomplete outputs ({len(incomplete)}): " + ", ".join(sorted(incomplete)),
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
