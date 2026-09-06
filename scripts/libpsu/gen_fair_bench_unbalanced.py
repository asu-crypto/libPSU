#!/usr/bin/env python3
"""Generate fair_bench_unbalanced_2p<S>x2p<C>.conf for unbalanced harness runs.

1. Mirror every balanced PSU folder (bench/configs/psu/*) into bench/configs/upsu/*
   with unequal server_log_set_size / client_log_set_size (pto_type stays PSU).
2. Mirror ASIACCS:BlaAgu12 balanced configs into upsu/* (pto_type unchanged).
3. Patch native UPSU folders (09_CCS:TCLZ23, 10_USENIX:BinYujConYanYu25, …) that use pto_type = UPSU.

These harness configs are experimental: balanced protocols at unequal sizes may
abort or produce non-standard results. Native UPSU entries are true UPSU.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Callable
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from fair_bench_conf import (  # noqa: E402
    BA12_ROOT,
    PSU_ROOT,
    REPO,
    UPSU_ROOT,
    apply_leading_header,
    load_first_config,
    patch_append_and_sizes,
    set_property,
    write_if_changed,
)

PSU_HARNESS_HEADER = (
    "# HARNESS EXPERIMENT: balanced PSU protocol at unequal set sizes (not true UPSU).\n"
    "# server = sender 2^{s}, client = receiver 2^{c}. May abort or be invalid — compare with care.\n"
)

UPSU_HEADER = (
    "# Fair UPSU: sender 2^{s} = {sender_n}, receiver 2^{c} = {receiver_n}.\n"
    "# server = sender (X), client = receiver (Y).\n"
)


def load_balanced_base(folder: Path, server_log: int) -> str | None:
    return load_first_config(
        folder,
        (
            f"fair_bench_2p{server_log}.conf",
            "fair_bench_2p20.conf",
            "fair_bench_2p5.conf",
            "fair_bench.conf",
        ),
    )


def load_upsu_base(folder: Path) -> str | None:
    return load_first_config(
        folder,
        (
            "fair_bench_unbalanced_2p10x2p20.conf",
            "fair_bench_2p5.conf",
            "fair_bench.conf",
        ),
    )


def is_native_upsu_text(text: str) -> bool:
    return bool(re.search(r"^pto_type\s*=\s*UPSU\s*$", text, re.MULTILINE))


def patch_psu_harness(text: str, server_log: int, client_log: int, append: str) -> str:
    return apply_leading_header(
        patch_append_and_sizes(text, append, server_log, client_log),
        PSU_HARNESS_HEADER.format(s=server_log, c=client_log),
    )


def patch_ks05_limits(text: str, server_log: int, client_log: int) -> str:
    max_n = max(1 << server_log, 1 << client_log)
    if re.search(r"^ks05_max_set_size\s*=", text, re.MULTILINE):
        return set_property(text, "ks05_max_set_size", str(max_n))
    return text


def report_write(dest: Path, body: str) -> str:
    action = "wrote" if write_if_changed(dest, body) else "unchanged"
    print(f"{action} {dest.relative_to(REPO)}")
    return action


def mirror_harness_tree(
    root: Path,
    server_log: int,
    client_log: int,
    append: str,
    dest_name: str,
    *,
    patch_extra: Callable[[str, int, int], str] | None = None,
) -> tuple[int, int]:
    written = 0
    unchanged = 0
    if not root.is_dir():
        print(f"skip mirror {root.name}: root missing", file=sys.stderr)
        return written, unchanged
    for folder in sorted(p for p in root.iterdir() if p.is_dir()):
        text = load_balanced_base(folder, server_log)
        if text is None:
            print(f"skip {root.name} mirror {folder.name}: no base config", file=sys.stderr)
            continue
        body = patch_psu_harness(text, server_log, client_log, append)
        if patch_extra is not None:
            body = patch_extra(body, server_log, client_log)
        dest = UPSU_ROOT / folder.name / dest_name
        if report_write(dest, body) == "wrote":
            written += 1
        else:
            unchanged += 1
    return written, unchanged


def patch_native_upsu(text: str, server_log: int, client_log: int, append: str) -> str:
    sender_n = 1 << server_log
    receiver_n = 1 << client_log
    return apply_leading_header(
        patch_append_and_sizes(text, append, server_log, client_log),
        UPSU_HEADER.format(
            s=server_log,
            c=client_log,
            sender_n=sender_n,
            receiver_n=receiver_n,
        ),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate unbalanced harness configs under bench/configs/upsu/"
    )
    parser.add_argument("server_log", type=int, help="log2 sender (server) set size, e.g. 20")
    parser.add_argument("client_log", type=int, help="log2 receiver (client) set size, e.g. 10")
    args = parser.parse_args()
    s_log, c_log = args.server_log, args.client_log
    if s_log < 1 or s_log > 30 or c_log < 1 or c_log > 30:
        print("log exponents must be in [1, 30]", file=sys.stderr)
        return 1

    append = f"fair_bench_unbalanced_2p{s_log}x2p{c_log}"
    dest_name = f"{append}.conf"
    written = 0
    unchanged = 0

    if not PSU_ROOT.is_dir():
        print(f"PSU config root missing: {PSU_ROOT}", file=sys.stderr)
        return 1
    UPSU_ROOT.mkdir(parents=True, exist_ok=True)

    for folder in sorted(p for p in PSU_ROOT.iterdir() if p.is_dir()):
        if folder.name == "08_USENIX:BinYujConYanYu25":
            print(
                "skip psu mirror 08_USENIX:BinYujConYanYu25: native UPSU exists (10_USENIX:BinYujConYanYu25)",
                file=sys.stderr,
            )
            continue
        text = load_balanced_base(folder, s_log)
        if text is None:
            print(f"skip psu mirror {folder.name}: no base config", file=sys.stderr)
            continue
        body = patch_psu_harness(text, s_log, c_log, append)
        if "psu_pto_name = C:KisSon05" in body:
            body = patch_ks05_limits(body, s_log, c_log)
        dest = UPSU_ROOT / folder.name / dest_name
        if report_write(dest, body) == "wrote":
            written += 1
        else:
            unchanged += 1

    w, u = mirror_harness_tree(BA12_ROOT, s_log, c_log, append, dest_name)
    written += w
    unchanged += u

    psu_names = {p.name for p in PSU_ROOT.iterdir() if p.is_dir()}
    if UPSU_ROOT.is_dir():
        for folder in sorted(p for p in UPSU_ROOT.iterdir() if p.is_dir()):
            if folder.name in psu_names:
                continue
            text = load_upsu_base(folder)
            if text is None:
                print(f"skip native upsu {folder.name}: no base config", file=sys.stderr)
                continue
            if not is_native_upsu_text(text):
                print(f"skip {folder.name}: not pto_type UPSU", file=sys.stderr)
                continue
            dest = folder / dest_name
            if report_write(dest, patch_native_upsu(text, s_log, c_log, append)) == "wrote":
                written += 1
            else:
                unchanged += 1

    print(f"done ({written} updated, {unchanged} unchanged) -> {dest_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
