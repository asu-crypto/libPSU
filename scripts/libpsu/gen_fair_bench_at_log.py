#!/usr/bin/env python3
"""Generate fair_bench_2p<log>.conf for balanced PSU, PSI, ASIACCS:BlaAgu12, and optionally UPSU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from fair_bench_conf import (  # noqa: E402
    BA12_ROOT,
    PSI_ROOT,
    PSU_ROOT,
    REPO,
    UPSU_ROOT,
    force_boolean_property,
    load_first_config,
    patch_append_and_sizes,
    replace_first_comment_line,
    write_if_changed,
)


def load_base_text(folder: Path) -> str | None:
    text = load_first_config(
        folder, ("fair_bench.conf", "fair_bench_2p20.conf", "fair_bench_2p5.conf")
    )
    if text is not None:
        return text
    fallback = folder / "fair_bench_2p10.conf.disabled"
    if fallback.is_file():
        return fallback.read_text(encoding="utf-8")
    return None


def patch_balanced(text: str, log: int, append: str) -> str:
    out = patch_append_and_sizes(text, append, log, log)
    out = force_boolean_property(out, "skip_warmup", True)
    out = force_boolean_property(out, "skip_gc", True)
    n = 1 << log
    return replace_first_comment_line(out, f"# Fair benchmark (2^{log} = {n} x {n}).\n")


def patch_ba12_balanced(text: str, log: int, append: str) -> str:
    out = patch_balanced(text, log, append)
    n = 1 << log
    return replace_first_comment_line(
        out,
        f"# Fair benchmark (2^{log} = {n} x {n}) — ASIACCS:BlaAgu12 secret-shared set union.\n",
    )


def generate_tree(
    root: Path,
    log: int,
    append: str,
    dest_name: str,
    patch_fn=patch_balanced,
) -> int:
    if not root.is_dir():
        return 0
    written = 0
    for folder in sorted(p for p in root.iterdir() if p.is_dir()):
        text = load_base_text(folder)
        if text is None:
            print(f"skip {folder.relative_to(REPO)}: no base config", file=sys.stderr)
            continue
        dest = folder / dest_name
        if write_if_changed(dest, patch_fn(text, log, append)):
            print(f"wrote {dest.relative_to(REPO)}")
            written += 1
    return written


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate fair_bench_2pLOG configs")
    parser.add_argument(
        "log",
        type=int,
        help="log2 of set size (e.g. 5 -> 32x32, 20 -> 2^20 x 2^20)",
    )
    parser.add_argument(
        "--include-upsu",
        action="store_true",
        help="also patch unbalanced UPSU fair_bench_2pLOG.conf files",
    )
    args = parser.parse_args()
    log = args.log
    if log < 1 or log > 30:
        print("log must be in [1, 30]", file=sys.stderr)
        return 1

    append = f"fair_bench_2p{log}"
    dest_name = f"{append}.conf"
    total = 0
    total += generate_tree(PSU_ROOT, log, append, dest_name)
    total += generate_tree(PSI_ROOT, log, append, dest_name)
    total += generate_tree(BA12_ROOT, log, append, dest_name, patch_ba12_balanced)
    if args.include_upsu:
        total += generate_tree(UPSU_ROOT, log, append, dest_name)

    print(f"done ({total} file(s) updated) -> {dest_name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
