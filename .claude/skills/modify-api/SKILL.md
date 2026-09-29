---
name: modify-api
description: 新增或修改 HTTP / RPC 接口（如 700 号码申请、绑定、质检结果查询）。在接口字段、路径、错误码、语义有任何变化时使用。
roles: [developer]
---

# Modify API

## Workflow

1. 读对应业务域的流程文档，确认接口在业务流程中的位置。
2. 读 `08-development/api-development.md` 和 `06-data/api/overview.md`。
3. 执行 `impact-analysis`，列出所有调用方。
4. 先改契约（OpenAPI 或 `06-data/api/` 下的文档），再改代码。
5. 实现：参数校验、幂等、错误码、日志脱敏。
6. 测试：单元测试 + 契约测试；兼容性变更需要旧调用方式的回归用例。
7. 更新 `05-code/services/<service>.yaml` 的 `apis.provides`，运行 `python tools/kb_lint.py`。

## Checklist

- [ ] 只新增可选字段，或已新开版本
- [ ] 所有调用方已确认 / 通知
- [ ] 错误码使用统一错误码表
- [ ] 写接口支持幂等
- [ ] 日志中号码已脱敏
- [ ] 知识库契约已同步
