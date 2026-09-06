#!/usr/bin/env python3
"""Generate one markdown doc per runnable protocol under mpc4j-psu/docs/protocols/."""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "mpc4j-psu" / "docs" / "protocols"
BENCH_ROOT = REPO / "mpc4j-psu" / "bench" / "configs"

sys.path.insert(0, str(REPO / "scripts" / "libpsu"))
from protocol_display_names import CANONICAL_PROTOCOL_IDS, display_protocol_name, to_file_token  # noqa: E402

# Protocols with an existing deep-dive doc (linked from the generated stub).
DEEP_DIVE = {
    "Ours": "../SMALL_EC_ELLIGATOR_PSU_IMPLEMENTATION.md",
}


def rel(path: Path) -> str:
    return str(path.relative_to(REPO))


def doc_slug(internal: str) -> str:
    """Filesystem-safe name from display label (e.g. PKC:CheZhaZha24 -> PKC-CheZhaZha24)."""
    label = display_protocol_name(internal)
    return re.sub(r"[:/\\]+", "-", label)


PROTOCOLS: list[dict] = [
    {
        "name": "AC:KRTW19",
        "functionality": "Balanced PSU",
        "family": "Hash-bin RPMT (polynomial PEQT + KKRT OPRF ×2) + Core COT union",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/krtw19",
        "artifact": "mpc4j-psu-protocol-krtw19",
        "config_key": "psu_pto_name",
        "classes": "Krtw19PsuConfig, Krtw19PsuServer, Krtw19PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/01_KRTW19",
        "ot_status": "INCLUDED_INIT (KKRT OPRF ×2 + ALSZ13 Core COT)",
        "init": "rpmtOprfReceiver.init, peqtOprfSender.init, coreCotSender.init(delta); server sends hash-bin key.",
        "online": "Per-bin OPRF, PEQT, coreCotSender.send(binNum); client decrypts union.",
        "notes": "Up to three Base OT setups counted in Init Send Bytes.",
    },
    {
        "name": "PKC:GMRSS21",
        "functionality": "Balanced PSU",
        "family": "PKC:GMRSS21 mqRPMT (cuckoo + OKVS + DOSN/ROSN + KKRT OPRF ×2) + Core COT",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/gmr21",
        "artifact": "mpc4j-psu-protocol-gmr21",
        "config_key": "psu_pto_name",
        "classes": "Gmr21PsuConfig, Gmr21PsuServer, Gmr21PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/02_GMR21",
        "ot_status": "INCLUDED_INIT (KKRT ×2 + union Core COT; may be 3× Base OT)",
        "init": "gmr21MqRpmtServer.init (nested OPRF/DOSN/ROSN inits) + coreCotSender.init.",
        "online": "mqRPMT → serverVector; coreCotSender.send + XOR ciphertexts.",
        "notes": "Supports preCompute() via OoPsuMain only (not in fair PsuMain).",
    },
    {
        "name": "USENIX:JSZDG22",
        "functionality": "Balanced PSU",
        "family": "JSZ22 shuffle PSU (DOSN, ROSN, CM20 OPRF + standalone Core COT)",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/jsz22",
        "artifact": "mpc4j-psu-protocol-jsz22",
        "config_key": "psu_pto_name",
        "classes": "Jsz22SfcPsuConfig, Jsz22SfcPsuServer, Jsz22SfcPsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/03_JSZ22_SFC",
        "ot_status": "INCLUDED_INIT (IKNP in OPRF + ALSZ13 union COT)",
        "init": "dosnReceiver.init, rosnReceiver.init, oprfSender.init, coreCotSender.init.",
        "online": "Permutation/OSN, OPRF, PEQT, coreCotSender.send for encrypted payloads.",
        "notes": None,
    },
    {
        "name": "USENIX:JSZDG22_SFS",
        "functionality": "Balanced PSU",
        "family": "JSZ22 shuffle variant (dual DOSN/ROSN + OPRF receiver)",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/jsz22",
        "artifact": "mpc4j-psu-protocol-jsz22",
        "config_key": "psu_pto_name",
        "classes": "Jsz22SfsPsuConfig, Jsz22SfsPsuServer, Jsz22SfsPsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/04_JSZ22_SFS",
        "ot_status": "INCLUDED_INIT (CM20/IKNP via OPRF only)",
        "init": "firstDosnSender, secondDosnReceiver, firstRosnSender, secondRosnReceiver, oprfReceiver.init.",
        "online": "Dual DOSN/ROSN chain + OPRF evaluation and PEQT.",
        "notes": None,
    },
    {
        "name": "USENIX:ConYuWeiminDon23_PKE",
        "functionality": "Balanced PSU",
        "family": "ZCL23 PKE mqRPMT + Core COT union",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/zcl23",
        "artifact": "mpc4j-psu-protocol-zcl23",
        "config_key": "psu_pto_name",
        "classes": "Zcl23PkePsuConfig, Zcl23PkeMqRpmtServer/Client, Zcl23PkePsuServer/Client",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/05_ZCL23_PKE",
        "ot_status": "INCLUDED_INIT",
        "init": "zcl23PkeMqRpmtServer.init + coreCotSender.init(delta).",
        "online": "mqRPMT → vector; coreCotSender.send; XOR ciphertexts.",
        "notes": "Same OT accounting shape as PKC:CheZhaZha24.",
    },
    {
        "name": "USENIX:ConYuWeiminDon23_SKE",
        "functionality": "Balanced PSU",
        "family": "ZCL23 SKE (Z2 circuits + OPRP + GF2K-DOKVS + Core COT)",
        "security": "Semi-honest",
        "output": "One-sided (client union); fair bench uses 2-party PsuMain",
        "module": "protocols/balanced/zcl23",
        "artifact": "mpc4j-psu-protocol-zcl23",
        "config_key": "psu_pto_name",
        "classes": "Zcl23SkePsuConfig, Zcl23SkePsuServer, Zcl23SkePsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/05_ZCL23_SKE",
        "ot_status": "INCLUDED_INIT (Z2c + Core COT Base OT)",
        "init": "z2cSender.init, oprpReceiver.init, coreCotSender.init; send DOKVS keys.",
        "online": "PEQT shares and Core COT online delivery.",
        "notes": "Optional 3-party+aider API exists; fair configs use 2-party only.",
    },
    {
        "name": "PKC:CheZhaZha24",
        "functionality": "Balanced PSU",
        "family": "CZZ24 cwOPRF mqRPMT + Core COT union",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/czz24-cw-oprf",
        "artifact": "mpc4j-psu-protocol-czz24",
        "config_key": "psu_pto_name",
        "classes": "Czz24CwOprfPsuConfig, Czz24CwOprfPsuServer, Czz24CwOprfPsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/06_CZZ24_CW_OPRF",
        "ot_status": "INCLUDED_INIT (canonical OT reference)",
        "init": "mqRPMT local α/β + coreCotSender/Receiver.init (ALSZ13 + NP01 Base OT).",
        "online": "ECC mqRPMT, coreCot send/receive(n), encrypted union payloads.",
        "notes": "Reference protocol for fair-bench OT accounting.",
    },
    {
        "name": "ASIACCS:CSSW25",
        "functionality": "Balanced PSU",
        "family": "MP-OPRF + CCPSI + ROSN + Core COT",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/css25",
        "artifact": "mpc4j-psu-protocol-css25",
        "config_key": "psu_pto_name",
        "classes": "Css25PsuConfig, Css25PsuServer, Css25PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/07_CSS25",
        "ot_status": "INCLUDED_INIT (setupOprf/setupCcpsi/setupShtr/setupOt buckets)",
        "init": "mpOprfSender, ccpsiClient, rosnReceiver, coreCotSender inits.",
        "online": "MP-OPRF, CCPSI, ROSN, final coreCot.send(beta).",
        "notes": "Experimental runnable proxy; `css25_paper_exact=true` reserved. RS21 comparison is opt-in.",
    },
    {
        "name": "USENIX:BinYujConYanYu25",
        "functionality": "Balanced PSU / UPSU",
        "family": "Tu–Bai–Zhang ePSU (pnMCRG + XOR one-time pad)",
        "security": "Semi-honest",
        "output": "One-sided (receiver/client union)",
        "module": "protocols/balanced/tbz25 (+ UPSU wrapper in protocols/unbalanced/tbz25)",
        "artifact": "mpc4j-psu-protocol-tbz25",
        "config_key": "psu_pto_name / upsu_pto_name",
        "classes": "Tbz25PsuServer/Client; Tbz25UpsuSender/Receiver",
        "factory": "PsuFactory / UpsuFactory",
        "driver": "PsuMain / UpsuMain",
        "bench_dir": "psu/08_TBZ25",
        "bench_dir_extra": "upsu/10_TBZ25",
        "ot_status": "INCLUDED_INIT (RS21 + Core COT in nECRG)",
        "init": "pnMCRG sub-protocol inits (RS21 MP-OPRF + Core COT).",
        "online": "Cuckoo + pnMCRG; sender XORs OTP ciphertexts; receiver decrypts and unions.",
        "notes": "Paper FHE unbalanced MCRG (Fig. 14) is not implemented. Runnable UPSU uses the linear balanced core.",
    },
    {
        "name": "USENIX:HaoWan26",
        "functionality": "Balanced ePSU",
        "family": "Hao–Wang ePSU-fast (ssPMT-fast + ssOTd)",
        "security": "Semi-honest",
        "output": "One-sided (client union); sender outputs Finished",
        "module": "protocols/balanced/haowan2026",
        "artifact": "mpc4j-psu-protocol-haowan2026",
        "config_key": "psu_pto_name",
        "classes": "HaoWan2026PsuConfig, HaoWan2026PsuServer, HaoWan2026PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/09_HaoWan2026",
        "ot_status": "INCLUDED_INIT (Core COT in ssOTd; RS21 in ssPMT-fast)",
        "init": "ssPmtFastServer/Client.init + ssOtdServer/Client.init.",
        "online": "Server permutes X → ssPMT-fast → ssOTd; client outputs Y ∪ {decrypted z_i ≠ ⊥}.",
        "notes": "Implements ePSU-fast only (not ePSU-low / ssPMT-low).",
    },
    {
        "name": "EUROCRYPT:PisTri26",
        "functionality": "Balanced PSU",
        "family": "Pan–Tian 2026 IBLT peel + MP-OPRF + dual Core COT",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/pt26",
        "artifact": "mpc4j-psu-protocol-pt26",
        "config_key": "psu_pto_name",
        "classes": "Pt26PsuConfig, Pt26PsuServer, Pt26PsuClient, Pt26UnionPeel",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/14_PT26",
        "ot_status": "INCLUDED_INIT (OT12 + OT3 + MP-OPRF init)",
        "init": "IBLT keys RPC; ot12Receiver + ot3Sender + mpOprfSender init.",
        "online": "MP-OPRF in psu(), iterative peel with ot12/ot3 channels.",
        "notes": "Skipped by run_psu_fair.sh when LOG >= 18 unless --no-skip-pt26.",
    },
    {
        "name": "ACISP:DavCid17",
        "functionality": "Balanced PSU",
        "family": "Davidson–Cid Bloom-filter + Paillier AHE",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/dc17",
        "artifact": "mpc4j-psu-protocol-dc17",
        "config_key": "psu_pto_name",
        "classes": "Dc17PsuConfig, Dc17PsuServer, Dc17PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/15_DC17",
        "ot_status": "NO_OT",
        "init": "Minimal or empty.",
        "online": "PHE operations in psu().",
        "notes": None,
    },
    {
        "name": "ACNS:Frikken07",
        "functionality": "Balanced PSU",
        "family": "Legacy polynomial + Paillier AHE",
        "security": "Semi-honest",
        "output": "One-sided (client union)",
        "module": "protocols/balanced/f07",
        "artifact": "mpc4j-psu-protocol-f07",
        "config_key": "psu_pto_name",
        "classes": "ACNS:Frikken07PsuConfig, ACNS:Frikken07PsuServer, ACNS:Frikken07PsuClient",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/16_F07",
        "ot_status": "NO_OT",
        "init": "Minimal.",
        "online": "Polynomial + PHE in psu().",
        "notes": "Distinct from ACISP:DavCid17 (Bloom vs polynomial).",
    },
    {
        "name": "USENIX:YanShiHonDaw24",
        "functionality": "Balanced PSU",
        "family": "JSZG24 Fig.17 bECRG + batch OPPRF + PEQT + LNOT + DOSN",
        "security": "Semi-honest",
        "output": "One-sided (receiver union; sender Finished)",
        "module": "protocols/malicious/jszg24-becrg-psu",
        "artifact": "mpc4j-psu-protocol-jszg24",
        "config_key": "psu_pto_name",
        "classes": "Jszg24BecrgPsuConfig, Jszg24BecrgPsuServer/Client",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/19_JSZG24_BECRG_PSU",
        "ot_status": "INCLUDED_INIT (LNOT/NC-COT chain)",
        "init": "bopprfReceiver, peqtSender, lnotSender, dosnReceiver inits.",
        "online": "Cuckoo, OPPRF, PET, lnot send, pad XOR, DOSN, ciphertexts.",
        "notes": "Config keys: jszg24_item_bit_length, jszg24_lambda, silent_cot.",
    },
    {
        "name": "EUROCRYPT:PuGaoTri26",
        "functionality": "Balanced PSU",
        "family": "Pu–Gao–Trieu malicious two-sided (EC + AoK shuffle/RDDH)",
        "security": "Malicious",
        "output": "Two-sided (both parties get union)",
        "module": "protocols/malicious/pgt26",
        "artifact": "mpc4j-psu-protocol-pgt26",
        "config_key": "psu_pto_name",
        "classes": "Pgt26_2mPsuServer/Client via PsuTwoSided* API",
        "factory": "PsuFactory (two-sided)",
        "driver": "PsuMain",
        "bench_dir": "psu/18_PGT26_2M",
        "ot_status": "NO_OT",
        "init": "Pgt26PublicParams.setup(maxN) only.",
        "online": "Multi-round HashDH + proofs; no mpc4j OT layer.",
        "notes": "Fair benchmark always runs with shuffle proofs and RDDH proofs enabled.",
    },
    {
        "name": "Ours",
        "functionality": "Balanced PSU (leakage baseline)",
        "family": "Small-set EC/Elligator HashDH",
        "security": "Semi-honest (leakage baseline)",
        "output": "One-sided client union; server learns W \\ V pattern",
        "module": "protocols/balanced/small-ec-elligator-psu",
        "artifact": "mpc4j-psu-protocol-small-ec-elligator-psu",
        "config_key": "psu_pto_name",
        "classes": "SmallEcElligatorPsuConfig, SmallEcElligatorPsuServer/Client",
        "factory": "PsuFactory",
        "driver": "PsuMain",
        "bench_dir": "psu/22_SMALL_EC_ELLIGATOR_PSU",
        "ot_status": "NO_OT",
        "init": "No-op (no RPC).",
        "online": "Blinded X/Y, shuffled U, server filters difference, client rebuilds union.",
        "notes": "Not standard leakage-free PSU. Default element_byte_length = 16 (128-bit items).",
    },
    {
        "name": "C:KisSon05",
        "functionality": "PSI",
        "family": "Kissel–Schneider 2005 Paillier polynomial PSI",
        "security": "Semi-honest",
        "output": "Client learns intersection",
        "module": "protocols/psi/ks05",
        "artifact": "mpc4j-psu-protocol-ks05",
        "config_key": "psi_pto_name",
        "classes": "Ks05PsiConfig, Ks05PsiServer/Client",
        "factory": "PsiFactory",
        "driver": "PsiMain",
        "bench_dir": "psi/01_KS05",
        "ot_status": "NO_OT",
        "init": "Empty.",
        "online": "Paillier polynomial PSI in psi().",
        "notes": "Included in fair bench as PSI baseline.",
    },
    {
        "name": "JOC:HazNis12",
        "functionality": "PSI",
        "family": "Hazay–Nissim malicious PSI (DDH, ElGamal, ZK)",
        "security": "Malicious",
        "output": "Client learns intersection",
        "module": "protocols/psi/hn12",
        "artifact": "mpc4j-psu-protocol-hn12",
        "config_key": "psi_pto_name",
        "classes": "Hn12PsiConfig, Hn12PsiServer/Client",
        "factory": "PsiFactory",
        "driver": "PsiMain",
        "bench_dir": "psi/19_HN12_PSI",
        "ot_status": "NO_OT",
        "init": "Minimal.",
        "online": "DDH/ElGamal PSI with ZK proofs.",
        "notes": None,
    },
    {
        "name": "CCS:TCLZ23",
        "functionality": "Unbalanced UPSU",
        "family": "CCS:TCLZ23 sqOPRF + pm-PEQT + FHE + Core COT",
        "security": "Semi-honest",
        "output": "One-sided (receiver output)",
        "module": "protocols/unbalanced/tcl23",
        "artifact": "mpc4j-psu-protocol-tcl23",
        "config_key": "upsu_pto_name",
        "classes": "Tcl23UpsuConfig, Tcl23UpsuSender/Receiver",
        "factory": "UpsuFactory",
        "driver": "UpsuMain",
        "bench_dir": "upsu/09_TCL23",
        "ot_status": "INCLUDED_INIT (Core COT); FHE dominates Pto",
        "init": "sqOprf, pmPeqt, coreCot inits; FHE keygen + relin keys (large Init traffic).",
        "online": "OPRF, PEQT, COT, FHE ciphertexts.",
        "notes": "Requires libmpc4j-native-fhe. Large sets use -Xmx32g+.",
    },
    {
        "name": "ASIACCS:BlaAgu12",
        "functionality": "Garbled-circuit set ops",
        "family": "Bea91 Z2c MPC set operations",
        "security": "Semi-honest",
        "output": "Secret-shared set operation (e.g. ASIACCS:BlaAgu12_UNION)",
        "module": "protocols/psi/ba12",
        "artifact": "mpc4j-psu-protocol-ba12",
        "config_key": "ba12_pto_name",
        "classes": "Ba12Config, Ba12SetOpsParty",
        "factory": "N/A",
        "driver": "Ba12Main",
        "bench_dir": "ba12/01_BA12",
        "ot_status": "INCLUDED_INIT (Z2c / Bea91 COT in init)",
        "init": "Ba12SetOpsParty.init → z2c.init (Base OT inside).",
        "online": "runBinary (e.g. ASIACCS:BlaAgu12_UNION).",
        "notes": "pto_type = ASIACCS:BlaAgu12; output prefix PSU_ASIACCS:BlaAgu12_* in fair scripts. Not a PsuType.",
    },
]


def bench_conf_example(bench_dir: str) -> str | None:
    folder = BENCH_ROOT / bench_dir
    for candidate in ("fair_bench_2p5.conf", "fair_bench.conf", "fair_bench_2p20.conf"):
        path = folder / candidate
        if path.is_file():
            return rel(path)
    return None


def output_prefix(p: dict) -> str:
    if p["driver"] == "PsiMain":
        return "PSI"
    if p["driver"] == "UpsuMain":
        return "UPSU"
    return "PSU"


def render(p: dict) -> str:
    protocol_id = display_protocol_name(p["name"])
    file_token = to_file_token(protocol_id)
    deep = DEEP_DIVE.get(protocol_id)
    conf = bench_conf_example(p["bench_dir"])
    prefix = output_prefix(p)

    lines = [
        f"# {protocol_id}",
        "",
        f"{p['family']}.",
        "",
        "## At a glance",
        "",
        "| | |",
        "|--|--|",
        f"| **Protocol ID** | `{protocol_id}` |",
        f"| **Functionality** | {p['functionality']} |",
        f"| **Security** | {p['security']} |",
        f"| **Output** | {p['output']} |",
        f"| **Factory** | `{p['factory']}` |",
        f"| **Driver** | `{p['driver']}` |",
        f"| **Config key** | `{p['config_key']} = {protocol_id}` |",
        f"| **OT / cost** | {p['ot_status']} |",
        "",
        "## Implementation",
        "",
        "| | |",
        "|--|--|",
        f"| Path | `mpc4j-psu/{p['module']}/` |",
        f"| Maven artifact | `{p['artifact']}` |",
        f"| Classes | `{p['classes']}` |",
        "",
        "### Init",
        "",
        p["init"],
        "",
        "### Online",
        "",
        p["online"],
        "",
        "## Benchmarking",
        "",
        "| | |",
        "|--|--|",
        f"| Config folder | `mpc4j-psu/bench/configs/{p['bench_dir']}/` |",
    ]
    if p.get("bench_dir_extra"):
        lines.append(f"| UPSU configs | `mpc4j-psu/bench/configs/{p['bench_dir_extra']}/` |")
    if conf:
        lines.append(f"| Example config | `{conf}` |")
    lines += [
        f"| Output files | `temp/{prefix}_{file_token}_fair_bench_2p*_<element_bits>_<party>_<threads>.output` |",
        "",
        "Run (example, 2^5 × 2^5):",
        "",
        "```bash",
        f"./scripts/run_psu_fair.sh 5 --only {protocol_id} --force",
        "```",
        "",
        "Summarize:",
        "",
        "```bash",
        "python3 scripts/summarize_psu_fair_outputs.py --out temp/psu_fair_summary_2p5.csv",
        "```",
        "",
    ]
    if p.get("notes"):
        lines += ["## Notes", "", p["notes"], ""]
    if deep:
        lines += [
            "## Detailed guide",
            "",
            f"Full implementation write-up: [{deep}]({deep})",
            "",
        ]
    lines += [
        "## See also",
        "",
        "- [Protocol index](README.md)",
        "- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md)",
        "- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md)",
        "- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)",
        "",
    ]
    return "\n".join(lines)


def category_key(p: dict) -> str:
    func = p["functionality"]
    if func.startswith("Balanced"):
        return "Balanced PSU"
    if func == "PSI":
        return "PSI"
    if "UPSU" in func:
        return "Unbalanced UPSU"
    if "Garbled" in func:
        return "Garbled-circuit set ops"
    return func


def render_readme() -> str:
    by_cat: dict[str, list[dict]] = {}
    for p in PROTOCOLS:
        by_cat.setdefault(category_key(p), []).append(p)

    lines = [
        "# Protocol documentation",
        "",
        "One page per runnable protocol, titled with the **bench display name** "
        "(same labels as fair-bench CSV summaries). Internal enum IDs appear in each page.",
        "",
        "Regenerate:",
        "",
        "```bash",
        "python3 scripts/gen_protocol_docs.py",
        "```",
        "",
        "Display names are defined in `scripts/libpsu/protocol_display_names.py`.",
        "",
    ]

    order = [
        "Balanced PSU",
        "Unbalanced UPSU",
        "PSI",
        "Garbled-circuit set ops",
    ]
    for cat in order:
        items = sorted(by_cat.get(cat, []), key=lambda x: display_protocol_name(x["name"]))
        if not items:
            continue
        lines += [f"## {cat}", ""]
        for p in items:
            internal = p["name"]
            slug = doc_slug(internal)
            display = display_protocol_name(internal)
            deep = DEEP_DIVE.get(internal)
            suffix = " — [detailed guide](../Ours_IMPLEMENTATION.md)" if deep else ""
            lines.append(
                f"- [{display}]({slug}.md) (`{internal}`){suffix}"
            )
        lines.append("")

    lines += [
        "## Aggregate references",
        "",
        "- [Docs index](../README.md)",
        "- [PROTOCOL_MAPPING.md](../PROTOCOL_MAPPING.md) — enum → path",
        "- [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](../PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) — cost accounting",
        "- [OT_BASE_COST_AUDIT.md](../OT_BASE_COST_AUDIT.md)",
        "- [ADD_NEW_PROTOCOL.md](../ADD_NEW_PROTOCOL.md)",
        "",
    ]
    return "\n".join(lines)


def render_docs_index() -> str:
    lines = [
        "# libPSU documentation",
        "",
        "Documentation for the `mpc4j-psu` reactor: protocol implementations, fair benchmarks, and integration.",
        "",
        "## Quick links",
        "",
        "| Topic | Document |",
        "|-------|----------|",
        "| **All protocols (by display name)** | [protocols/README.md](protocols/README.md) |",
        "| Enum → module path | [PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md) |",
        "| Implementation + benchmark cost | [PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md](PROTOCOL_IMPLEMENTATIONS_AND_BENCHMARK_COST.md) |",
        "| OT / Base OT audit | [OT_BASE_COST_AUDIT.md](OT_BASE_COST_AUDIT.md) |",
        "| Add a new protocol | [ADD_NEW_PROTOCOL.md](ADD_NEW_PROTOCOL.md) |",
        "| Library usage | [LIBRARY_USAGE.md](LIBRARY_USAGE.md) |",
        "| Reactor layout | [ARCHITECTURE.md](ARCHITECTURE.md) |",
        "",
        "## Display names",
        "",
        "Benchmark CSVs and summaries use paper-style labels (e.g. `PKC:CheZhaZha24`, `USENIX:HaoWan26`). "
        "The mapping from internal enum IDs is in `scripts/libpsu/protocol_display_names.py`.",
        "",
        "## Balanced PSU (display names)",
        "",
    ]
    for p in PROTOCOLS:
        if not category_key(p).startswith("Balanced"):
            continue
        slug = doc_slug(p["name"])
        display = display_protocol_name(p["name"])
        lines.append(f"- [{display}](protocols/{slug}.md) — `{p['name']}`")
    lines += [
        "",
        "## Other families",
        "",
    ]
    for p in PROTOCOLS:
        cat = category_key(p)
        if cat.startswith("Balanced"):
            continue
        slug = doc_slug(p["name"])
        display = display_protocol_name(p["name"])
        lines.append(f"- **{cat}:** [{display}](protocols/{slug}.md) — `{p['name']}`")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    expected_slugs = {doc_slug(p["name"]) for p in PROTOCOLS}

    # Remove stale docs named by internal ID or old generator output.
    for path in OUT.glob("*.md"):
        if path.name == "README.md":
            continue
        stem = path.stem
        if stem not in expected_slugs:
            path.unlink()
            print(f"removed stale {path.relative_to(REPO)}")

    written = 0
    for p in PROTOCOLS:
        path = OUT / f"{doc_slug(p['name'])}.md"
        body = render(p)
        if path.is_file() and path.read_text(encoding="utf-8") == body:
            continue
        path.write_text(body, encoding="utf-8")
        print(f"wrote {path.relative_to(REPO)}")
        written += 1

    readme = OUT / "README.md"
    readme_body = render_readme()
    if not readme.is_file() or readme.read_text(encoding="utf-8") != readme_body:
        readme.write_text(readme_body, encoding="utf-8")
        print(f"wrote {readme.relative_to(REPO)}")
        written += 1

    docs_index = REPO / "mpc4j-psu" / "docs" / "README.md"
    index_body = render_docs_index()
    if not docs_index.is_file() or docs_index.read_text(encoding="utf-8") != index_body:
        docs_index.write_text(index_body, encoding="utf-8")
        print(f"wrote {docs_index.relative_to(REPO)}")
        written += 1

    print(f"done ({written} file(s) updated)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
