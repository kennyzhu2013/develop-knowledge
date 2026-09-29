"""Shared helpers for the knowledge-base tools."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parent.parent

KNOWLEDGE_DIRS = [
    "00-context", "01-mid-number", "02-call-qc", "03-ai", "04-architecture",
    "05-code", "06-data", "07-operation", "08-development", "09-decisions",
    "10-lessons", "roles",
]
SKILLS_DIR = ROOT / "99-agent" / "skills"
TEMPLATES_DIR = ROOT / "99-agent" / "templates"

DOC_SUFFIXES = {".md", ".yaml", ".yml"}
FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*(\n|\Z)", re.S)


def ensure_utf8_stdout() -> None:
    # Windows 控制台默认 GBK，输出中文会报错。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")


def rel(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def load_yaml(path: Path) -> Any:
    with path.open(encoding="utf-8") as f:
        return yaml.safe_load(f)


def load_knowledge() -> dict:
    return load_yaml(ROOT / "knowledge.yaml")


def parse_frontmatter(text: str) -> tuple[dict | None, str]:
    m = FRONTMATTER_RE.match(text)
    if not m:
        return None, text
    meta = yaml.safe_load(m.group(1)) or {}
    return meta, text[m.end():]


@dataclass
class Doc:
    path: Path
    meta: dict | None
    body: str = ""
    data: Any = None
    error: str | None = None
    extra: dict = field(default_factory=dict)

    @property
    def rel(self) -> str:
        return rel(self.path)

    @property
    def audience(self) -> list[str]:
        aud = (self.meta or {}).get("audience") or ["all"]
        return aud if isinstance(aud, list) else [aud]

    def visible_to(self, role: str | None) -> bool:
        return role is None or "all" in self.audience or role in self.audience


def load_doc(path: Path) -> Doc:
    text = path.read_text(encoding="utf-8")
    if path.suffix == ".md":
        try:
            meta, body = parse_frontmatter(text)
        except yaml.YAMLError as e:
            return Doc(path, None, text, error=f"frontmatter YAML 解析失败: {e}")
        return Doc(path, meta, body)
    try:
        data = yaml.safe_load(text)
    except yaml.YAMLError as e:
        return Doc(path, None, error=f"YAML 解析失败: {e}")
    meta = data.get("meta") if isinstance(data, dict) else None
    return Doc(path, meta, text, data=data)


def iter_files(path: Path):
    if path.is_file():
        if path.suffix in DOC_SUFFIXES:
            yield path
        return
    for p in sorted(path.rglob("*")):
        if p.is_file() and p.suffix in DOC_SUFFIXES:
            yield p


def iter_knowledge_docs():
    for d in KNOWLEDGE_DIRS:
        base = ROOT / d
        if base.exists():
            for p in iter_files(base):
                yield load_doc(p)


def expand(path_spec: str, role: str | None = None) -> list[Doc]:
    """Expand a knowledge.yaml path (file or directory) into docs visible to role."""
    target = ROOT / path_spec
    if not target.exists():
        return []
    docs = [load_doc(p) for p in iter_files(target)]
    if target.is_file():
        return docs
    return [d for d in docs if d.visible_to(role)]
