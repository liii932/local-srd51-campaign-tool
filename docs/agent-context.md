# Agent 项目上下文

本文为新 Agent 会话提供精简项目地图。它只负责定位，不替代架构、冻结规则、迁移、数据库、
测试或部署文档。动态 Issue/PR 状态、历史测试计数和旧会话结论不在本文维护；每次任务必须从
当前 Git、代码和经授权的外部证据重新确认。

## 产品与范围

- 产品是 loopback-only、单 DM 的本机 Web 工具；唯一支持入口为 `http://127.0.0.1:8080`。
- 技术栈为 Java 21、Tomcat 10.1、Servlet/JSP、原生 JavaScript、MySQL 8、JDBC/JNDI、Gson 和 Maven WAR。
- 目标是完整覆盖 D&D 5e 2014 / SRD 5.1 的公开范围。
- LAN/公网、HTTPS、玩家账号、`/display`、`/api/public/*`、公开投影、WebSocket/SSE、运行时脚本、模组上传和动态 DDL 均不在当前范围。

## 发布与开发状态

| 身份 | release | canonical | archive | 状态与用途 |
|---|---:|---:|---:|---|
| `dnd5e2014_srd51_se_v1` | 1 | 1 | 1 | `RELEASED`；冻结简化版；当前新战役默认 |
| `dnd5e2014_srd51_se` | 1 | 2 | 2 | `DRAFT`；完整规则族的开发身份；不可绑定战役或启用存档 |

- V001—V018 是不可修改的迁移历史；仓库中的迁移不证明任何数据库已经执行。
- V011—V016 建立角色目录、一级创建、升级/生命骰、职业特性生命周期、多职业/ASI/专长及初始熟练基线的 DRAFT 基础；V017 为 format 2 当前状态恢复增加显式来源与事件闭包约束；V018 固定十二职业的多职业施法贡献并提供共享法术位上限计算基础。法术权威状态/升级事务集成和 Grappler 战斗效果仍阻断。
- DRAFT 代码、目录、schema 或测试存在不代表功能已经发布、部署或可由正式业务入口执行。
- 完整规则族在角色、装备/冒险、战斗/状态、法术、怪物/魔法物品完成跨领域整合前保持 DRAFT。
- archive format 2 可以在开发中逐步实现和测试，但只在跨领域发布候选验收后激活和冻结。

## 兼容策略

- 始终保护已发布 v1、既有迁移文件、真实持久化身份、摘要域和旧战役解释。
- 迁移文件不可变不等于 DRAFT 业务语义已发布；DRAFT 可通过新的前向迁移及协调的代码、测试和文档变更继续演进。
- 不为已废弃的 DRAFT 行为默认增加兼容层。只有验收标准明确指出必须保留的真实数据或已发布合同才触发兼容工作。
- 不在单个领域或少量 Issue 完成后切换默认发布版；最终发布、摘要固定、archive 2 激活和默认切换属于同一跨领域发布门。

## 架构与安全不变量

- Servlet/API 只做有界解析；Service 负责业务验证和事务；Repository 负责持久化；module 层负责冻结目录和规范编码。
- 客户端不可信。骰点、候选集合、派生值、算法、规范身份、版本推进和审计结果由服务器决定。
- 完整请求、冻结目录、目标集合和乐观版本必须在消耗随机性或写审计记录前验证。
- Unicode 长度按 code point 计算；需要时规范化 NFC；拒绝 C0/C1 控制字符。
- 权威状态、根事件、快照、效果、字段/资源变化、版本和幂等结果共同提交或回滚。
- 调用方拥有事务的 Repository 不得 commit 或 rollback。
- 只允许 `/host/*`、`/api/host/*` 和安全的 `/health`；产品不信任 `X-Forwarded-*`。
- 数据库自动化固定分工：本机 `dnd_tool_se`/`dnd_tool_se_app` 用于部署运行，`dnd_tool_se_agent` 只读核验，`dnd_tool_se_it` 用于临时表集成测试；V001—V018 只在物理隔离的 disposable MySQL 中以原名 `dnd_tool_se` 原样重放并由隔离只读账号验收；正式 migrator 只在单独批准的停服检查点使用。
- 当前少量测试数据的 `dnd_tool_se` 可作为开发部署运行库，但不得成为集成测试或迁移重放目标；使用前仍需盘点、备份和可回滚边界。

## 权威资料

按任务读取相关部分，不必每次完整加载全部文档：

- 产品、组件、信任和事务边界：[`architecture.md`](architecture.md)
- 冻结 v1 稳定合同：[`rules/srd-5.1.md`](rules/srd-5.1.md)
- 完整规则目标与发布门：[`rules/srd-5.1-complete.md`](rules/srd-5.1-complete.md)
- 数据库、账号与自动化检查点：[`database.md`](database.md) 和 `src/main/resources/db/migration/`
- 静态规则目录与运行状态分离目标：[`rule-database-separation.md`](rule-database-separation.md)
- 测试与 WAR 审计：[`testing.md`](testing.md)
- 部署与回滚：[`deployment.md`](deployment.md)
- 当前角色 DRAFT 合同：`docs/rules/*-v2.md` 与 [`rules/archive-format-2-character-state.md`](rules/archive-format-2-character-state.md)，包括 [`rules/multiclass-spell-slots-v2.md`](rules/multiclass-spell-slots-v2.md)

## 本机命令环境

- 先读取 `use-local-tool-paths` 注册表；Agent 位于 Linux/WSL 时优先直接使用已验证的原生 Linux Git、Maven、Java、Python、Node.js 和 ripgrep。
- Linux/WSL 中使用 Bash 与挂载后的仓库路径，不通过 `wsl.exe` 或 Windows PowerShell 包装原生工具。
- 只有 Windows 管理、明确指定 Windows 实现或必须与 Windows Tomcat/MySQL/脚本互操作时，才使用 PowerShell 7 和 Windows 工具路径，并在该边界内使用 PowerShell 语法。
- 单个任务保持同一 Git 实现；`.gitattributes` 负责跨平台行尾合同，不为切换工具批量改写工作树。

## 新任务最小恢复流程

1. 完整阅读根目录 `AGENTS.md` 和本文。
2. 按上述规则选择已验证的原生工具；Linux/WSL Agent 默认直接使用 Linux 工具。
3. 检查分支/HEAD/上游、staged、unstaged、untracked、实际 diff 和 remotes；所有既有修改均视为用户所有。
4. 取得目标 Issue 或用户给出的验收标准；未经授权不访问或修改 GitHub。
5. 只读取与任务最接近的生产代码、测试、迁移和权威文档章节。
6. 实施前报告当前差距、最小修改范围、受保护合同、验证方案及不会执行的外部操作。
7. 测试优先完成一个最小领域切片；先定向测试，再按变更类型执行完整验证和 WAR 审计。
8. stage、commit、fetch、push、PR、merge、数据库、部署和浏览器写入均按 `AGENTS.md` 的独立授权边界处理。

本机忽略目录 `.pi/harness/domain-json-rules/` 保存分域 JSON 规则复刻 harness；它不是项目级命令模板，也不进入 Git 或 WAR。
