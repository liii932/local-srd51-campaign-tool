# 静态规则目录与运行数据库分离

本文说明当前存储与双 schema 采用边界。新 v2 的权威目标分别位于
[规则存储与版本](design/v2/rule-storage-and-versioning.md)、[运行与事务](design/v2/runtime-and-transactions.md)
及[采用边界](design/v2/adoption-boundaries.md)，这里不维护另一套启动或快照方案。

## 当前边界

当前 Host 仍通过 `java:comp/env/jdbc/DndToolSE` 使用 `dnd_tool_se`。`module_release`、
`module_*`、`module_catalog_*_v2` 与战役、角色、事件、幂等表共处该 schema，运行表仍有
本地规则外键及业务流程中的目录读取。不能只更换 Servlet loader 就宣称已完成拆分。

独立规则来源的语言与工具熟练目录表、安装事实及读取已见[来源 schema](rule-source-schema.md)、
[离线安装](rules/offline-language-installation.md)和[来源读取](rules/source-language-reading.md)。
来源 V001/V002 保存语言与工具目录；运行 V019 提供[语言快照分区](rules/runtime-language-snapshot.md)，
V020 增加[工具镜像并支持双分区读写](rules/tool-catalog-partition.md)。这些是分区实现，不代表
第二 JNDI、完整准备/执行上下文、战役快照绑定或部署已完成。

## 两侧职责

| 边界 | 目标职责 | 引用与写入 |
|---|---|---|
| `dnd_tool_rules` | 已安装领域规则、发布身份、安装事实和独立迁移账本 | 源内外键；安装/迁移/发布分权；Web 只有准备边界的只读能力 |
| `dnd_tool_se` | 完整不可变运行规则快照、可变工作集、独立档案区、结果和技术身份登记 | 本地复合外键；业务与审计单运行事务，不回查来源库 |
| 应用内存 | 启动准备材料及本次运行实际读回后建立的执行视图 | 内容不可变；执行资格由当前守卫和提交事实控制 |

同一 MySQL 实例中使用两个 schema 和不同职责账号。运行 JNDI 保留 `jdbc/DndToolSE`，
来源只读 JNDI 目标为 `jdbc/DndToolRules`，不能接反、接到同一 schema 或互作 fallback。
凭据和池配置仍在 WAR 外，由[配置](configuration.md)及[部署](deployment.md)程序管理。

跨边界规则身份为 `(module_key, release_version, content_sha256)`，来源自增 ID 不传播到
运行身份或存档。完整规则快照是受控不可变派生物，不是可独立编辑的第二规则来源。

## 外键与物理拆分

- 现有已发布 v1 的本地外键和持久化解释继续受保护；不得改写旧迁移或直接移动旧规则表。
- 新 DRAFT 运行模型使用本地 run/snapshot 与稳定规则键，规则成员关系由完整本地快照
  约束。来源外键留在来源库，不创建跨 schema 外键。
- 领域关系、完整复制、实际读回和内容校验同时设计，不能用仅键清单替代完整快照。
- 旧 DRAFT 三表、profile 和 API 可经协调前向采用替换，不默认保留兼容分支。
- 删除旧表不是首个切片的验收目标；须先证明没有现行合同、真实数据或消费者仍依赖它。

## 加载与采用

新 v2 启动只准备一个受控选择的精确规则；首次初始化前再次全量读取来源，结束来源事务，
再在运行事务中保存完整快照和业务。实际读回且提交确认后才发布本次执行视图。普通命令、
保存和结束不持有来源访问能力，存档不嵌入规则内容。

该目标取代较早“启动加载默认及所有战役引用的多发布目录”设想；它不改现行 v1，也不
表示新初始化已经开放。单规则准备、残留救援和来源故障隔离以[运行设计](design/v2/runtime-and-transactions.md)
为准，旧发布仍按其原有识别和回归合同保留。

## 迁移、验证与回滚

运行与来源迁移各自独立编号、批准和诊断，准确清单见[数据库](database.md)。已应用文件
和账本不可改写，规则 SQL 不进入 WAR。应用启动不建库、不安装内容、不修补账本。

采用前核对新旧写入口共同接纳、全部规则 JOIN/外键/触发器、事务所有权、读取能力和
实际账号权限。验证覆盖精确身份/摘要、缺分区/畸形数据、跨快照串用、提交失败/未知、
只读路径无业务写入以及已发布 v1 回归；真实 SQL/锁/池语义使用隔离环境验收。

测试和 WAR 审计沿[测试指南](testing.md)，实际部署沿[部署指南](deployment.md)。迁移、
授权、内容安装、发布、部署与新战役默认切换分别核验。应用回退须兼容已安装账本和所需
规则，不能删除账本假装可回退。业务文件和数据库灾备的保管范围分别见
[保存与恢复设计](design/v2/save-and-recovery.md)与[现行备份程序](backup-and-restore.md)。
