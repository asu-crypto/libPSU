#!/usr/bin/env python3
"""Verify summarizers export fidelity_label and CSS25/UPSU/HN12 labels."""

from __future__ import annotations

import csv
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from protocol_display_names import (  # noqa: E402
    apply_display_to_meta,
    fidelity_label,
    normalize_css25_mode,
)
from summarize_psu_fair_outputs import main as psu_main  # noqa: E402
from summarize_upsu_fair_outputs import main as upsu_main  # noqa: E402


def _write_psu_output(
    temp_dir: Path,
    *,
    protocol_token: str,
    append: str = "fair_bench_2p4",
    meta_line: str | None = None,
) -> Path:
    name = f"PSU_{protocol_token}_{append}_128_0_1.output"
    path = temp_dir / name
    lines = []
    if meta_line is not None:
        lines.append(meta_line)
    lines.append("Time(ms)\tSend(B)")
    lines.append("1.0\t2")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _write_upsu_output(temp_dir: Path, *, protocol_token: str) -> Path:
    name = f"UPSU_{protocol_token}_fair_bench_unbalanced_2p20x2p10_128_0_1.output"
    path = temp_dir / name
    path.write_text("Time(ms)\tSend(B)\n1.0\t2\n", encoding="utf-8")
    return path


def _run_psu_summary(temp_dir: Path, out: Path) -> list[dict[str, str]]:
    argv = sys.argv
    try:
        sys.argv = [
            "summarize_psu_fair_outputs.py",
            "--temp-dir",
            str(temp_dir),
            "--out",
            str(out),
            "--include-all-appends",
        ]
        code = psu_main()
        assert code == 0, f"psu summarizer exited {code}"
    finally:
        sys.argv = argv
    with out.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _run_upsu_summary(temp_dir: Path, out: Path) -> list[dict[str, str]]:
    argv = sys.argv
    try:
        sys.argv = [
            "summarize_upsu_fair_outputs.py",
            "--temp-dir",
            str(temp_dir),
            "--out",
            str(out),
        ]
        code = upsu_main()
        assert code == 0, f"upsu summarizer exited {code}"
    finally:
        sys.argv = argv
    with out.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


class FidelitySummaryTest(unittest.TestCase):
    def test_normalize_css25_legacy_boolean(self) -> None:
        self.assertEqual(normalize_css25_mode(None), "PROXY")
        self.assertEqual(normalize_css25_mode(""), "PROXY")
        self.assertEqual(normalize_css25_mode("false"), "PROXY")
        self.assertEqual(normalize_css25_mode("0"), "PROXY")
        self.assertEqual(normalize_css25_mode("no"), "PROXY")
        self.assertEqual(normalize_css25_mode("true"), "PAPER_COMPARISON_PROXY")
        self.assertEqual(normalize_css25_mode("1"), "PAPER_COMPARISON_PROXY")
        self.assertEqual(normalize_css25_mode("yes"), "PAPER_COMPARISON_PROXY")
        self.assertEqual(normalize_css25_mode("PAPER_EXACT"), "PAPER_EXACT")

    def test_label_helpers(self) -> None:
        self.assertIn("PSTY19", fidelity_label("CSS25"))
        self.assertIn("RS21", fidelity_label("CSS25", css25_mode="true"))
        self.assertIn("UNSUPPORTED", fidelity_label("CSS25", css25_mode="PAPER_EXACT"))
        self.assertIn("linear", fidelity_label("TBZ25", functionality="UPSU").lower())
        self.assertIn("fast", fidelity_label("HAO_WAN2026").lower())
        self.assertIn("leakage", fidelity_label("Ours").lower())
        self.assertIn("experimental", fidelity_label("HN12").lower())
        self.assertIn("internal one-sided", fidelity_label("PGT26_1M"))

    def test_psu_csv_exports_fidelity_and_css25_modes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            temp_dir = Path(tmp)
            _write_psu_output(
                temp_dir,
                protocol_token="CSS25",
                meta_line="#libpsu_meta\tcss25_mode=PROXY",
            )
            _write_psu_output(
                temp_dir,
                protocol_token="ASIACCS-CSSW25",
                append="fair_bench_2p5",
                meta_line="#libpsu_meta\tcss25_mode=PAPER_COMPARISON_PROXY",
            )
            _write_psu_output(temp_dir, protocol_token="HAO_WAN2026", append="fair_bench_2p6")
            _write_psu_output(temp_dir, protocol_token="Ours", append="fair_bench_2p8")
            _write_psu_output(temp_dir, protocol_token="HN12", append="fair_bench_2p10")
            _write_psu_output(temp_dir, protocol_token="PGT26_1M", append="fair_bench_2p12")
            out = temp_dir / "summary.csv"
            rows = _run_psu_summary(temp_dir, out)
            self.assertGreaterEqual(len(rows), 5)
            fieldnames = list(rows[0].keys())
            self.assertIn("fidelity_label", fieldnames)
            self.assertIn("css25_mode", fieldnames)

            by_proto = {r["source_file"]: r for r in rows}
            self.assertGreaterEqual(len(by_proto), 5)
            css_default = next(r for r in rows if "CSS25_fair_bench_2p4" in r["source_file"])
            self.assertEqual(css_default["css25_mode"], "PROXY")
            self.assertIn("PSTY19", css_default["fidelity_label"])

            css_cmp = next(r for r in rows if "fair_bench_2p5" in r["source_file"])
            self.assertEqual(css_cmp["css25_mode"], "PAPER_COMPARISON_PROXY")
            self.assertIn("RS21", css_cmp["fidelity_label"])

            hao = next(r for r in rows if "HAO_WAN" in r["source_file"] or "HaoWan" in r.get("psu_type", ""))
            self.assertIn("fast", hao["fidelity_label"].lower())

            ours = next(r for r in rows if r.get("psu_type") == "Ours" or "Ours_" in r["source_file"])
            self.assertIn("leakage", ours["fidelity_label"].lower())

            hn12 = next(r for r in rows if "HN12" in r["source_file"] or "HazNis" in r.get("psu_type", ""))
            self.assertIn("experimental", hn12["fidelity_label"].lower())

            pgt1m = next(r for r in rows if "PGT26_1M" in r["source_file"])
            self.assertIn("internal one-sided", pgt1m["fidelity_label"])
            self.assertNotEqual(pgt1m.get("psu_type"), "EUROCRYPT:PuGaoTri26")
            self.assertIn("PGT26_1M", pgt1m.get("psu_type", "") + pgt1m["source_file"])

    def test_upsu_tbz25_wrapper_label(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            temp_dir = Path(tmp)
            _write_upsu_output(temp_dir, protocol_token="TBZ25")
            out = temp_dir / "upsu.csv"
            rows = _run_upsu_summary(temp_dir, out)
            self.assertEqual(1, len(rows))
            self.assertIn("fidelity_label", rows[0])
            self.assertIn("wrapper", rows[0]["fidelity_label"].lower())

    def test_legacy_css25_paper_comparison_flag(self) -> None:
        meta = {"psu_type": "CSS25", "css25_paper_comparison": "true"}
        apply_display_to_meta(meta)
        self.assertEqual(meta["css25_mode"], "PAPER_COMPARISON_PROXY")
        self.assertIn("RS21", meta["fidelity_label"])


if __name__ == "__main__":
    unittest.main()
