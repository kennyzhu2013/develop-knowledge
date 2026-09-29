#!/usr/bin/env python3
"""生成各 Agent 工具的入口文件和 Skills 镜像，使 AGENTS.md 与 99-agent/skills/ 保持唯一事实源。

    python tools/sync_adapters.py           # 生成 / 更新
    python tools/sync_adapters.py --check   # 只检查是否最新（CI 使用），不一致时退出码为 1

生成目标：
  CLAUDE.md                        Claude Code（@ 导入 AGENTS.md）
  GEMINI.md / QWEN.md              Gemini CLI / Qwen Code（@ 导入 AGENTS.md）
  .github/copilot-instructions.md  GitHub Copilot
  .trae/rules/project_rules.md     Trae
  .claude/skills/<name>/           Claude Code、VS Code Copilot
  .agents/skills/<name>/           Codex、Cursor 等遵循 Agent Skills 约定的工具
Cursor、Codex、OpenCode 原生读取 AGENTS.md，无需额外入口文件。
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from kb_common import ROOT, SKILLS_DIR, ensure_utf8_stdout

HEADER = "<!-- 由 tools/sync_adapters.py 生成，请勿手改；请修改 AGENTS.md -->\n"

POINTER = HEADER + """
本仓库的 Agent 规则统一维护在 AGENTS.md，开始任何任务前必须先完整阅读并遵守它。

{import_line}
"""

ENTRY_FILES = {
    "CLAUDE.md": "@AGENTS.md",
    "GEMINI.md": "@./AGENTS.md",
    "QWEN.md": "@./AGENTS.md",
    ".github/copilot-instructions.md": "请阅读仓库根目录的 `AGENTS.md`。",
    ".trae/rules/project_rules.md": "请阅读仓库根目录的 `AGENTS.md`。",
}

SKILL_MIRRORS = [".claude/skills", ".agents/skills"]


def expected_files() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, import_line in ENTRY_FILES.items():
        files[ROOT / name] = POINTER.format(import_line=import_line).encode("utf-8")
    for skill_dir in sorted(p for p in SKILLS_DIR.iterdir() if (p / "SKILL.md").exists()):
        for src in sorted(skill_dir.rglob("*")):
            if src.is_file():
                for mirror in SKILL_MIRRORS:
                    files[ROOT / mirror / src.relative_to(SKILLS_DIR)] = src.read_bytes()
    return files


def stale_mirror_files(expected: dict[Path, bytes]) -> list[Path]:
    stale = []
    for mirror in SKILL_MIRRORS:
        base = ROOT / mirror
        if base.exists():
            stale += [p for p in base.rglob("*") if p.is_file() and p not in expected]
    return stale


def main() -> int:
    ensure_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    expected = expected_files()
    changed = [p for p, data in expected.items() if not p.exists() or p.read_bytes() != data]
    stale = stale_mirror_files(expected)

    if args.check:
        for p in changed:
            print(f"过期：{p.relative_to(ROOT).as_posix()}")
        for p in stale:
            print(f"多余：{p.relative_to(ROOT).as_posix()}")
        if changed or stale:
            print("请运行 python tools/sync_adapters.py 并提交结果")
            return 1
        print("Agent 适配文件已是最新")
        return 0

    for p in changed:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(expected[p])
    for p in stale:
        p.unlink()
    for mirror in SKILL_MIRRORS:
        for d in sorted((ROOT / mirror).rglob("*"), reverse=True):
            if d.is_dir() and not any(d.iterdir()):
                shutil.rmtree(d)
    print(f"更新 {len(changed)} 个文件，删除 {len(stale)} 个多余文件")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
