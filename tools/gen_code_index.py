#!/usr/bin/env python3
"""从业务代码仓库自动抽取代码知识，并与 05-code/services/<service>.yaml 对比。

基于正则的轻量扫描，覆盖常见写法：
  Java  : Spring MVC 映射注解、JDK HttpServer createContext、@FeignClient、@KafkaListener、
          @RocketMQMessageListener、@RabbitListener（解析 static final 常量）、"TID_*" Topic 字面量、
          kafkaTemplate / rocketMQTemplate 发送、@Table / @TableName
  Go    : Gin 路由（解析 Group 前缀，包括通过函数参数传入的 Group）
  Python: FastAPI / Flask 路由（解析 Blueprint url_prefix）、__tablename__
  通用  : 配置文件（yaml/yml/toml/properties/xml）中的 Topic，SQL 字符串中的表名

    python tools/gen_code_index.py --repo vi-assets/vi-as-master --service vi-as           # 打印索引与差异
    python tools/gen_code_index.py --repo vi-assets/vi-as-master --service vi-as --write   # 写入 05-code/index/
    python tools/gen_code_index.py --all --write   # 按 05-code/repositories.yaml 扫描全部本地仓库

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
CODE_SUFFIXES = (".java", ".kt", ".py", ".go")
CONFIG_SUFFIXES = (".yaml", ".yml", ".toml", ".properties", ".xml")

JAVA_CLASS_MAPPING = re.compile(r"@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*\"([^\"]*)\"")
JAVA_METHOD_MAPPING = re.compile(
    r"@(Get|Post|Put|Delete|Patch|Request)Mapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*\"([^\"]*)\"([^)]*)\)")
JAVA_REQ_METHOD = re.compile(r"RequestMethod\.(\w+)")
JAVA_CLASS_DECL = re.compile(r"\b(class|interface)\s+\w+")
JAVA_CREATE_CONTEXT = re.compile(r"\.(?:createContext|addHandler)\(\s*\"(/[^\"]*)\"")
JAVA_FEIGN = re.compile(r"@FeignClient\(\s*(?:name\s*=\s*|value\s*=\s*)?\"([^\"]+)\"")
JAVA_KAFKA_LISTENER = re.compile(r"@KafkaListener\([^)]*topics\s*=\s*\{?([^})]*)")
JAVA_ROCKET_LISTENER = re.compile(r"@RocketMQMessageListener\([^)]*?topic\s*=\s*\"([^\"]+)\"", re.S)
JAVA_RABBIT_LISTENER = re.compile(r"@RabbitListener\([^)]*?queues\s*=\s*(\{[^}]*\}|[^,)]+)", re.S)
JAVA_SEND = re.compile(r"(?:kafkaTemplate|rocketMQTemplate|rocketMqTemplate)\s*\.\s*(?:send|syncSend|asyncSend|convertAndSend|sendOneWay)\(\s*\"([^\"]+)\"", re.I)
JAVA_TABLE = re.compile(r"@(?:Table|TableName)\(\s*(?:name\s*=\s*|value\s*=\s*)?\"([^\"]+)\"")
JAVA_CONST = re.compile(r"static\s+final\s+String\s+(\w+)\s*=\s*\"([^\"]*)\"")
TOPIC_LITERAL = re.compile(r"\"((?:TID|GID)_[A-Z0-9_]+)\"")

GO_GROUP_ARG = re.compile(r"(\w+)\(\s*\w+\.Group\(\s*\"([^\"]*)\"\s*\)\s*\)")
GO_GROUP_VAR = re.compile(r"(\w+)\s*:?=\s*(\w+)\.Group\(\s*\"([^\"]*)\"")
GO_FUNC_GROUP_PARAM = re.compile(r"func\s+(\w+)\(\s*(\w+)\s+\*gin\.RouterGroup")
GO_ROUTE = re.compile(r"\b(\w+)\.(GET|POST|PUT|DELETE|PATCH|Any)\(\s*\"([^\"]*)\"")

PY_BLUEPRINT = re.compile(r"(\w+)\s*=\s*Blueprint\([^)]*url_prefix\s*=\s*[\"']([^\"']+)")
PY_ROUTE = re.compile(r"@(\w+)\.(get|post|put|delete|patch)\(\s*[\"']([^\"']+)")
PY_FLASK_ROUTE = re.compile(r"@(\w+)\.route\(\s*[\"']([^\"']+)[\"'](?:[^)]*methods\s*=\s*\[([^\]]*)\])?")
PY_TABLENAME = re.compile(r"__tablename__\s*=\s*[\"']([^\"']+)")

CONFIG_TOPIC = re.compile(r"^\s*([\w.-]+)\s*[:=]\s*[\"']?((?:TID)_[A-Z0-9_]+)", re.M)

STRING_LITERAL = re.compile(r"\"\"\"(.*?)\"\"\"|'''(.*?)'''|\"((?:[^\"\\\n]|\\.)*)\"|'((?:[^'\\\n]|\\.)*)'", re.S)
SQL_HINT = re.compile(r"\b(select|insert|update|delete|replace)\b", re.I)
SQL_TABLE = re.compile(r"\b(?:from|into|update|join)\s+`?([a-zA-Z_][\w]*(?:\.[a-zA-Z_][\w]*)?)`?", re.I)
SQL_STOPWORDS = {"select", "set", "where", "values", "dual", "the", "a", "an", "to", "table", "ignore"}

QUOTED = re.compile(r"\"([^\"]+)\"")
LINE_COMMENT = re.compile(r"^\s*//.*$", re.M)
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)


def iter_files(repo: Path, suffixes: tuple[str, ...]):
    for p in sorted(repo.rglob("*")):
        if p.suffix not in suffixes or not p.is_file():
            continue
        if any(part in SKIP_DIRS for part in p.relative_to(repo).parts):
            continue
        yield p


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8", errors="ignore")


def strip_c_comments(text: str) -> str:
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def join_path(*parts: str) -> str:
    return "/" + "/".join(s.strip("/") for s in parts if s.strip("/"))


def gin_to_openapi(path: str) -> str:
    return re.sub(r":(\w+)", r"{\1}", path)


def scan_sql_tables(text: str, loc: str, out: dict) -> None:
    for m in STRING_LITERAL.finditer(text):
        s = next(g for g in m.groups() if g is not None)
        if not SQL_HINT.search(s):
            continue
        for t in SQL_TABLE.findall(s):
            if t.lower() in SQL_STOPWORDS or ("_" not in t and "." not in t) or t.isupper() or t.endswith("_"):
                continue
            out["tables"].add((t, loc))


def java_constants(repo: Path) -> dict[str, str]:
    consts = {}
    for p in iter_files(repo, (".java", ".kt")):
        cls = p.stem
        for name, value in JAVA_CONST.findall(read(p)):
            consts[name] = value
            consts[f"{cls}.{name}"] = value
    return consts


def scan_java(text: str, loc: str, out: dict, consts: dict[str, str]) -> None:
    text = strip_c_comments(text)
    prefix = ""
    head = text.split("{", 1)[0] if JAVA_CLASS_DECL.search(text) else ""
    m = JAVA_CLASS_MAPPING.search(head)
    if m:
        prefix = m.group(1)
    for m in JAVA_METHOD_MAPPING.finditer(text):
        if head and m.start() < len(head):
            continue
        kind, path, rest = m.groups()
        methods = (JAVA_REQ_METHOD.findall(rest) or ["ANY"]) if kind == "Request" else [kind.upper()]
        for method in methods:
            out["apis"].add((method, join_path(prefix, path), loc))
    for m in JAVA_CREATE_CONTEXT.finditer(text):
        out["apis"].add(("ANY", m.group(1), loc))
    for m in JAVA_FEIGN.finditer(text):
        out["calls"].add((m.group(1), loc))
    for m in JAVA_KAFKA_LISTENER.finditer(text):
        for t in QUOTED.findall(m.group(1)) or [m.group(1).strip()]:
            out["consume"].add((t, loc))
    listener_spans = []
    for m in JAVA_ROCKET_LISTENER.finditer(text):
        out["consume"].add((m.group(1), loc))
        listener_spans.append(m.span())
    for m in JAVA_RABBIT_LISTENER.finditer(text):
        for ref in [r.strip() for r in m.group(1).strip("{} \n").split(",") if r.strip()]:
            if ref.startswith("#{") or ref.startswith("\"#{"):
                out["consume"].add((f"<dynamic:{ref.strip(chr(34))}>", loc))
                continue
            name = ref.strip("\"") if ref.startswith("\"") else consts.get(ref, consts.get(ref.split(".")[-1], ref))
            out["consume"].add((name, loc))
    for m in TOPIC_LITERAL.finditer(text):
        if m.group(1).startswith("GID_") or any(a <= m.start() < b for a, b in listener_spans):
            continue
        out["produce"].add((m.group(1), loc))
    for m in JAVA_SEND.finditer(text):
        out["produce"].add((m.group(1), loc))
    for m in JAVA_TABLE.finditer(text):
        out["tables"].add((m.group(1), loc))
    scan_sql_tables(text, loc, out)


def go_func_prefixes(repo: Path) -> dict[str, str]:
    prefixes = {}
    for p in iter_files(repo, (".go",)):
        for fname, prefix in GO_GROUP_ARG.findall(strip_c_comments(read(p))):
            prefixes[fname.split(".")[-1]] = prefix
    return prefixes


def scan_go(text: str, loc: str, out: dict, func_prefixes: dict[str, str]) -> None:
    text = strip_c_comments(text)
    var_prefix: dict[str, str] = {}
    for fname, param in GO_FUNC_GROUP_PARAM.findall(text):
        var_prefix[param] = func_prefixes.get(fname, "")
    for var, parent, prefix in GO_GROUP_VAR.findall(text):
        var_prefix[var] = join_path(var_prefix.get(parent, ""), prefix)
    for var, method, path in GO_ROUTE.findall(text):
        full = join_path(var_prefix.get(var, ""), path) if path != "/" else (var_prefix.get(var) or "/")
        out["apis"].add(("ANY" if method == "Any" else method, gin_to_openapi(full), loc))


def scan_python(text: str, loc: str, out: dict) -> None:
    prefixes = dict(PY_BLUEPRINT.findall(text))
    for var, method, path in PY_ROUTE.findall(text):
        out["apis"].add((method.upper(), join_path(prefixes.get(var, ""), path), loc))
    for m in PY_FLASK_ROUTE.finditer(text):
        var, path, methods = m.groups()
        for method in re.findall(r"[\"'](\w+)[\"']", methods or "") or ["GET"]:
            out["apis"].add((method.upper(), join_path(prefixes.get(var, ""), path), loc))
    for m in PY_TABLENAME.finditer(text):
        out["tables"].add((m.group(1), loc))
    scan_sql_tables(text, loc, out)


def scan(repo: Path) -> dict:
    out = {k: set() for k in ("apis", "calls", "consume", "produce", "tables", "configured")}
    consts = java_constants(repo)
    func_prefixes = go_func_prefixes(repo)
    for p in iter_files(repo, CODE_SUFFIXES):
        text = read(p)
        loc = p.relative_to(repo).as_posix()
        if p.suffix == ".py":
            scan_python(text, loc, out)
        elif p.suffix == ".go":
            scan_go(text, loc, out, func_prefixes)
        else:
            scan_java(text, loc, out, consts)
    for p in iter_files(repo, CONFIG_SUFFIXES):
        loc = p.relative_to(repo).as_posix()
        for key, topic in CONFIG_TOPIC.findall(read(p)):
            out["configured"].add((topic, key, loc))
    return out


def load_topics() -> tuple[dict[str, str], list[tuple[re.Pattern, str]]]:
    data = load_yaml(ROOT / "06-data" / "mq" / "topics.yaml") or {}
    names, patterns = {}, []
    for t in data.get("topics", []):
        if t.get("name"):
            names[t["name"]] = t.get("event")
        if t.get("pattern"):
            patterns.append((re.compile(t["pattern"]), t.get("event")))
    return names, patterns


def resolve_event(topic: str, names: dict[str, str], patterns: list[tuple[re.Pattern, str]]) -> str | None:
    if topic in names:
        return names[topic]
    for rx, event in patterns:
        if rx.fullmatch(topic):
            return event
    return None


def build_index(service: str, repo: Path, found: dict) -> dict:
    names, patterns = load_topics()

    def topics(key):
        return [{"topic": t, "event": resolve_event(t, names, patterns), "at": loc} for t, loc in sorted(found[key])]

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
            "source_repo": rel(repo) if repo.is_relative_to(ROOT) else repo.name,
        },
        "service": service,
        "apis": [{"method": m, "path": p, "at": loc} for m, p, loc in sorted(found["apis"])],
        "outbound_calls": [{"target": t, "at": loc} for t, loc in sorted(found["calls"])],
        "mq": {
            "consume": topics("consume"),
            "produce": topics("produce"),
            "configured": [{"topic": t, "key": k, "event": resolve_event(t, names, patterns), "at": loc}
                           for t, k, loc in sorted(found["configured"])],
        },
        "tables": [{"name": t, "at": loc} for t, loc in sorted(found["tables"])],
    }


def short_table(name: str) -> str:
    return str(name).split(".")[-1].lower()


def diff(service: str, index: dict) -> list[str]:
    path = ROOT / "05-code" / "services" / f"{service}.yaml"
    if not path.exists():
        return [f"DRIFT {service}: 知识库中没有 {rel(path)}，请用模板 99-agent/templates/service.yaml 新建"]
    doc = load_yaml(path) or {}
    out = []

    apis = doc.get("apis") or {}
    doc_apis = {(a.get("method"), a.get("path")) for a in as_list(apis.get("provides")) + as_list(apis.get("inactive"))}
    code_apis = {(a["method"], a["path"]) for a in index["apis"]}
    for m, p in sorted(code_apis - doc_apis):
        out.append(f"DRIFT api: 代码中有、知识库中没有 {m} {p}")
    for m, p in sorted(doc_apis - code_apis):
        if "TODO" not in str(p):
            out.append(f"DRIFT api: 知识库中有、代码中没找到 {m} {p}")

    events = doc.get("events") or {}
    inactive = set(as_list(events.get("inactive")))
    configured = {t["event"] for t in index["mq"]["configured"] if t["event"]}
    for t in index["mq"]["configured"]:
        if not t["event"]:
            out.append(f"DRIFT mq.configured: Topic {t['topic']} 未在 06-data/mq/topics.yaml 登记（{t['at']} {t['key']}）")
    for direction in ("consume", "produce"):
        code_events = set()
        for t in index["mq"][direction]:
            if t["event"]:
                code_events.add(t["event"])
            else:
                out.append(f"DRIFT mq.{direction}: Topic {t['topic']} 未在 06-data/mq/topics.yaml 登记（{t['at']}）")
        doc_events = set(as_list(events.get(direction)))
        for e in sorted(code_events - doc_events - inactive):
            out.append(f"DRIFT events.{direction}: 代码中有、知识库中没有 {e}")
        for e in sorted(doc_events - code_events - configured):
            out.append(f"DRIFT events.{direction}: 知识库中有、代码中没扫描到 {e}（可能是动态 Topic，需人工确认）")
    declared = set(as_list(events.get("consume"))) | set(as_list(events.get("produce"))) | inactive
    for e in sorted(configured - declared):
        out.append(f"DRIFT events: 配置文件中声明了 {e} 的 Topic，但 events.consume / produce 中没有")

    doc_deps = set(as_list((doc.get("dependencies") or {}).get("services")))
    for c in index["outbound_calls"]:
        if c["target"] not in doc_deps:
            out.append(f"DRIFT dependencies: 代码调用了 {c['target']}，但未列入 dependencies.services（{c['at']}）")

    doc_tables = {short_table(t) for t in as_list(doc.get("tables")) + as_list(doc.get("tables_inactive")) + as_list(doc.get("tables_other"))
                  if "TODO" not in str(t)}
    code_tables = {short_table(t["name"]) for t in index["tables"]}
    for t in sorted(code_tables - doc_tables):
        out.append(f"DRIFT tables: 代码中有、知识库中没有 {t}")
    return out


def as_list(v) -> list:
    if v is None:
        return []
    return v if isinstance(v, list) else [v]


def run_one(service: str, repo: Path, write: bool, quiet: bool) -> int:
    index = build_index(service, repo, scan(repo))
    if write:
        target = ROOT / "05-code" / "index" / f"{service}.yaml"
        target.parent.mkdir(parents=True, exist_ok=True)
        text = yaml.safe_dump(index, allow_unicode=True, sort_keys=False)
        target.write_text("# 由 tools/gen_code_index.py 自动生成，请勿手改。\n" + text, encoding="utf-8")
        print(f"已写入 {rel(target)}")
    elif not quiet:
        print(yaml.safe_dump(index, allow_unicode=True, sort_keys=False))
    drifts = diff(service, index)
    for d in drifts:
        print(f"[{service}] {d}")
    mq = index["mq"]
    print(f"[{service}] {len(index['apis'])} 个接口，{len(mq['consume'])} 个消费，{len(mq['produce'])} 个生产，"
          f"{len(mq['configured'])} 个配置 Topic，{len(index['tables'])} 处表引用，{len(drifts)} 处差异\n")
    return len(drifts)


def main() -> int:
    ensure_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", type=Path, help="业务代码仓库路径")
    ap.add_argument("--service", help="服务名（knowledge.yaml services 中的名字）")
    ap.add_argument("--all", action="store_true", help="扫描 05-code/repositories.yaml 中 local_path 存在的全部仓库")
    ap.add_argument("--write", action="store_true", help="写入 05-code/index/<service>.yaml")
    ap.add_argument("--fail-on-drift", action="store_true", help="存在 DRIFT 时退出码为 1（用于业务仓库 CI）")
    args = ap.parse_args()

    jobs: list[tuple[str, Path]] = []
    if args.all:
        for r in (load_yaml(ROOT / "05-code" / "repositories.yaml") or {}).get("repositories", []):
            repo = (ROOT / r.get("local_path", "")).resolve()
            if not repo.is_dir():
                print(f"跳过 {r.get('id')}：本地目录不存在 {r.get('local_path')}")
                continue
            for svc in as_list(r.get("services")):
                jobs.append((svc, repo))
    elif args.repo and args.service:
        repo = args.repo.resolve()
        if not repo.is_dir():
            print(f"仓库不存在：{repo}", file=sys.stderr)
            return 2
        jobs.append((args.service, repo))
    else:
        ap.error("需要 --repo 和 --service，或使用 --all")

    total = sum(run_one(svc, repo, args.write, quiet=args.all) for svc, repo in jobs)
    return 1 if total and args.fail_on_drift else 0


if __name__ == "__main__":
    raise SystemExit(main())
