#!/usr/bin/env python3
"""
Merge mpc4j PsuMain benchmark outputs (PSU_*.output under temp/) into one CSV.

Each .output file is tab-separated with a header row. Filenames follow PsuMain:

  PSU_<PsuType>_<append_string>_<element_bits>_<party_id>_<threads>.output

By default fair_bench_2p5 and fair_bench_2p20 rows are included, plus every
Ours small_ec_bench_* mode case for the same log sizes.

Usage (from repo root):
  python3 scripts/summarize_psu_fair_outputs.py
  python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5_2p20.csv
  python3 scripts/summarize_psu_fair_outputs.py --include-all-appends
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bench_output import (  # noqa: E402
    SMALL_EC_META_COLS,
    collect_records,
    expand_append_filters,
    glob_output_files,
    parse_output_basename,
    write_csv,
)
from protocol_display_names import (  # noqa: E402
    apply_display_to_meta,
    internal_protocol_from_meta,
)

DEFAULT_APPEND_STRINGS: tuple[str, ...] = (
    "fair_bench_2p5",
    "fair_bench_2p20",
)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--temp-dir",
        type=Path,
        default=Path("temp"),
        help="Directory containing PSU_*.output (default: ./temp)",
    )
    ap.add_argument(
        "--out",
        type=Path,
        default=Path("temp") / "psu_fair_summary_2p5_2p20.csv",
        help="Output CSV path (default: temp/psu_fair_summary_2p5_2p20.csv)",
    )
    ap.add_argument(
        "--append-glob",
        action="append",
        default=[],
        metavar="GLOB",
        help="Extra output globs under --temp-dir (e.g. PSI_*.output); may repeat",
    )
    ap.add_argument(
        "--only-append",
        action="append",
        default=None,
        metavar="APPEND",
        help=(
            "Keep rows with this append_string (repeatable). "
            f"Default: {', '.join(DEFAULT_APPEND_STRINGS)} "
            "(fair_bench_* also pulls in small_ec_bench_* for the same log size)"
        ),
    )
    ap.add_argument(
        "--include-all-appends",
        action="store_true",
        help="Include every append_string (fair_bench_10, fair_bench_2p10, legacy fair_bench, etc.)",
    )
    ap.add_argument(
        "--no-small-ec-modes",
        action="store_true",
        help="Do not auto-include small_ec_bench_* when filtering fair_bench_2p*",
    )
    args = ap.parse_args()

    if args.include_all_appends:
        only_append: list[str] = []
    elif args.only_append is not None:
        only_append = args.only_append
    else:
        only_append = list(DEFAULT_APPEND_STRINGS)

    if only_append and not args.no_small_ec_modes:
        only_append = expand_append_filters(only_append)

    temp_dir: Path = args.temp_dir.resolve()
    if not temp_dir.is_dir():
        print(f"Not a directory: {temp_dir}", file=sys.stderr)
        return 1

    files = glob_output_files(temp_dir, ["PSU_*.output", *args.append_glob])
    if not files:
        print(
            f"No output files matching {['PSU_*.output', *args.append_glob]!r} under {temp_dir}",
            file=sys.stderr,
        )
        return 1

    meta_cols = [
        "source_file",
        "psu_type",
        "fidelity_label",
        "css25_mode",
        "append_string",
        *SMALL_EC_META_COLS,
        "element_bits",
        "party_id",
        "thread_num",
    ]
    all_header, merged, incomplete, skipped_append = collect_records(
        files,
        parse_output_basename,
        only_append,
        omit_protocol=internal_protocol_from_meta,
        after_parse=apply_display_to_meta,
    )
    if not merged:
        print("No data rows to write.", file=sys.stderr)
        if skipped_append:
            print(
                f"({skipped_append} file(s) skipped by append filter; "
                f"active: {only_append or 'all'})",
                file=sys.stderr,
            )
        return 1

    assert all_header is not None
    fieldnames = meta_cols + [c for c in all_header if c not in meta_cols]
    write_csv(args.out.resolve(), fieldnames, merged)

    filter_note = "all append strings" if not only_append else ", ".join(only_append)
    print(f"Wrote {len(merged)} rows from {len(files)} file(s) to {args.out.resolve()}")
    print(f"  append filter: {filter_note}")
    small_ec_rows = sum(
        1 for rec in merged if rec.get("append_string", "").startswith("small_ec_bench_")
    )
    if small_ec_rows:
        print(f"  SMALL_EC_ELLIGATOR mode rows: {small_ec_rows}")
    if skipped_append:
        print(f"  skipped {skipped_append} file(s) (other append_string values)", file=sys.stderr)
    if incomplete:
        print(
            f"Incomplete outputs ({len(incomplete)}): " + ", ".join(sorted(incomplete)),
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
