#!/usr/bin/env python3
"""Average ranked short PSU fair-bench CSVs across multiple trial runs."""

from __future__ import annotations

import argparse
import csv
import statistics
import sys
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bench_output import TIMEOUT_PLACEHOLDER  # noqa: E402


KEY_COLS = (
    "Protocol",
    "Append String",
    "Element Bits",
    "Server Set Size",
    "Client Set Size",
    "Thread Num",
)

NUMERIC_COLS = (
    "Party Count",
    "Party 0 Time(ms)",
    "Party 1 Time(ms)",
    "Total Time(ms)",
    "Total Time(s)",
    "Init Comm(Bytes)",
    "PTO Comm(Bytes)",
    "Total Comm(Bytes)",
    "Init Comm(MB)",
    "PTO Comm(MB)",
    "Total Comm(MB)",
)


def is_timeout_value(value: str) -> bool:
    return str(value).strip() == TIMEOUT_PLACEHOLDER


def to_float(value: str):
    text = str(value).strip()
    if not text or is_timeout_value(text):
        return None
    try:
        return float(text)
    except ValueError:
        return None


def fmt_decimal(value: float, digits: int) -> str:
    quant = Decimal("1." + "0" * digits)
    return str(Decimal(str(value)).quantize(quant, rounding=ROUND_HALF_UP))


def row_key(row: dict) -> tuple:
    return tuple(row.get(col, "").strip() for col in KEY_COLS)


def average_rows(rows: list[dict], fieldnames: list[str]) -> dict:
    out = dict(rows[0])
    if any(is_timeout_value(row.get("Total Time(s)", "")) for row in rows):
        for col in NUMERIC_COLS:
            if col in fieldnames:
                out[col] = TIMEOUT_PLACEHOLDER
        return out

    for col in NUMERIC_COLS:
        if col not in fieldnames:
            continue
        vals = [to_float(row.get(col, "")) for row in rows]
        vals = [v for v in vals if v is not None]
        if not vals:
            out[col] = ""
            continue
        mean = statistics.mean(vals)
        if col.endswith("(ms)") or col in ("Party Count",):
            out[col] = str(int(round(mean)))
        elif col.endswith("(Bytes)"):
            out[col] = str(int(round(mean)))
        elif col.endswith("(s)"):
            out[col] = fmt_decimal(mean, 3)
        elif col.endswith("(MB)"):
            out[col] = fmt_decimal(mean, 6)
        else:
            out[col] = fmt_decimal(mean, 6)
    return out


def average_short_csvs(input_paths: list[Path], output_csv: Path) -> int:
    groups: dict[tuple, list[dict]] = defaultdict(list)
    fieldnames = None

    for path in input_paths:
        with path.open("r", newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            if not reader.fieldnames:
                continue
            if fieldnames is None:
                fieldnames = list(reader.fieldnames)
            for row in reader:
                if not row.get("Protocol", "").strip():
                    continue
                groups[row_key(row)].append(row)

    if not fieldnames or not groups:
        print(f"No rows to average from {len(input_paths)} input file(s).", file=sys.stderr)
        return 1

    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for key in sorted(groups.keys(), key=lambda k: (k[0], k[1])):
            writer.writerow(average_rows(groups[key], fieldnames))

    print(f"Wrote {output_csv} ({len(groups)} protocol row(s) from {len(input_paths)} trial file(s))")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", help="Short summary CSV paths to average")
    parser.add_argument("-o", "--out", required=True, help="Output averaged short CSV path")
    args = parser.parse_args()
    paths = [Path(p) for p in args.inputs]
    missing = [p for p in paths if not p.is_file()]
    if missing:
        for path in missing:
            print(f"missing input: {path}", file=sys.stderr)
        return 1
    return average_short_csvs(paths, Path(args.out))


if __name__ == "__main__":
    raise SystemExit(main())
