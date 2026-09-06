#!/usr/bin/env python3
import argparse
import csv
import sys
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bench_output import TIMEOUT_PLACEHOLDER, infer_set_sizes  # noqa: E402
from protocol_display_names import summary_protocol_name  # noqa: E402


def to_int(value, default=0):
    try:
        if value is None or str(value).strip() in ("", TIMEOUT_PLACEHOLDER):
            return default
        return int(str(value).strip())
    except ValueError:
        return default


def is_timeout_row(row):
    for key in ("Init Time(ms)", "Pto Time(ms)", "Pto  Time(ms)"):
        if key in row and str(row[key]).strip() == TIMEOUT_PLACEHOLDER:
            return True
    return False


def row_get(row, *keys, default=""):
    """Return the first present column (PsuMain uses double spaces in some headers)."""
    for key in keys:
        if key in row and row[key] is not None:
            return row[key]
    return default


def mb_decimal(num_bytes, digits=6):
    value = Decimal(num_bytes) / Decimal(1_000_000)
    quant = Decimal("1." + "0" * digits)
    return str(value.quantize(quant, rounding=ROUND_HALF_UP))


def sec_decimal(ms, digits=3):
    value = Decimal(ms) / Decimal(1000)
    quant = Decimal("1." + "0" * digits)
    return str(value.quantize(quant, rounding=ROUND_HALF_UP))


def detect_protocol_column(fieldnames):
    candidates = ["psu_type", "upsu_type", "psi_type", "protocol", "Protocol"]
    for c in candidates:
        if c in fieldnames:
            return c
    raise ValueError(
        f"Cannot detect protocol column. Expected one of: {candidates}"
    )


def summarize(input_csv, output_csv, digits_mb=6, digits_sec=3):
    with open(input_csv, "r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames

        if not fieldnames:
            raise ValueError("Input CSV has no header.")

        protocol_col = detect_protocol_column(fieldnames)

        groups = defaultdict(list)

        for row in reader:
            # Skip empty trailing rows like ",,,,,,,,,"
            if not row or not row.get("source_file", "").strip():
                continue

            protocol = row.get(protocol_col, "").strip()
            protocol = summary_protocol_name(protocol)
            if not protocol:
                continue

            append_string = row.get("append_string", "").strip()
            # Prefer sizes from append_string so unbalanced PSU harness rows
            # (client writes own/other sizes swapped) still group as one protocol.
            inferred_server, inferred_client = infer_set_sizes(append_string)
            if inferred_server != TIMEOUT_PLACEHOLDER:
                server_size = inferred_server
                client_size = inferred_client
            else:
                server_size = row.get("Server Set Size", "").strip()
                client_size = row.get("Client Set Size", "").strip()

            key = (
                protocol,
                append_string,
                row.get("element_bits", "").strip(),
                server_size,
                client_size,
                row.get("thread_num", "").strip(),
            )

            groups[key].append(row)

    summaries = []

    for key, rows in groups.items():
        protocol, append_string, element_bits, server_size, client_size, thread_num = key

        if any(is_timeout_row(row) for row in rows):
            summaries.append({
                "Protocol": protocol,
                "Append String": append_string,
                "Element Bits": element_bits,
                "Server Set Size": server_size,
                "Client Set Size": client_size,
                "Thread Num": thread_num,
                "Party Count": len(rows),
                "Party 0 Time(ms)": TIMEOUT_PLACEHOLDER,
                "Party 1 Time(ms)": TIMEOUT_PLACEHOLDER,
                "Total Time(ms)": TIMEOUT_PLACEHOLDER,
                "Total Time(s)": TIMEOUT_PLACEHOLDER,
                "Init Comm(Bytes)": TIMEOUT_PLACEHOLDER,
                "PTO Comm(Bytes)": TIMEOUT_PLACEHOLDER,
                "Total Comm(Bytes)": TIMEOUT_PLACEHOLDER,
                "Init Comm(MB)": TIMEOUT_PLACEHOLDER,
                "PTO Comm(MB)": TIMEOUT_PLACEHOLDER,
                "Total Comm(MB)": TIMEOUT_PLACEHOLDER,
            })
            continue

        party_times = {}
        init_comm_bytes = 0
        pto_comm_bytes = 0

        for row in rows:
            party_id = row.get("party_id", row.get("Party ID", "")).strip()

            init_time = to_int(row_get(row, "Init Time(ms)"))
            pto_time = to_int(row_get(row, "Pto Time(ms)", "Pto  Time(ms)"))
            total_party_time = init_time + pto_time

            party_times[party_id] = total_party_time

            init_send = to_int(row_get(row, "Init Send Bytes(B)"))
            pto_send = to_int(row_get(row, "Pto Send Bytes(B)", "Pto  Send Bytes(B)"))

            init_comm_bytes += init_send
            pto_comm_bytes += pto_send

        total_time_ms = max(party_times.values()) if party_times else 0
        total_comm_bytes = init_comm_bytes + pto_comm_bytes

        summaries.append({
            "Protocol": protocol,
            "Append String": append_string,
            "Element Bits": element_bits,
            "Server Set Size": server_size,
            "Client Set Size": client_size,
            "Thread Num": thread_num,
            "Party Count": len(rows),
            "Party 0 Time(ms)": party_times.get("0", ""),
            "Party 1 Time(ms)": party_times.get("1", ""),
            "Total Time(ms)": total_time_ms,
            "Total Time(s)": sec_decimal(total_time_ms, digits_sec),
            "Init Comm(Bytes)": init_comm_bytes,
            "PTO Comm(Bytes)": pto_comm_bytes,
            "Total Comm(Bytes)": total_comm_bytes,
            "Init Comm(MB)": mb_decimal(init_comm_bytes, digits_mb),
            "PTO Comm(MB)": mb_decimal(pto_comm_bytes, digits_mb),
            "Total Comm(MB)": mb_decimal(total_comm_bytes, digits_mb),
        })

    def sort_key(row):
        if row["Total Time(s)"] == TIMEOUT_PLACEHOLDER:
            return (1, Decimal(0), Decimal(0))
        return (0, Decimal(row["Total Time(s)"]), Decimal(row["Total Comm(MB)"]))

    summaries.sort(key=sort_key)

    rank = 0
    for row in summaries:
        if row["Total Time(s)"] == TIMEOUT_PLACEHOLDER:
            row["Rank By Time"] = TIMEOUT_PLACEHOLDER
        else:
            rank += 1
            row["Rank By Time"] = rank

    output_fields = [
        "Rank By Time",
        "Protocol",
        "Append String",
        "Element Bits",
        "Server Set Size",
        "Client Set Size",
        "Thread Num",
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
    ]

    with open(output_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=output_fields)
        writer.writeheader()
        writer.writerows(summaries)


def main():
    parser = argparse.ArgumentParser(
        description="Summarize PSU/UPSU/PSI benchmark CSV by protocol."
    )
    parser.add_argument("input_csv", help="Input raw benchmark CSV file")
    parser.add_argument("output_csv", help="Output summarized CSV file")
    parser.add_argument("--digits-mb", type=int, default=6, help="Decimal digits for MB")
    parser.add_argument("--digits-sec", type=int, default=3, help="Decimal digits for seconds")
    args = parser.parse_args()

    summarize(
        args.input_csv,
        args.output_csv,
        digits_mb=args.digits_mb,
        digits_sec=args.digits_sec,
    )


if __name__ == "__main__":
    main()