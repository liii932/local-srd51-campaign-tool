# 静态规则材料与运行数据库分离

当前显式 SQLite 路线将准确冻结规则、业务状态、全历史和本地收据放在一次打开的
私有工作库。来源 MySQL 与原运行 MySQL 继续有独立维护/保管职责，不进入新路线业务
事务。未设置 `DND_STORAGE_ROOT` 时仍使用原 MySQL 简化模式，默认尚未切换；当前有限
能力与本次验收见[领域接合表](design/v2/domain-integration.md)。

## 当前权威与准确来源

| 边界 | 当前责任 |
|---|---|
| 四个应用内置 schema-2 原包 | `rule-packages/srd51-runtime/` 作者源和 WAR `storage/rules/` 确定性 ZIP；生产 reader/compose 建立本版有限规则材料 |
| 私有 SQLite | 完整 `Material(members,rules,entries,chains)`、严格角色/场景/manual 聚合、独立关系投影、全根子历史/pending 与本地控制，唯一活动权威 |
| 完整 JSON | 准确 material/完整链、初始/当前事实及全历史/未决图；四集合逐项精确比对本版已验证固定投影，不只携规则指纹，不逐次重新 compose/hash |
| 原来源 MySQL | schema-1 分区、schema-2 原包/独立接受及类型化镜像的实际维护/读取合同；当前内置新建/打开/恢复无需连接它 |
| 原运行 MySQL | 未启用 SQLite 的简化模式及独立内部服务；已应用迁移/账号/真实数据保持原处，不自动转换或删除 |
| JVM/页面/缓存 | 只由已读回事实生成执行/查询视图，无权补状态或成为第二写入权威 |

当前关系/正文选择见[规则存储](design/v2/rule-storage-and-versioning.md)和
[数据映射](design/v2/storage-data-map-and-rollout.md)：复杂对象采用封闭 record/enum 的
严格 typed TEXT 聚合，资源/装备/书籍/动作/效果/怪物等关系表同事务核验，不是另一份
独立可写余额。应用 JSON 完整声明所有物理列，作者/上传不能定义 SQL、类或路径。

## 引用、加载与运行

- 规则身份是原所属模组/类型/键，条目保留完整所有者/槽身份，准确版本和原/逻辑摘要
  由组合绑定；完整链不能简化为最后有效值。
- schema-2 prepared Identity 虽名 runId/snapshotId，作为初始/历史/因果的稳定业务键
  进入文件；打开不重新分配。物理 core run、epoch、Host revision 和 HTTP/保存收据
  属于新本地运行，不进文件，也不由 prepared 键获得旧连接或执行权限。
- 新建/文件打开只读取本版内置四包并核输入，完整材料/链写本库；普通命令和保存不
  回源。文件同样携材料，当前来源离线不阻止新建、打开、继续或同版恢复。
- 每个命令在实例/目录独占与 `BEGIN IMMEDIATE` 下，状态/投影/根子历史/版本/接受一次
  提交。没有独立 C0 接纳或 NOT_COMMITTED 裁定；未知阻断，recover 核真实库及全部
  收据/历史后新 epoch，未持久接受意图不自动重试/重播。
- 文件打开建立新物理库，直接写已验证事实，不执行历史、重掷或补授予；残留恢复
  保留原库，只核计划/结构/事实，不补表或 seed。完整 INITIAL 检查点接受后才开放新运行。
- INITIAL/MANUAL/FINAL 用 P0—P3，发布与数据库接受分开；END 不处理游戏 pending，准确
  封存后清理整运行目录。周期 AUTO/玩家步骤是后续功能，不是当前耐久运行前提。

## 原 MySQL 服务与后续自定义来源

原 Host 通过 `jdbc/DndToolSE` 使用 `dnd_tool_se` 时，规则与业务仍按既有外键/目录读取
合同运行；原 `Schema2SourceLedger` / `JdbcSourceSchema2Repository` 完整读取来源接受/
镜像，来源连接结束后才由内部 `Schema2SnapshotTransferService` 向 MySQL 写入有限快照。
这些独立服务不因当前 SQLite 路线改变而省略校验或扩大 DRAFT/完整初始化资格。

未来开放任意自定义包时，须独立交付准确原包保管、本地接受、依赖/组合/机制/发布资格
和来源连接/索引/维护协议。来源 MySQL 可承担其持久账本，类型化索引可重建也不允许在
旧 reader/installer 仍使用时删改不变量。未来来源资格不是当前四包运行的未实现前提。

## 迁移、保管与验证

原 MySQL 两条迁移和账号职责继续按[数据库](database.md)执行；保留已应用文件/checksum，
变化用前向迁移。新 SQLite 按当前声明初始化，不重放 MySQL 编号，不为旧存档建立兼容
升级链。真实重建/重置/删除、来源安装接受、应用部署和默认切换分别需要独立授权。

核对当前全部已实现用户能力和旧正式差距，不仅替换 Servlet loader 或 JDBC 驱动。
DM-managed 自由卡/MANUAL 等独立领域不得用来任意修改 schema-2 角色。实际边界和证据见
接合表；类/表存在、MySQL/代理测试或文档修订不能证明 SQLite/文件系统故障已验收。

物理备份保管完整库/journal 或采用引擎一致备份，便携恢复保管 JSON 与支持准确规则的应用。
既有数据不自动迁移/覆盖/删除，不将备份或真实存档放 Git/WAR。配置、打包、备份与部署
分别按[配置](configuration.md)、[测试](testing.md)、[备份恢复](backup-and-restore.md)和
[部署](deployment.md)执行，不另创运维流程。
