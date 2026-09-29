---
# 必填
id: <domain>-<short-name>          # 全局唯一，kebab-case；ADR 用 ADR-NNN
title: <标题>
domain: <knowledge.yaml 中的 domain 键>
kind: stable                        # stable 稳定 | dynamic 常变 | historical 历史
audience: [all]                     # all | architect | developer | ops | qa（可多选）
owner: <负责人或团队>
status: draft                       # draft | active | deprecated
last_verified: YYYY-MM-DD           # 最近一次与代码/线上核对的日期
# 选填
related: []                         # 相关文档 id
source_of_truth: []                 # 事实源：代码路径、配置 key、外部文档链接
---

# <标题>

## 1. 是什么

## 2. 为什么

## 3. 在哪里（代码 / 配置 / 数据）

## 4. 怎么运行

## 5. 约束与坑
