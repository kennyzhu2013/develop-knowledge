#!/usr/bin/env python3
"""任务 → 知识召回。

根据任务描述和角色，从 knowledge.yaml 的 routes 中选出最相关的知识文件和 Skill，
让 Agent 只读取最小必要上下文。

    python tools/kb_route.py "修改700号码的号码申请接口" --role developer
    python tools/kb_route.py "质检结果延迟告警" --format json
"""

from __future__ import annotations

import argparse
import json

from kb_common import SKILLS_DIR, ensure_utf8_stdout, expand, load_knowledge

ROLE_HINTS = {
    "ops": ["告警", "故障", "部署", "上线", "回滚", "扩容", "积压", "宕机", "监控", "重启", "应急"],
    "architect": ["架构", "评审", "选型", "方案", "设计", "容量", "高可用", "演进", "解耦"],
    "qa": ["用例", "测试", "验证", "回归", "误报", "漏报", "评测"],
}


def infer_role(task: str) -> str:
    scores = {role: sum(k in task for k in kws) for role, kws in ROLE_HINTS.items()}
    best = max(scores, key=scores.get)
    return best if scores[best] > 0 else "developer"


def score_routes(task: str, routes: list[dict]) -> list[tuple[int, dict, list[str]]]:
    text = task.lower()
    scored = []
    for r in routes:
        hits = [k for k in r.get("keywords", []) if str(k).lower() in text]
        if hits:
            scored.append((len(hits), r, hits))
    scored.sort(key=lambda x: -x[0])
    return scored


def route(task: str, role: str | None, max_files: int | None = None) -> dict:
    kb = load_knowledge()
    roles = kb["roles"]
    role = role or infer_role(task)
    if role not in roles:
        raise SystemExit(f"未知角色 {role}，可选：{', '.join(roles)}")
    role_cfg = roles[role]
    router = kb.get("router", {})
    max_files = max_files or router.get("max_files", 20)

    matched = score_routes(task, kb.get("routes", []))[: router.get("max_routes", 3)]

    specs: list[tuple[str, str]] = [(role_cfg["entry"], "角色视图")]
    specs += [(p, "角色默认") for p in role_cfg.get("always_load", [])]
    skills: list[str] = []
    for _, r, _ in matched:
        specs += [(p, f"路由 {r['id']}") for p in r.get("load", [])]
        specs += [(p, f"路由 {r['id']}（{role}）") for p in r.get("by_role", {}).get(role, [])]
        skills += r.get("skills", [])
    if not matched:
        specs += [("00-context/business-overview.md", "未命中路由，回退"),
                  ("04-architecture/overall.md", "未命中路由，回退")]
        skills.append("understand-system")

    files: list[dict] = []
    seen: set[str] = set()
    for spec, reason in specs:
        for doc in expand(spec, role):
            if doc.rel in seen:
                continue
            seen.add(doc.rel)
            files.append({
                "path": doc.rel,
                "title": (doc.meta or {}).get("title", ""),
                "reason": reason,
            })

    skills = list(dict.fromkeys(skills))
    role_skills = set(role_cfg.get("skills", []))
    skills.sort(key=lambda s: s not in role_skills)
    skill_items = [{
        "name": s,
        "path": f"99-agent/skills/{s}/SKILL.md",
        "exists": (SKILLS_DIR / s / "SKILL.md").exists(),
    } for s in skills]

    return {
        "task": task,
        "role": role,
        "role_name": role_cfg.get("name", role),
        "matched_routes": [{"id": r["id"], "hits": hits} for _, r, hits in matched],
        "files": files[:max_files],
        "truncated": max(0, len(files) - max_files),
        "skills": skill_items,
    }


def print_text(result: dict) -> None:
    print(f"任务：{result['task']}")
    print(f"角色：{result['role_name']}（{result['role']}）")
    if result["matched_routes"]:
        routes = "；".join(f"{r['id']}[{', '.join(r['hits'])}]" for r in result["matched_routes"])
        print(f"命中路由：{routes}")
    else:
        print("命中路由：无（回退到全局概览，请考虑在 knowledge.yaml 中补充路由关键词）")
    print("\n建议阅读（按顺序）：")
    for i, f in enumerate(result["files"], 1):
        title = f" - {f['title']}" if f["title"] else ""
        print(f"  {i:2d}. {f['path']}{title}    ← {f['reason']}")
    if result["truncated"]:
        print(f"  …… 另有 {result['truncated']} 个文件被截断，可用 --max-files 调整")
    print("\n推荐 Skill：")
    for s in result["skills"]:
        flag = "" if s["exists"] else "（缺失）"
        print(f"  - {s['name']}: {s['path']}{flag}")


def main() -> None:
    ensure_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("task", help="任务描述")
    ap.add_argument("--role", help="architect | developer | ops | qa；不填则按任务推断")
    ap.add_argument("--format", choices=["text", "json"], default="text")
    ap.add_argument("--max-files", type=int)
    args = ap.parse_args()

    result = route(args.task, args.role, args.max_files)
    if args.format == "json":
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print_text(result)


if __name__ == "__main__":
    main()
