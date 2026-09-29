#!/usr/bin/env python3
"""从业务代码仓库自动抽取代码知识，并与 05-code/services/<service>.yaml 对比。

基于正则的轻量扫描，覆盖常见写法：
  Java  : Spring MVC 映射注解、@FeignClient、@KafkaListener、@RocketMQMessageListener、
          kafkaTemplate / rocketMQTemplate 发送、@Table / @TableName
  Python: FastAPI / Flask 路由、__tablename__

    python tools/gen_code_index.py --repo ../call-qc --service call-qc-service           # 打印索引与差异
    python tools/gen_code_index.py --repo ../call-qc --service call-qc-service --write   # 写入 05-code/index/

生成结果写入 05-code/index/<service>.yaml（机器生成，请勿手改）；人工维护的 services/*.yaml 不会被修改，
差异以 DRIFT 形式输出，由人决定如何合并。扫描不到的写法（常量拼接的 Topic 等）会原样记录，需人工确认。
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

import yaml

from kb_common import ROOT, ensure_utf8_stdout, load_yaml, rel

SKIP_DIRS = {".git", "node_modules", "target", "build", "dist", ".venv", "venv", "__pycache__", ".idea", "test", "tests"}

JAVA_CLASS_MAPPING = re.compile(r"@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*\"([^\"]*)\"")
JAVA_METHOD_MAPPING = re.compile(
    r"@(Get|Post|Put|Delete|Patch|Request)Mapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*\"([^\"]*)\"([^)]*)\)")
JAVA_REQ_METHOD = re.compile(r"RequestMethod\.(\w+)")
JAVA_CLASS_DECL = re.compile(r"\b(class|interface)\s+\w+")
JAVA_FEIGN = re.compile(r"@FeignClient\(\s*(?:name\s*=\s*|value\s*=\s*)?\"([^\"]+)\"")
JAVA_KAFKA_LISTENER = re.compile(r"@KafkaListener\([^)]*topics\s*=\s*\{?([^})]*)")
JAVA_ROCKET_LISTENER = re.compile(r"@RocketMQMessageListener\([^)]*topic\s*=\s*\"([^\"]+)\"")
JAVA_SEND = re.compile(r"(?:kafkaTemplate|rocketMQTemplate|rocketMqTemplate)\s*\.\s*(?:send|syncSend|asyncSend|convertAndSend|sendOneWay)\(\s*\"([^\"]+)\"", re.I)
JAVA_TABLE = re.compile(r"@(?:Table|TableName)\(\s*(?:name\s*=\s*|value\s*=\s*)?\"([^\"]+)\"")

PY_ROUTE = re.compile(r"@\w+\.(get|post|put|delete|patch)\(\s*[\"']([^\"']+)")
PY_FLASK_ROUTE = re.compile(r"@\w+\.route\(\s*[\"']([^\"']+)[\"'](?:[^)]*methods\s*=\s*\[([^\]]*)\])?")
PY_TABLENAME = re.compile(r"__tablename__\s*=\s*[\"']([^\"']+)")

QUOTED = re.compile(r"\"([^\"]+)\"")


def iter_sources(repo: Path):
    for p in repo.rglob("*"):
        if p.suffix not in (".java", ".kt", ".py") or not p.is_file():
            continue
        if any(part in SKIP_DIRS for part in p.relative_to(repo).parts):
            continue
        yield p


def join_path(prefix: str, path: str) -> str:
    return "/" + "/".join(s.strip("/") for s in (prefix, path) if s.strip("/"))


def scan_java(text: str, loc: str, out: dict) -> None:
    prefix = ""
    head = text.split("{", 1)[0] if JAVA_CLASS_DECL.search(text) else ""
    m = JAVA_CLASS_MAPPING.search(head)
    if m:
        prefix = m.group(1)
    for m in JAVA_METHOD_MAPPING.finditer(text):
        if head and m.start() < len(head):
            continue
        kind, path, rest = m.groups()
        if kind == "Request":
            methods = JAVA_REQ_METHOD.findall(rest) or ["ANY"]
        else:
            methods = [kind.upper()]
        for method in methods:
            out["apis"].add((method, join_path(prefix, path), loc))
    for m in JAVA_FEIGN.finditer(text):
        out["calls"].add((m.group(1), loc))
    for m in JAVA_KAFKA_LISTENER.finditer(text):
        for t in QUOTED.findall(m.group(1)) or [m.group(1).strip()]:
            out["consume"].add((t, loc))
    for m in JAVA_ROCKET_LISTENER.finditer(text):
        out["consume"].add((m.group(1), loc))
    for m in JAVA_SEND.finditer(text):
        out["produce"].add((m.group(1), loc))
    for m in JAVA_TABLE.finditer(text):
        out["tables"].add((m.group(1), loc))


def scan_python(text: str, loc: str, out: dict) -> None:
    for m in PY_ROUTE.finditer(text):
        out["apis"].add((m.group(1).upper(), m.group(2), loc))
    for m in PY_FLASK_ROUTE.finditer(text):
        methods = re.findall(r"[\"'](\w+)[\"']", m.group(2) or "") or ["GET"]
        for method in methods:
            out["apis"].add((method.upper(), m.group(1), loc))
    for m in PY_TABLENAME.finditer(text):
        out["tables"].add((m.group(1), loc))


def scan(repo: Path) -> dict:
    out = {k: set() for k in ("apis", "calls", "consume", "produce", "tables")}
    for p in iter_sources(repo):
        text = p.read_text(encoding="utf-8", errors="ignore")
        loc = p.relative_to(repo).as_posix()
        if p.suffix == ".py":
            scan_python(text, loc, out)
        else:
            scan_java(text, loc, out)
    return out


def topic_to_event() -> dict[str, str]:
    data = load_yaml(ROOT / "06-data" / "mq" / "topics.yaml") or {}
    return {t["name"]: t.get("event") for t in data.get("topics", []) if t.get("name")}


def build_index(service: str, repo: Path, found: dict) -> dict:
    t2e = topic_to_event()

    def topics(key):
        return [{"topic": t, "event": t2e.get(t), "at": loc} for t, loc in sorted(found[key])]

    return {
        "meta": {
            "id": f"index-{service}",
            "title": f"{service} 代码索引（自动生成）",
            "domain": "code",
            "kind": "dynamic",
            "audience": ["developer", "architect"],
            "owner": "gen_code_index",
            "status": "active",
            "last_verified": dt.date.today().isoformat(),
            "generated_by": "gen_code_index",
            "source_repo": repo.name,
        },
        "service": service,
        "apis": [{"method": m, "path": p, "at": loc} for m, p, loc in sorted(found["apis"])],
        "outbound_calls": [{"target": t, "at": loc} for t, loc in sorted(found["calls"])],
        "mq": {"consume": topics("consume"), "produce": topics("produce")},
        "tables": [{"name": t, "at": loc} for t, loc in sorted(found["tables"])],
    }


def diff(service: str, index: dict) -> list[str]:
    path = ROOT / "05-code" / "services" / f"{service}.yaml"
    if not path.exists():
        return [f"DRIFT {service}: 知识库中没有 {rel(path)}，请用模板 99-agent/templates/service.yaml 新建"]
    doc = load_yaml(path) or {}
    out = []

    doc_apis = {(a.get("method"), a.get("path")) for a in (doc.get("apis") or {}).get("provides", [])}
    code_apis = {(a["method"], a["path"]) for a in index["apis"]}
    for m, p in sorted(code_apis - doc_apis):
        out.append(f"DRIFT api: 代码中有、知识库中没有 {m} {p}")
    for m, p in sorted(doc_apis - code_apis):
        if "TODO" not in str(p):
            out.append(f"DRIFT api: 知识库中有、代码中没找到 {m} {p}")

    events = doc.get("events") or {}
    for direction in ("consume", "produce"):
        code_events = set()
        for t in index["mq"][direction]:
            if t["event"]:
                code_events.add(t["event"])
            else:
                out.append(f"DRIFT mq.{direction}: Topic {t['topic']} 未在 06-data/mq/topics.yaml 登记（{t['at']}）")
        doc_events = set(events.get(direction) or [])
        for e in sorted(code_events - doc_events):
            out.append(f"DRIFT events.{direction}: 代码中有、知识库中没有 {e}")
        for e in sorted(doc_events - code_events):
            out.append(f"DRIFT events.{direction}: 知识库中有、代码中没扫描到 {e}（可能是动态 Topic，需人工确认）")

    doc_deps = set((doc.get("dependencies") or {}).get("services") or [])
    for c in index["outbound_calls"]:
        if c["target"] not in doc_deps:
            out.append(f"DRIFT dependencies: 代码调用了 {c['target']}，但未列入 dependencies.services（{c['at']}）")

    doc_tables = {t for t in doc.get("tables") or [] if "TODO" not in str(t)}
    code_tables = {t["name"] for t in index["tables"]}
    for t in sorted(code_tables - doc_tables):
        out.append(f"DRIFT tables: 代码中有、知识库中没有 {t}")
    return out


def main() -> int:
    ensure_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", required=True, type=Path, help="业务代码仓库路径")
    ap.add_argument("--service", required=True, help="服务名（knowledge.yaml services 中的名字）")
    ap.add_argument("--write", action="store_true", help="写入 05-code/index/<service>.yaml")
    ap.add_argument("--fail-on-drift", action="store_true", help="存在 DRIFT 时退出码为 1（用于业务仓库 CI）")
    args = ap.parse_args()

    repo = args.repo.resolve()
    if not repo.is_dir():
        print(f"仓库不存在：{repo}", file=sys.stderr)
        return 2

    index = build_index(args.service, repo, scan(repo))
    text = yaml.safe_dump(index, allow_unicode=True, sort_keys=False)
    if args.write:
        target = ROOT / "05-code" / "index" / f"{args.service}.yaml"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("# 由 tools/gen_code_index.py 自动生成，请勿手改。\n" + text, encoding="utf-8")
        print(f"已写入 {rel(target)}")
    else:
        print(text)

    drifts = diff(args.service, index)
    for d in drifts:
        print(d)
    print(f"\n{len(index['apis'])} 个接口，{len(index['mq']['consume'])} 个消费 Topic，"
          f"{len(index['mq']['produce'])} 个生产 Topic，{len(index['tables'])} 张表，{len(drifts)} 处差异")
    return 1 if drifts and args.fail_on_drift else 0


if __name__ == "__main__":
    raise SystemExit(main())
