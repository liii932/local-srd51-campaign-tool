---
name: dnd-project-start
description: Use first when starting any task, resuming work, or recovering context in local-srd51-campaign-tool (Local 5E Campaign Tool). Route Chinese or English requests to the smallest relevant code, tests, and authoritative sections without loading the whole repository. Not for unrelated projects.
---

# 精准恢复项目上下文

本项目是 Java 21 / Maven WAR / Tomcat / MySQL 的单 DM 本机工具。本 skill 只负责定位，不保存进度或替代项目合同。相对链接以本 skill 所在目录为基准；命令在仓库根目录执行。

## 1. 恢复最小证据

- 遵守根目录 [AGENTS.md](../../../AGENTS.md)。完整、当前的内容若已注入就复用，不再次读取；压缩摘要不算完整内容，文件变动后需重读。
- 协作遵循根 [Agent collaboration](../../../AGENTS.md#agent-collaboration)：主Agent规划、按实际依赖和写入边界划分任务并交子Agent执行，数量与串并行按需决定；子Agent明确使用 `gpt-6.1-sol` / `xhigh`。作者自检后由主Agent整合并亲审实际差量、合同与证据，发现缺陷退回修正；额外非作者交叉审不是主审前置门。
- 使用 `use-local-tool-paths` 选择工具；保持同一 Git 实现。不要重新扫描机器找工具，也不要默认读取被忽略的 `.pi/` 本地 harness。
- 先取 Git 清单，不输出整个仓库的补丁、不联网：

```bash
git status --short --branch
git rev-parse --short HEAD
git diff --cached --name-status
git diff --name-status
git ls-files --others --exclude-standard
git remote -v
```

- 所有已有修改均受保护。对任务涉及的文件分别看 `git diff --cached -- <path>` 和 `git diff -- <path>`；相关 untracked 文件先读取再决定是否编辑。大补丁按文件/区段检查，不以截断输出当作审查完成。
- 需要排查 GitHub 连接时，在本地清单之后查看 `use-local-tool-paths` 的 SSH 传输说明。区分 Git 配置的 SSH 与裸 `ssh` 的客户端/agent；用当前仓库实际 Git 只读访问核验，不因裸 `ssh` 失败就重载密钥或切换协议。本机路径、账号和认证配置只维护在个人工具登记表。

## 2. 按任务加载，不按目录遍历

1. 从 [任务地图](../../../docs/agent-context.md) 按需取路由：先用 `rg -n '<领域关键词或类型名>' docs/agent-context.md` 查命中行，不默认读全文；没有命中才读路由表。英文需求可映射为表中领域名。只选最相关的一行，跨领域才加相邻行；目标不明先问目标。
2. 第一轮定位通常只需：一个相关合同的章节、一个生产入口、一个最近的测试。先从地图给出的路径/类型名搜索；找不到再扩大范围，不能把地图当实现或发布证据。
3. 长文先用 `rg -n '^#{1,3} ' <doc>` 定位标题，再用 `read` 的 `offset`/`limit` 读取完整相关章节（每批约 100—200 行）。源码同样先查符号；涉及校验顺序、事务或调用方时继续读到边界完整，不为省上下文跳过依赖。
4. Java 路径先用 `rg --files src/main/java/com/dndtool src/test/java/com/dndtool -g '*<Type>*'` 定位；随后只在选定文件中查符号。测试不是总与入口同名，按地图给出的测试族补查。
5. 若涉及持久化，再找对应 Repository、迁移和 `docs/database.md`；若只是文档/skill，不读取无关业务实现。不要预读全部规则、全部迁移、部署文档、旧会话、日志或测试报告。

停止定位的条件：能指出修改入口、相关测试、受保护合同和剩余未知。实施前用几行说明目标、范围、验证与授权边界，然后开始工作；不要把已读文件重新长篇复述。

## 3. 验证与维护

- 验证方法按 [docs/testing.md](../../../docs/testing.md) 选择：默认仅运行本次行为及调用方相关测试；纯文档/skill 不运行 Maven，纯编译使用 `mvn compile`。仅在用户要求、当前发布检查点明确要求或有证据表明定向覆盖不足时运行全量，`clean` 单独判断；保留必要的 WAR 审计。旧报告不能证明本次通过。
- 正常本地 harness 状态、规划、校验和测试无需额外审批，平台执行权限仍按实际配置生效。审核实际 diff 和内容；未明确要求时不为文档或常规编辑生成基线 hash、不反复全量 hash 校验。既定 canonical/persisted digest、安全信任边界及构建发布 WAR hash 检查沿用根规则。
- Git/GitHub、数据库、部署与浏览器写入沿用 `AGENTS.md` 的独立授权边界；加载 skill 不授权任何外部操作。
- 代码入口重命名或合同变更时，同步修正地图并核对路径。地图只维护稳定入口，不复制规则正文，不记录分支进度、Issue 状态、测试计数或“已部署”结论。
