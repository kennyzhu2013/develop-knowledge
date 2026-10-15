---
id: arch-security
title: 安全与合规
domain: architecture
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-overall, lesson-security-risks]
source_of_truth:
  - vi-assets/qa-server-master/apps/__init__.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/engine/
  - vi-assets/vi-as-master/
---

# 安全与合规

## 1. 敏感数据

| 数据 | 要求 |
| --- | --- |
| 手机号、700 号码 | 话单与 MQ 中为明文。日志和知识库必须脱敏，示例只用 `700xxxx0001`、`138xxxx0001` |
| 录音与转写 | 录音在 PFS；转写经过脱敏模型后仍可能残留在回调与海山。scheduler 回调后删除 Mongo 中的转写全文 |
| 密钥与签名材料 | 配置与 `model_meta.authentication` 中存在。文档不摘录。qa-server 签名不覆盖 body |

## 2. 代码里已经能看到的风险

细节与位置见 `10-lessons/pitfalls/security-risks.md`。

| 项 | 事实 |
| --- | --- |
| qa-server 签名 | `MD5(timestamp + secret)`，5 分钟窗口，不签 body |
| scheduler 管理接口 | `/admin/*` 与 `/v1/front/*` 在代码中无鉴权；流程 XML 使用 SpEL |
| 媒体运维接口 | vi-media `/som/*`、vi-merge `/ops/*`、offline-merge `/ops/*` 与 `/v1/test` 无鉴权。`/v1/test` 会真实发 MQ |
| 配置 | 仓库配置含地址与凭据占位或硬编码。知识库不复制这些值 |
| 样例号码 | `vi-cront-jobs` 的数据检查脚本含未脱敏号码，文档不抄录 |

## 3. 合规边界

质检结果推给客户回调地址。地址必须在 qa-server 的 URL 白名单内。知识库与测试样例不得保存真实录音文本。
