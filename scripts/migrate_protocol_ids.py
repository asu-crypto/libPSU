#!/usr/bin/env python3
"""One-shot migration: legacy protocol ids -> canonical wire ids (venue:Name)."""

from __future__ import annotations

import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

# Legacy -> canonical wire id (configs, registry, docs)
WIRE_REPLACEMENTS: list[tuple[str, str]] = [
    ("PKC:CheZhaZha24", "PKC:CheZhaZha24"),
    ("Ours", "Ours"),
    ("USENIX:HaoWan26", "USENIX:HaoWan26"),
    ("USENIX:YanShiHonDaw24", "USENIX:YanShiHonDaw24"),
    ("EUROCRYPT:PuGaoTri26", "EUROCRYPT:PuGaoTri26"),
    ("EUROCRYPT:PuGaoTri26", "EUROCRYPT:PuGaoTri26"),
    ("USENIX:ConYuWeiminDon23_PKE", "USENIX:ConYuWeiminDon23_PKE"),
    ("USENIX:ConYuWeiminDon23_SKE", "USENIX:ConYuWeiminDon23_SKE"),
    ("USENIX:JSZDG22", "USENIX:JSZDG22"),
    ("USENIX:JSZDG22_SFS", "USENIX:JSZDG22_SFS"),
    ("PKC:GMRSS21", "PKC:GMRSS21"),
    ("AC:AC-KRTW19", "AC:AC:AC-KRTW19"),
    ("ASIACCS:CSSW25", "ASIACCS:CSSW25"),
    ("USENIX:BinYujConYanYu25", "USENIX:BinYujConYanYu25"),
    ("EUROCRYPT:PisTri26", "EUROCRYPT:PisTri26"),
    ("ACISP:DavCid17", "ACISP:DavCid17"),
    ("ACNS:Frikken07", "ACNS:Frikken07"),
    ("C:KisSon05", "C:KisSon05"),
    ("JOC:HazNis12", "JOC:HazNis12"),
    ("CCS:TCLZ23", "CCS:TCLZ23"),
    ("ASIACCS:BlaAgu12", "ASIACCS:BlaAgu12"),
]

# Java PsuType/PsiType/UpsuType enum constant renames (longest first)
JAVA_ENUM_REPLACEMENTS: list[tuple[str, str]] = [
    ("Ours", "Ours"),
    ("USENIX:YanShiHonDaw24", "USENIX_YanShiHonDaw24"),
    ("PKC:CheZhaZha24", "PKC_CheZhaZha24"),
    ("USENIX:HaoWan26", "USENIX_HaoWan26"),
    ("EUROCRYPT:PuGaoTri26", "EUROCRYPT_PuGaoTri26"),
    ("EUROCRYPT:PuGaoTri26", "EUROCRYPT_PuGaoTri26"),
    ("USENIX:ConYuWeiminDon23_PKE", "USENIX_ConYuWeiminDon23_PKE"),
    ("USENIX:ConYuWeiminDon23_SKE", "USENIX_ConYuWeiminDon23_SKE"),
    ("USENIX:JSZDG22_PROXY", "USENIX_JSZDG22"),
    ("USENIX:JSZDG22_SFS_PROXY", "USENIX_JSZDG22_SFS"),
    ("USENIX:JSZDG22", "USENIX_JSZDG22"),
    ("USENIX:JSZDG22_SFS", "USENIX_JSZDG22_SFS"),
    ("PKC:GMRSS21_PROXY", "PKC_GMRSS21"),
    ("PKC:GMRSS21", "PKC_GMRSS21"),
    ("ASIACCS:CSSW25_PROXY", "ASIACCS_CSSW25"),
    ("ASIACCS:CSSW25", "ASIACCS_CSSW25"),
    ("USENIX:BinYujConYanYu25", "USENIX_BinYujConYanYu25"),
    ("EUROCRYPT:PisTri26", "EUROCRYPT_PisTri26"),
    ("ACISP:DavCid17", "ACISP_DavCid17"),
    ("ACNS:Frikken07", "ACNS_Frikken07"),
    ("AC:AC-KRTW19", "AC_AC:AC-KRTW19"),
    ("JOC:HazNis12", "JOC_HazNis12"),
    ("C:KisSon05", "C_KisSon05"),
]

# Bench output filename tokens (colon -> hyphen)
FILE_TOKEN_REPLACEMENTS: list[tuple[str, str]] = [
    (old, new.replace(":", "-"))
    for old, new in WIRE_REPLACEMENTS
]

SKIP_SUFFIXES = {".class", ".jar", ".output", ".png", ".jpg", ".so", ".git"}

GLOB_DIRS = [
    REPO / "mpc4j-psu",
    REPO / "scripts",
    REPO / "docs",
    REPO / "README.md",
]


def should_touch(path: Path) -> bool:
    if path.suffix in SKIP_SUFFIXES:
        return False
    if "_archive" in path.parts:
        return False
    if path.name.endswith(".disabled"):
        return False
    return path.suffix in {
        ".java", ".py", ".sh", ".md", ".conf", ".txt", ".xml", ".properties", ""
    }


def replace_in_text(text: str, pairs: list[tuple[str, str]], word_boundary: bool = False) -> str:
    for old, new in pairs:
        if word_boundary:
            text = re.sub(rf"\b{re.escape(old)}\b", new, text)
        else:
            text = text.replace(old, new)
    return text


def migrate_file(path: Path) -> bool:
    try:
        original = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, IsADirectoryError):
        return False
    updated = original

    if path.suffix == ".java":
        updated = replace_in_text(updated, JAVA_ENUM_REPLACEMENTS, word_boundary=True)
    elif path.suffix == ".conf":
        updated = replace_in_text(updated, WIRE_REPLACEMENTS)
    elif path.name == "bench_output.py":
        # file tokens in output parser
        updated = replace_in_text(updated, FILE_TOKEN_REPLACEMENTS, word_boundary=True)
        updated = replace_in_text(updated, WIRE_REPLACEMENTS, word_boundary=True)
    else:
        updated = replace_in_text(updated, WIRE_REPLACEMENTS)
        if path.suffix == ".py" and "PSU_TYPE_PREFIXES" in updated:
            updated = replace_in_text(updated, FILE_TOKEN_REPLACEMENTS, word_boundary=True)

    if updated != original:
        path.write_text(updated, encoding="utf-8")
        return True
    return False


def main() -> int:
    changed = 0
    targets: list[Path] = []
    for item in GLOB_DIRS:
        if item.is_file():
            targets.append(item)
        else:
            targets.extend(p for p in item.rglob("*") if p.is_file() and should_touch(p))

    for path in sorted(set(targets)):
        if migrate_file(path):
            print(f"updated {path.relative_to(REPO)}")
            changed += 1
    print(f"done ({changed} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
