# 后端修改记录（Backend Change Log）

本目录按**开发批次**记录 `backend/` 的每一次修改，用于团队内部追溯「改了什么、为什么改、怎么验证」。
前端改动不在此记录；设计与任务规划见 `.workbuddy/tasks/`（本地，不提交 GitHub）。

## 记录约定

- **一批一文档**：每完成一批后端开发，新增一份 `BXX-中文短名.md`；批次较大时拆为 `BXX-1`、`BXX-2`。
- **文件名用中文短名**：编号前缀（`B01-`、`B04-1-` 等）保证排序稳定，短名用中文概括本批主题。
- **先方案、后代码**：每批先在 `.workbuddy/tasks/` 出方案，确认后实施；方案里与实际落地有偏差的，在本文档「设计决策」里如实记录。
- **必含章节**：批次信息 / 背景与目标 / 改动清单（按层）/ API 变更 / 数据库变更 / 错误码 / 设计决策 / 验证结果 / 遗留事项。
- **只改 backend**，不动任何既有测试断言；git 操作（add/commit/push/分支）由提交人亲自执行。

## 批次索引

| 文档 | 批次 | 日期 | 主题 | 对应提交 |
| --- | --- | --- | --- | --- |
| [B01-平台运行基础.md](B01-平台运行基础.md) | B01 | 2026-11-04 | 平台运行基础：请求追踪、异常映射、幂等、异步任务、权限缓存、内部认证 | `fa278b5` |
| [B02-账号与个人资料.md](B02-账号与个人资料.md) | B02 | 2026-11-04 | 账号与个人资料：资料双视图、昵称唯一、登录限流 429 | `fa278b5` |
| [B03-课程生命周期与成员权限.md](B03-课程生命周期与成员权限.md) | B03 | 2026-11-04 | 课程生命周期：归档/重开、成员软删除、授权与权限失效 | `fa278b5` |
| [B04-1-问答核心.md](B04-1-问答核心.md) | B04-1 | 2026-11-12 | 问答核心：清零 co-qa 全部 30 处 S2 TODO | `c79fbc1` |
| [B04-2-问题状态机与采纳复核.md](B04-2-问题状态机与采纳复核.md) | B04-2 | 2026-11-19 | 问题状态机、撤销采纳、教师复核、认证历史、回答来源与分页 | `4012abe` |
| [B05-声誉积分与冲正.md](B05-声誉积分与冲正.md) | B05-1 | 2026-11-23 | 清零 S3 全部 71 处 TODO：声誉账本、悬赏结算、徽章、搜索投影、通知基线 | `f7e8c48` |
| [B05-2-撤销冲正与积分分账.md](B05-2-撤销冲正与积分分账.md) | B05-2 | 2026-11-25 | 撤销冲正闭环、`reverses_event_id`、声誉/积分分账、`/users/me/points` 双端点、展示下限 0 | `69643a8` |
| [B06-悬赏与课程预算.md](B06-悬赏与课程预算.md) | B06 | 2026-11-25 | 悬赏资金来源分账、幂等与并发终态、课程预算双端点、问题删除退款、待追回复核 | 待提交 |

> B01–B03 三批合并在同一个提交 `fa278b5`（分支 `feature/user-login`）中；B04-1 单独提交于 `feat/backend-b04-qa-core`（`c79fbc1`）；B04-2 同分支后续提交 `4012abe`。B05-1 提交于 `f7e8c48`、B05-2 提交于 `69643a8`（均在分支 `feat/backend-b05-reputation-ledger`）；B06 同分支继续开发，完成时仍在工作区、尚未提交。

## 全局约束（所有批次共同遵守）

- **架构**：Java 21 + Spring Boot 3.4.5，Maven 多模块模块化单体；DDD 限界上下文 + 六边形分层（interfaces / application / domain / infrastructure / bootstrap）。
- **ArchUnit FF-1~FF-6**：领域层纯 Java 零框架依赖；依赖由外向内；跨上下文只经 `*.api` 发布语言；上下文无环；JPA 实体与领域对象分离（ADR-008）；表名前缀 `id_ / qa_ / rep_ / dis_`。
- **设计原则**：SOLID、包内聚/耦合原则，以及 SCP / KISS / DRY / YAGNI / SoC / POLA / LoD（源自《软件设计原则》上、下篇）。
- **工作方式**：以测试为规格（红→绿→重构），不改已有测试断言。
- **关键 ADR**：事务性发件箱、Redis 服务端会话（非 JWT）、声誉只追加账本 + 对账、MySQL ngram 全文检索、软删除、OpenAPI 契约优先。

## 当前进度

- 已完成：B01–B03（P0 身份/课程平台能力）、B04-1（co-qa 的 S2 全部 30 处 TODO）、B04-2（问答治理增强）、B05-1（S3 全部 71 处 TODO 清零）、B05-2（撤销冲正与积分分账，**B05 整批收口**）、**B06（悬赏与课程预算）**。
- 基线状态：`TODO(S2)` / `TODO(S3)` 在 `backend/` 内已全部清零，`mvn -B test` 可一路跑到 `co-bootstrap`（27/27 模块 SUCCESS，**217 条测试**，含 ArchUnit）。
- 覆盖率门禁：`co-identity-domain` / `co-qa-domain` / `co-reputation-domain` 均设 `domain.coverage.minimum=0.70`；`co-reputation-domain` 实测 **92.54%**。`co-discovery-domain` 尚未设（回落父 pom 的 0），留待后续批次决策。
- 账本形态：`rep_ledger` 单表只追加，用 `ReputationReason.Currency` 区分声誉/积分；`reverses_event_id` 记录冲正关联；FF-8 对账只统计 REPUTATION 币种。课程预算走 `rep_course_budget` + `rep_budget_adjustment`，与个人积分**来源分账不混**；待追回走 `rep_points_recovery`。
- 悬赏：`rep_bounty` 带 `source_type/source_id/command_key/disputed/open_slot`，`uk_bounty_open_question` + `uk_bounty_command` + `tryAward/tryExpire` 条件更新共同保证幂等与并发终态；`BOUNTY_AWARDED` 维持 REPUTATION 币种不变。
- CI 现状：`SPRINT_MODULES` 仍停在 S2（co-shared-kernel + co-identity + co-qa 各子模块）。S3 已可纳入，但 `.github/workflows/ci.yml` 不在 `backend/` 内，需走 PR 由质量负责人推进；推进到 S3 后可按注释删除该变量、恢复全量门禁。
- **FlowIT / 完整装配（Docker 就绪后补验，2026-11-25）**：`mvn -B verify` 已能启动 MySQL 8 + Redis 7 Testcontainers，**10 个 Flyway 迁移全部应用成功**（含 B05-2 / B06 两个新迁移），Spring 上下文完整启动，B06 新增 bean 无装配歧义。但 failsafe 的 7 条 IT 有 **4 条失败，均为既有基线缺陷**：`co-qa` 的 `QuestionJpaEntity.updatedAt` 为 null 撞上 `qa_question.updated_at NOT NULL`（FlowIT 两个用例 409）、`co-bootstrap` 的 `PlatformFoundationsIT` 两个用例未开事务却调 `MANDATORY` 方法。详见 B06 文档第 8/9 节；修复需另开批次并获授权。
- 后续：B07（搜索/通知/排行榜增强）、B09（AI 回答不发奖励）、B10（教师审核队列，含悬赏 `disputed` 正式复核）、**修复上述 4 个 IT 既有缺陷**与 T4/T5 新需求。
