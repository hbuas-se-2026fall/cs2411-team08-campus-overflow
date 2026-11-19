# 校园智能问答平台 · CampusOverflow

在原 Java/Spring Boot 与 React 框架上继续实现的前后端分离平台，包含课程问答、独立声誉与积分账本、悬赏、教师资料与审核、AI 课程参考、困惑点和周报。原教学框架说明保存在 [docs/原始框架说明.md](docs/原始框架说明.md)。

最新核心安全实现、自动验证和上线边界见[安全加固实施与验证](docs/安全加固实施与验证.md)。新课程默认私有且禁止模型外发，助教按能力授权，管理员按对象申请限时治理；本轮Java170项、Python43项、Edge21条通过，生产容器/TLS和真实课程效果尚未验收。

## 本机启动（Windows）

需要 Java 21+、Maven、Node.js、Python 3.12+，以及本机 MySQL 8、Redis。运行配置在 `deploy/.env`；首次部署参考 `.env.example`。该文件已被忽略，不要提交或截图分享。

MySQL 需要存在 `campus_overflow` 数据库，字符集使用 utf8mb4。应用启动时由 Flyway 自动迁移，不会清空数据库。仅在数据库不存在时自行执行：

```sql
CREATE DATABASE campus_overflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

在仓库根目录执行：

```powershell
# 安装和编译（首次运行）
python -m venv ai-service/.venv
ai-service/.venv/Scripts/python.exe -m pip install -r ai-service/requirements.lock.txt
Push-Location frontend
npm.cmd ci
npm.cmd run build
Pop-Location
Push-Location backend
mvn "-Dmaven.repo.local=E:/campus-overflow-student/.m2/repository" package
Pop-Location

# 启动本项目服务；不会重复启动已经监听的服务
powershell -ExecutionPolicy Bypass -File deploy/local.ps1 start
```

前端：<http://127.0.0.1:5173>；后端：<http://localhost:8080>；开发接口文档：<http://localhost:8080/swagger-ui.html>。

`local.ps1` 自动读取 `.env`，在后台启动服务，并把日志写到 `deploy/*-local.log`。Qdrant 使用官方 Windows 发布包，首次下载到忽略的 `deploy/runtime/`；Redis 使用 PATH 中已有的 `redis-server.exe`。MySQL 作为现有系统服务独立运行。脚本的状态与停止操作：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/local.ps1 status
powershell -ExecutionPolicy Bypass -File deploy/local.ps1 stop -Service backend
powershell -ExecutionPolicy Bypass -File deploy/local.ps1 start -Service backend
```

停止操作仅处理该脚本记录且路径匹配的进程，不关闭其他项目的服务。代码改动后需重新编译并重启相应服务；前端开发服务支持热更新。换目录后请同步修改上述 Maven 缓存路径，也可使用自己的默认 Maven 仓库。

## 演示账号

本地脚本启用 `local,demo`，首次运行会初始化以下账号和一门课程。演示密码统一为 `Campus2026`。

| 身份 | 账号 |
| --- | --- |
| 管理员 | admin01 |
| 教师 | T2001 |
| 助教 | TA3001 |
| 学生 | 2023001、2023002、2023003 |

正式环境应去掉 `demo`，替换密钥、账号和数据库凭据，并配置 HTTPS；不要直接公开本地开发脚本启动的服务。

## 模型与检索

Java 负责授权、持久化任务和业务状态；Python 仅处理已授权证据、解析、召回和生成。模型配置说明见 [ai-service/README.md](ai-service/README.md)。当前本地配置使用提供的 Intern AI 接口和聊天模型，以及硅基流动 `BAAI/bge-m3`；Key 只保存在 `.env`。

发布资料后才可用于学生参考。新版本发布会替换旧有效版本；停用或修改证据会使旧结果标记过期。AI 发布的正式回答保留 `AI_ASSISTED`，不参与人工答案投票、采纳奖励和认证奖励；教师审核不会改掉来源标记。

声誉用于认可贡献；积分用于悬赏，二者独立。认证奖励增加积分；采纳奖励增加声誉；教师悬赏从管理员分配的课程额度冻结。撤销操作按原实际奖励冲正，不按后来修改的规则重算；已结算悬赏不会因重复采纳再次发放。

## 验证

```powershell
Push-Location backend
mvn "-Dmaven.repo.local=E:/campus-overflow-student/.m2/repository" test
Pop-Location
ai-service/.venv/Scripts/python.exe -m unittest discover -s ai-service -v
Push-Location frontend
npm.cmd run typecheck
npm.cmd run build
npx.cmd playwright test
Pop-Location

# 依赖已启动的真实服务与 demo 账号，创建独立测试课程和账号
ai-service/.venv/Scripts/python.exe scripts/smoke.py
# 本地后台提醒/任务生命周期，不调用供应商
ai-service/.venv/Scripts/python.exe scripts/lifecycle_smoke.py
# 补齐功能、真实资料索引（少量embedding，无聊天生成）
ai-service/.venv/Scripts/python.exe scripts/completion_smoke.py
# 使用上一命令创建的独立样本，演练租约恢复与事件重放
ai-service/.venv/Scripts/python.exe scripts/recovery_smoke.py
# 举报额度、规则版本和资料发布的并发竞争
ai-service/.venv/Scripts/python.exe scripts/concurrency_smoke.py
# 独立新数据库与临时Redis上的完整迁移和业务回归
ai-service/.venv/Scripts/python.exe scripts/clean_deploy_smoke.py
# 语义去重、答案排序、缺口统计的真实联调（少量 embedding，不调用聊天模型）
ai-service/.venv/Scripts/python.exe scripts/semantic_smoke.py
# 仅在要验证真实归纳时，加 --with-summary，允许一次教师触发的聊天调用
# 调用实际模型和 embedding，可能产生供应商用量
ai-service/.venv/Scripts/python.exe scripts/ai_smoke.py
```

浏览器测试在 Windows 默认使用本机 Edge；Linux CI 应先执行 `npx playwright install --with-deps chromium`。`mvn verify` 中的 Testcontainers 集成测试需要 Docker，本机没有 Docker 时可用上述 HTTP 回归验证真实 MySQL/Redis 链路。

## 部署与文档

本地 Docker 方式使用独立容器环境，和本机服务二选一，避免端口冲突：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d --build
```

- [生产安全部署说明](docs/生产安全部署说明.md)（生产使用独立.env.production及docker-compose.yml）
- [实现与验证记录](docs/开发进度.md)
- [全量完成度核查与剩余任务](docs/全量完成度核查.md)
- [三项智能能力的实现与验收](docs/智能能力实现与验收.md)
- [前端详细开发计划](docs/前端详细开发计划.md) / [后端详细开发计划](docs/后端详细开发计划.md)
- [运行维护与备份](docs/运行维护.md)
- [架构决策](docs/adr/)

实现和限制以开发进度记录为准。课程语义聚类、严格重复候选、可解释规则质量排序和教师触发的困惑点归纳已经接入；向量特征缓存可复用。规则和阈值尚需真实标注评估，扫描 PDF OCR、大规模离线索引和学习排序模型属于后续增强。
