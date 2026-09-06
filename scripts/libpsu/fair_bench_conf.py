"""Shared fair-bench .conf load/patch helpers."""

from __future__ import annotations

import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "mpc4j-psu" / "bench" / "configs"
PSU_ROOT = RES / "psu"
PSI_ROOT = RES / "psi"
BA12_ROOT = RES / "ba12"
UPSU_ROOT = RES / "upsu"


def force_boolean_property(text: str, key: str, value: bool) -> str:
    rendered = "true" if value else "false"
    property_pattern = rf"^{re.escape(key)}\s*=.*$"
    if re.search(property_pattern, text, re.MULTILINE):
        return re.sub(
            property_pattern,
            f"{key} = {rendered}",
            text,
            count=1,
            flags=re.MULTILINE,
        )
    for anchor_key in ("skip_warmup", "parallel", "pto_type"):
        anchor_pattern = rf"^({re.escape(anchor_key)}\s*=.*)$"
        if re.search(anchor_pattern, text, re.MULTILINE):
            return re.sub(
                anchor_pattern,
                lambda match: f"{match.group(1)}\n{key} = {rendered}",
                text,
                count=1,
                flags=re.MULTILINE,
            )
    return text


def set_property(text: str, key: str, value: str) -> str:
    pattern = rf"^{re.escape(key)}\s*=.*$"
    if re.search(pattern, text, re.MULTILINE):
        return re.sub(
            pattern,
            f"{key} = {value}",
            text,
            count=1,
            flags=re.MULTILINE,
        )
    return text


def ensure_append_string(text: str, append: str) -> str:
    if re.search(r"^append_string\s*=", text, re.MULTILINE):
        return set_property(text, "append_string", append)
    return re.sub(
        r"^(pto_type\s*=.*)$",
        f"\\1\n\nappend_string = {append}",
        text,
        count=1,
        flags=re.MULTILINE,
    )


def set_log_sizes(text: str, server_log: int, client_log: int) -> str:
    text = set_property(text, "server_log_set_size", str(server_log))
    return set_property(text, "client_log_set_size", str(client_log))


def patch_append_and_sizes(
    text: str, append: str, server_log: int, client_log: int
) -> str:
    return set_log_sizes(ensure_append_string(text, append), server_log, client_log)


def apply_leading_header(text: str, header: str) -> str:
    if text.startswith("#"):
        return re.sub(r"^#.*\n(?:#.*\n)*", header, text, count=1)
    return header + "\n" + text


def replace_first_comment_line(text: str, header: str) -> str:
    if text.startswith("#"):
        return re.sub(r"^# [^\n]*\n", header, text, count=1)
    return header + "\n" + text


def load_first_config(folder: Path, names: tuple[str, ...]) -> str | None:
    for name in names:
        src = folder / name
        if src.is_file():
            text = src.read_text(encoding="utf-8")
            if "server_name" in text:
                return text
    return None


def write_if_changed(path: Path, body: str) -> bool:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file() and path.read_text(encoding="utf-8") == body:
        return False
    path.write_text(body, encoding="utf-8")
    return True
