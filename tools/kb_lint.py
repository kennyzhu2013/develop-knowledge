#!/usr/bin/env python3
"""知识库校验（知识 CI）。

    python tools/kb_lint.py                # 校验，有 ERROR 时退出码为 1
    python tools/kb_lint.py --strict       # WARN 也视为失败
    python tools/kb_lint.py --write-deps   # 根据 05-code/services/*.yaml 重新生成依赖图
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
from pathlib import Path

import yaml

from kb_common import (
    ROOT, SKILLS_DIR, Doc, ensure_utf8_stdout, iter_knowledge_docs, load_knowledge,
    load_yaml, parse_frontmatter, rel,
)

REQUIRED_META = ["id", "title", "domain", "kind", "audience", "owner", "status", "last_verified"]
KINDS = {"stable", "dynamic", "historical"}
STATUSES = {"draft", "active", "deprecated"}
ADR_STATUSES = {"Proposed", "Accepted", "Deprecated", "Superseded"}
STALE_DAYS = {"dynamic": 90, "stable": 365, "historical": None}

PATH_REF_RE = re.compile(r"`((?:0\d|10|99)-[^`\s]+|roles/[^`\s]+|knowledge\.yaml|AGENTS\.md)`")
PHONE_RE = re.compile(r"(?<![\d.])1[3-9]\d{9}(?!\d)")
SECRET_RE = re.compile(r"(?i)\b(password|passwd|secret|token|access[_-]?key)\b\s*[:=]\s*['\"]?[A-Za-z0-9/+_\-]{8,}")
MERMAID_RE = re.compile(r"```mermaid\n(.*?)```", re.S)
KEBAB_NODE_RE = re.compile(r"(?<![\w\-./])([a-z][a-z0-9]*(?:-[a-z0-9]+)+)(?![\w\-./])")

DEPS_HEADER = """\
meta:
  id: code-dependency-map
  title: 服务依赖图
  domain: code
  kind: dynamic
  audience: [all]
  owner: TODO
  status: draft
  last_verified: {today}
  # 由 tools/kb_lint.py --write-deps 根据 05-code/services/*.yaml 汇总生成，请勿手改。
  generated_by: kb_lint

"""


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, where: str, msg: str) -> None:
        self.errors.append(f"ERROR {where}: {msg}")

    def warn(self, where: str, msg: str) -> None:
        self.warnings.append(f"WARN  {where}: {msg}")


def as_list(v) -> list:
    if v is None:
        return []
    return v if isinstance(v, list) else [v]


def check_meta(doc: Doc, kb: dict, rep: Report) -> None:
    where = doc.rel
    meta = doc.meta
    if meta is None:
        rep.error(where, "缺少 frontmatter（.md）或 meta 块（.yaml）")
        return
    for key in REQUIRED_META:
        if meta.get(key) in (None, ""):
            rep.error(where, f"缺少必填字段 {key}")
    if meta.get("kind") and meta["kind"] not in KINDS:
        rep.error(where, f"kind={meta['kind']} 非法，可选 {sorted(KINDS)}")
    if meta.get("status") and meta["status"] not in STATUSES:
        rep.error(where, f"status={meta['status']} 非法，可选 {sorted(STATUSES)}")
    if meta.get("domain") and meta["domain"] not in kb["domains"]:
        rep.error(where, f"domain={meta['domain']} 不在 knowledge.yaml domains 中")
    valid_aud = set(kb["roles"]) | {"all"}
    for a in as_list(meta.get("audience")):
        if a not in valid_aud:
            rep.error(where, f"audience 含未知角色 {a}")
    if "adr_status" in meta and meta["adr_status"] not in ADR_STATUSES:
        rep.error(where, f"adr_status={meta['adr_status']} 非法")
    if meta.get("adr_status") == "Superseded" and not meta.get("superseded_by"):
        rep.error(where, "Superseded 的 ADR 必须填写 superseded_by")
    if str(meta.get("owner", "")).startswith("TODO"):
        rep.warn(where, "owner 未填写")

    lv = meta.get("last_verified")
    if lv:
        try:
            day = lv if isinstance(lv, dt.date) else dt.date.fromisoformat(str(lv))
        except ValueError:
            rep.error(where, f"last_verified={lv} 不是 YYYY-MM-DD")
        else:
            limit = STALE_DAYS.get(meta.get("kind"))
            if limit and (dt.date.today() - day).days > limit:
                rep.warn(where, f"last_verified 已超过 {limit} 天，请与代码/线上重新核对")


def check_path_refs(doc: Doc, rep: Report) -> None:
    text = doc.body or ""
    for m in PATH_REF_RE.finditer(text):
        ref = m.group(1).rstrip("，。；：,.;:")
        if any(c in ref for c in "<>*{}") or "NNN" in ref or "YYYY" in ref:
            continue
        if not (ROOT / ref).exists():
            rep.error(doc.rel, f"引用的路径不存在：{ref}")


def check_sensitive(path: Path, text: str, rep: Report) -> None:
    for i, line in enumerate(text.splitlines(), 1):
        if PHONE_RE.search(line):
            rep.error(f"{rel(path)}:{i}", "疑似真实手机号，请脱敏")
        if SECRET_RE.search(line):
            rep.error(f"{rel(path)}:{i}", "疑似密钥 / 密码")


def check_knowledge_yaml(kb: dict, rep: Report) -> None:
    where = "knowledge.yaml"
    for name, r in kb["roles"].items():
        for p in [r.get("entry")] + as_list(r.get("always_load")):
            if p and not (ROOT / p).exists():
                rep.error(where, f"roles.{name} 引用的路径不存在：{p}")
        for s in as_list(r.get("skills")):
            if not (SKILLS_DIR / s / "SKILL.md").exists():
                rep.error(where, f"roles.{name} 引用的 Skill 不存在：{s}")
    for name, d in kb["domains"].items():
        for p in as_list(d.get("paths")):
            if not (ROOT / p).exists():
                rep.error(where, f"domains.{name} 路径不存在：{p}")
        for s in as_list(d.get("services")):
            if s not in kb["services"]:
                rep.error(where, f"domains.{name} 引用未登记的服务：{s}")
    for s in kb["services"]:
        if not (ROOT / "05-code" / "services" / f"{s}.yaml").exists():
            rep.error(where, f"服务 {s} 缺少 05-code/services/{s}.yaml")
    ids = set()
    for r in kb.get("routes", []):
        rid = r.get("id")
        if rid in ids:
            rep.error(where, f"routes 中 id 重复：{rid}")
        ids.add(rid)
        if not r.get("keywords"):
            rep.error(where, f"route {rid} 没有 keywords")
        paths = as_list(r.get("load"))
        for role, extra in (r.get("by_role") or {}).items():
            if role not in kb["roles"]:
                rep.error(where, f"route {rid} by_role 含未知角色 {role}")
            paths += as_list(extra)
        for p in paths:
            if not (ROOT / p).exists():
                rep.error(where, f"route {rid} 路径不存在：{p}")
        for s in as_list(r.get("skills")):
            if not (SKILLS_DIR / s / "SKILL.md").exists():
                rep.error(where, f"route {rid} 引用的 Skill 不存在：{s}")


def check_skills(kb: dict, rep: Report) -> None:
    for skill_md in sorted(SKILLS_DIR.glob("*/SKILL.md")):
        where = rel(skill_md)
        meta, body = parse_frontmatter(skill_md.read_text(encoding="utf-8"))
        if not meta:
            rep.error(where, "缺少 frontmatter")
            continue
        if meta.get("name") != skill_md.parent.name:
            rep.error(where, f"name 必须与目录名一致：{skill_md.parent.name}")
        if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", str(meta.get("name", ""))):
            rep.error(where, "name 只能包含小写字母、数字和连字符")
        if not meta.get("description"):
            rep.error(where, "缺少 description")
        for r in as_list(meta.get("roles")):
            if r not in kb["roles"]:
                rep.error(where, f"roles 含未知角色 {r}")
        check_path_refs(Doc(skill_md, meta, body), rep)


def load_named(path: str, key: str, field: str = "id") -> set[str]:
    data = load_yaml(ROOT / path) or {}
    return {str(x.get(field)) for x in data.get(key, []) if isinstance(x, dict)}


def check_services(kb: dict, rep: Report) -> dict[str, dict]:
    repos = load_named("05-code/repositories.yaml", "repositories")
    events = load_named("06-data/events/events.yaml", "events", "name")
    services = {}
    for f in sorted((ROOT / "05-code" / "services").glob("*.yaml")):
        where = rel(f)
        data = load_yaml(f) or {}
        svc = data.get("service") or {}
        name = svc.get("name")
        if name != f.stem:
            rep.error(where, f"service.name={name} 必须与文件名一致")
        if name not in kb["services"]:
            rep.error(where, f"服务 {name} 未在 knowledge.yaml services 中登记")
        if svc.get("repository") not in repos:
            rep.error(where, f"repository={svc.get('repository')} 不在 05-code/repositories.yaml 中")
        for dep in as_list((data.get("dependencies") or {}).get("services")):
            if dep not in kb["services"]:
                rep.error(where, f"依赖未登记的服务：{dep}")
        ev = data.get("events") or {}
        for direction in ("produce", "consume"):
            for e in as_list(ev.get(direction)):
                if e not in events:
                    rep.error(where, f"events.{direction} 中的 {e} 不在 06-data/events/events.yaml 中")
        for rb in as_list(data.get("runbooks")):
            if not (ROOT / rb).exists():
                rep.error(where, f"Runbook 不存在：{rb}")
        services[name] = data
    return services


def check_fraud(rep: Report) -> None:
    base = ROOT / "02-call-qc" / "fraud"
    behaviors = load_named("02-call-qc/fraud/risk-behaviors.yaml", "behaviors")
    rules_data = load_yaml(base / "rules.yaml") or {}
    rules = {r["id"] for r in rules_data.get("rules", [])}
    for r in rules_data.get("rules", []):
        for rb in as_list(r.get("detects")):
            if rb not in behaviors:
                rep.error("02-call-qc/fraud/rules.yaml", f"{r['id']} 引用不存在的风险行为 {rb}")
    patterns = load_yaml(base / "fraud-patterns.yaml") or {}
    seen = set()
    for p in patterns.get("patterns", []):
        where = f"02-call-qc/fraud/fraud-patterns.yaml#{p.get('id')}"
        if p.get("id") in seen:
            rep.error(where, "诈骗模式 id 重复")
        seen.add(p.get("id"))
        refs = as_list(p.get("risk_behaviors")) + [s.get("behavior") for s in as_list(p.get("conversation_flow"))]
        for rb in refs:
            if rb not in behaviors:
                rep.error(where, f"引用不存在的风险行为 {rb}")
        flow = {s.get("behavior") for s in as_list(p.get("conversation_flow"))}
        if not flow <= set(as_list(p.get("risk_behaviors"))):
            rep.error(where, "conversation_flow 中的行为必须同时列在 risk_behaviors 中")
        for rid in as_list(p.get("rules")):
            if rid not in rules:
                rep.error(where, f"引用不存在的规则 {rid}")


def derive_edges(services: dict[str, dict]) -> list[dict]:
    edges = []
    producers: dict[str, list[str]] = {}
    for name, data in services.items():
        for dep in as_list((data.get("dependencies") or {}).get("services")):
            edges.append({"from": name, "to": dep, "via": "sync"})
        for e in as_list((data.get("events") or {}).get("produce")):
            producers.setdefault(e, []).append(name)
    for name, data in services.items():
        for e in as_list((data.get("events") or {}).get("consume")):
            for p in producers.get(e, []):
                edges.append({"from": p, "to": name, "via": f"event:{e}"})
    return sorted(edges, key=lambda x: (x["from"], x["to"], x["via"]))


def check_deps(services: dict[str, dict], rep: Report) -> None:
    path = ROOT / "05-code" / "dependency-map.yaml"
    current = sorted((load_yaml(path) or {}).get("edges", []), key=lambda x: (x["from"], x["to"], x["via"]))
    if current != derive_edges(services):
        rep.warn("05-code/dependency-map.yaml", "与 services/*.yaml 不一致，运行 python tools/kb_lint.py --write-deps 重新生成")


def write_deps(services: dict[str, dict]) -> None:
    path = ROOT / "05-code" / "dependency-map.yaml"
    body = yaml.safe_dump({"edges": derive_edges(services)}, allow_unicode=True, sort_keys=False)
    path.write_text(DEPS_HEADER.format(today=dt.date.today().isoformat()) + body, encoding="utf-8")
    print(f"已写入 {rel(path)}")


def check_mermaid(doc: Doc, kb: dict, rep: Report) -> None:
    if not doc.rel.startswith("04-architecture/"):
        return
    known = set(kb["services"])
    for block in MERMAID_RE.findall(doc.body or ""):
        for line in block.splitlines():
            if line.strip().startswith(("%%", "flowchart", "graph")):
                continue
            parts = line.split("|")
            outside_labels = parts[0] if len(parts) == 1 else parts[0] + " " + parts[-1]
            for node in dict.fromkeys(KEBAB_NODE_RE.findall(outside_labels)):
                if node not in known:
                    rep.error(doc.rel, f"架构图节点 {node} 不是已登记的服务名")


def main() -> int:
    ensure_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--strict", action="store_true", help="WARN 也视为失败")
    ap.add_argument("--write-deps", action="store_true", help="重新生成 05-code/dependency-map.yaml")
    ap.add_argument("--quiet", action="store_true", help="只输出 ERROR")
    args = ap.parse_args()

    rep = Report()
    kb = load_knowledge()
    check_knowledge_yaml(kb, rep)

    ids: dict[str, str] = {}
    docs = list(iter_knowledge_docs())
    for doc in docs:
        if doc.error:
            rep.error(doc.rel, doc.error)
            continue
        check_meta(doc, kb, rep)
        if doc.path.suffix == ".md":
            check_path_refs(doc, rep)
            check_mermaid(doc, kb, rep)
        check_sensitive(doc.path, doc.path.read_text(encoding="utf-8"), rep)
        did = (doc.meta or {}).get("id")
        if did:
            if did in ids:
                rep.error(doc.rel, f"id={did} 与 {ids[did]} 重复")
            ids[did] = doc.rel
    for doc in docs:
        for r in as_list((doc.meta or {}).get("related")):
            if r not in ids:
                rep.error(doc.rel, f"related 引用不存在的文档 id：{r}")
        sup = (doc.meta or {}).get("superseded_by")
        if sup and sup not in ids:
            rep.error(doc.rel, f"superseded_by 引用不存在的 ADR：{sup}")

    for top in ("AGENTS.md", "README.md"):
        p = ROOT / top
        check_path_refs(Doc(p, {}, p.read_text(encoding="utf-8")), rep)

    check_skills(kb, rep)
    services = check_services(kb, rep)
    check_fraud(rep)

    if args.write_deps:
        write_deps(services)
    else:
        check_deps(services, rep)

    for line in rep.errors:
        print(line)
    if not args.quiet:
        for line in rep.warnings:
            print(line)
    drafts = sum(1 for d in docs if (d.meta or {}).get("status") == "draft")
    print(f"\n共 {len(docs)} 个知识文件（draft {drafts}），{len(rep.errors)} 个错误，{len(rep.warnings)} 个警告")
    failed = rep.errors or (args.strict and rep.warnings)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
