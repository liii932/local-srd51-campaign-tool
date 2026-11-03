# SQLite 当前有限战役：规则、运行与完整文件

本页规定显式 SQLite 模式的当前使用方法、数据责任及同版文件生命周期。
`DND_STORAGE_ROOT` 启用此模式，`/host` 进入 `/host/storage`；未配置时仍使用原 MySQL
简化模式。两者在启动时选择，不双写、不因连接或文件错误自动切换。现有 MySQL 真实
数据、迁移、账号和格式 1 存档须独立保管，不能把启用 SQLite 当作数据迁移。

当前应用模型沿用 `applicationModel=srd51-vitality-1`、`storageModel=sqlite-vitality-1`，
可选择两个明确的运行范围：生命值记录，或在其上选择有限 runtime 能力。名称不表示
完整 SRD `RELEASED`、通用 archive format 2 或跨版本兼容。准确逐领域覆盖和剩余差异见
[领域接合表](design/v2/domain-integration.md)。

## 选择规则与开始使用

1. 在 `http://127.0.0.1:8080/host/storage` 创建战役。只选择基础
   `srd51-vitality@1` 时可记录角色 HP；加入 `srd51-healing@1` 后可定额治疗。
   选择 `srd51-runtime@1` 必须同时选择前两者，顺序准确固定，缺依赖、未知或重复成员拒绝。
2. 生命值记录范围可创建最大 HP `12`、当前 HP `4` 的角色，治疗 `5` 后为 `9/12`；
   这是明确的 HP 记录，不冒充完整职业角色。runtime 使用下面两种独立角色卡，不混入
   基础生命值记录模型。
3. runtime 的规则卡可创建 fighter、Life-domain cleric、Wizard PC/NPC，查看初始/当前卡，
   使用角色、装备、检定、健康、成长、资源、休息及场景动作；HP 和伤害/死亡状态由真实
   规则拥有。规则卡的初始队伍须在时间零点建立，推进时间后不补入另一条未同步时钟；
   这一创建时间限制只适用于规则卡。法师完成短休时，可在成长与休息表单选择已消耗的
   法术位进行奥术回想；全部为零可放弃。后端核验法术书、等级预算、位数缺口与战役日，
   页面显示实际恢复量和使用日，保存与重开保留这些事实。
4. runtime 同时提供独立 DM 自由卡，可从空白或 NPC 模板创建，编辑基础字段、职业等级、
   技能/豁免熟练及简单物品，执行 MANUAL、属性、技能或 SAVING_THROW 检定和有序分支
   后果。自由卡保留自己的初始/当前卡和全部根历史，不转换成规则卡；HP 为零仍是记录值。
   简易遭遇可混合两种卡，管理名单/阵营、实体位置和独立队伍节点，没有先攻或回合；
   规则战斗仍使用规则卡的完整战斗消费者。
5. 节点/连接/位置、简单物品、角色名称/类型/活动状态和消息使用同一战役根历史。
   活动场景、反应窗口或 pending 的操作由原 owner 继续，不通过独立角色操作绕过。
6. 保存生成不可变完整 JSON；继续游戏不会修改已有保存点。结束先接受最新 FINAL，
   再封存并清理原运行。打开保存文件建立新运行，保留原初始事实、消耗、骰子及全部历史。
7. 浏览器刷新只读当前状态。重启发现残留时先显式恢复或精确放弃，不自动新建或用旧档
   覆盖尚未保存的最新提交。上传导入、下载、打开、删除与放弃残留分别针对准确身份。

## 有限 schema-2 规则材料

runtime 的作者源在 `rule-packages/srd51-runtime/`，资源在
`src/main/resources/storage/rules/`。`BundledCampaignRules` 经
`ProductionRuleArtifact.read` 验证每个原始 ZIP，再调用
`ProductionSchema2Projection.compose` 建立生产投影；不从测试 fixture 取得规则，
不伪造来源 MySQL 的安装/接受账本。四包的准确顺序是：

| 包 | 角色与直接依赖 |
|---|---|
| `srd51_runtime@1` | base；语言/工具/熟练/角色来源与休息等基础 |
| `srd51_characters@1` | extension；直接依赖 `srd51_runtime@1` |
| `srd51_magic@1` | extension；直接依赖前两包 |
| `dm_runtime_house_rules@1` | extension；直接依赖 `srd51_runtime@1` |

原始/逻辑摘要、完整身份、最终规则和每个字段的完整链固定在本次工作库内。读取同时核对
当前应用支持的准确材料；改变成员、版本或规则解释必须新建战役，不在历史上热改规则。
数据来源、许可、确定性构建和完整限制见[包指南](../rule-packages/srd51-runtime/package-guide.md)。

Fighter 支持到职业 6 级、Life-domain cleric 到 1 级、Wizard 到 5 级。当前角色是有限应用
profile：种族属性提升、完整背景技能、起始装备整包与未接入特性不被补造。Fighter 特性和
Evocation Savant/Sculpt Spells 只作 `OPTION` 目录，不自动获得；选择的 Champion/Evocation
身份仍保留。Wizard Spellcasting/Arcane Recovery 与一级 Life profile 才拥有其真实机制。
Grappler 的获得记录不授予战斗效果；任何实际取得且缺少执行 owner 的影响继续失败关闭。

施法只支持 Bless、Cure Wounds、Shield、Counterspell 和 Burning Hands 的有界算法；
其余 Wizard 法术只可作为学习/准备元数据。多施法职业共享槽不得由独立资源拼凑。
Hell Hound Fire Breath 是唯一可执行怪物动作，其他动作明确为 metadata。
DM 抗魔区域及固定直接生命效果是显式家规，不称作 SRD 法术或物品。

## 结构、所有者与事务

结构唯一源是 `src/main/resources/storage/campaign-schema.json`。
`StorageSchemaCatalog` 严格读取应用声明，`StoragePlan` 计算依赖闭包、纳入原因与计划摘要，
编译 DDL/索引/静态种子，并由 `SqliteSchemaVerifier` 核对真实 SQLite。选择规则缺项不
自动补选；结构依赖则完整纳入，包括空角色集合以后可能写入的表。结构不能来自请求、
规则作者包或战役文件。新建/文件打开建立新库，同版残留恢复不补表、迁移或重 seed。

| 所有者 | 当前职责 |
|---|---|
| `HostStorageServlet` 与安全过滤器 | 解析有限命令，验证 loopback、Session/CSRF、来源、epoch、revision、请求 UUID/摘要；不接物理路径或 SQL |
| `CampaignStorageService` | 持接纳门及实例锁，协调初始化、同一 SQLite 业务事务、幂等、保存/结束、打开和恢复 |
| `CampaignDomainEngine` | 无连接的领域执行/预检和历史验证，复用已有纯角色/战斗规则，控制随机性及跨角色时间/后果 |
| `CampaignDomainData` / `CampaignDomainCodec` | 封闭 record 类型、准确规则/字段链、初始计划、当前状态、完整角色/场景/因果历史与未决动作 |
| `CampaignDomainRepository` | 在调用方事务写聚合文档及关系投影，读回逐行独立核对，不自行提交、回滚或修复 |
| `CampaignCodec` / `CampaignDomainValidation` | 核验整场战役、根/子事件归属、历史邻接、当前状态和准确规则，产生完整便携文件 |

领域文档是带封闭 discriminator 的严格类型化聚合，不是任意 JSON 属性袋。未知/缺失字段、
未登记类型、错误标量、坏 Unicode、非法映射顺序或越界集合拒绝。数据库同时保存可核对的
资源、装备、法术书、场景/动作帧、效果、怪物等关系投影；聚合与任一投影不符时拒绝，
不重建一份“看似正确”的索引后继续。

`CampaignCheckEffects` 承接成功/失败两条有序效果分支：健康必须引用支持的健康规则，
简单物品用有界 delta，位置指向本战役节点，消息独立记录。两分支完整预检后才掷骰，
命中的后果及检查原骰同根提交。`CampaignManualData` / `CampaignManualCommands` 接合
独立 DM-managed 自由卡、职业等级/熟练、MANUAL/SAVING_THROW 与简易遭遇名单/阵营；不得把它们
转换成或用于绕过 schema-2 规则角色。具体 UI/测试交付状态按[接合表](design/v2/domain-integration.md)验收。

初始化的 DDL、seed、准确规则、战役事实和控制同一事务提交；独立连接完整读回且首份
检查点接受后才开放游戏命令。每个领域命令先验证完整冻结规则、对象/目标集合、版本、
领域集合/事件计数容量和继续义务，再消费真实随机性；当前状态、原骰、全部根/子历史、跨对象后果、版本及
幂等结果共同提交或回滚。实例根锁、串行接纳门和一个 `BEGIN IMMEDIATE` 覆盖该次
接纳及业务提交，不另设独立 C0 或 NOT_COMMITTED 裁定。commit/rollback 未知时禁止
新写，显式 recover 核验真实库、完整历史和全部接受收据后建立新 epoch；未持久意图
不自动重试或重播。不会调用另一条 MySQL 服务事务保存其中一部分。
分配首先以原子目录创建发布 `.prepare-<runId>_<inputDigest>_<planDigest>_<inputLength>`
归属绑定；此阶段只允许空锁及原输入/分配的 `.part`、`.json` 固定槽，不得存在数据库。
原输入以 runId 为文件身份，完整字节、长度和计划均须匹配目录绑定，分配内容严格核对。
完整文件经 force/读回后以硬链接发布，再将验证完成的目录改名为 runId；已有目标拒绝。
数据库开始标记先于首次打开数据库，之后数据库缺失时拒绝重建，避免把丢失的已提交库
当成未初始化。运行目录改名由实例锁串行化；应用不把持同账号的外部文件篡改视为受支持
并发操作。
完整候选的 16 MiB 文件字节上限在纯结果产生后、任何领域 SQL 写入前核验；超过上限则
拒绝本次结果，不记录其随机事实、不改变原状态或截断历史。领域事件预留不是所有后续
文件体积的预留，不能据此宣称无限历史或最坏体积已通过容量验收。

连接固定 SQLite JDBC `3.50.3.0`、UTF-8、`journal_mode=DELETE`、`synchronous=EXTRA`、
`foreign_keys=ON` 与有界 busy timeout，并读回核验。数据库文本按原始 UTF-8 字节严格
解码。实例锁覆盖整个受管根，业务/保存串行接纳，原始写连接不交给 Servlet 或规则消费者。

## 完整文件与直接恢复

文件为 `fileKind=VITALITY_CAMPAIGN`、`formatVersion=1`，不是旧 MySQL 存档或通用完整
format 2。根字段为 `applicationModel/campaign/characters/fileKey/fileKind/formatVersion/`
`history/rules/integrity`；选择 runtime 时另有必需的 `domains`，并禁止再存另一套基础角色 HP。
业务整数用规范十进制字符串；完整根 canonical 对象键排序、数组保留业务顺序，使用
`DND-VITALITY-FILE` 域和 canonical 1 的 SHA-256 完整性绑定。

runtime 的 `domains` 保留准确成员/最终规则/完整字段链、每个角色加入时的初始计划与标准
数组初始事件、当前状态及所有角色事件、全部场景/战斗历史、节点/位置/简单物品和连续根
因果事件。法术书、资源、装备、健康、原骰、动作流/叶子、反应窗口、效果、怪物和充能
由其类型化状态/历史携带。根历史必须与所有子事件一一归属，不能接受孤儿、重复事件或
只含当前摘要的文件。旧基础 HP 模型仍单独核对其创建/治疗全历史。

文件不含 SQLite DDL、物理结构计划、路径、HTTP Session、CSRF、当前安全 epoch 或本地
请求收据。保留的角色准备身份属于业务事实；打开文件只分配新的本地运行/安全权限，
不把这些身份当作可写数据库连接。读取从完整保留事实验证，不重新掷骰、授予、扣费、
恢复资源或执行历史中的命令。

## 保存、导入、删除与残留

P0 用短控制事务固定保存意图和源 revision；P1 在受管档案目录写临时文件、force、关闭并
读回；P2 通过同目录硬链接原子发布唯一 fileKey，POSIX 平台同时 force 目录，Windows
按[平台落盘合同](configuration.md#sqlite-当前应用存储配置)保留明确的目录元数据限制。
P3 从正式文件独立核验后接受收据，FINAL 与封存在同一事务。硬链接不受支持就失败，
POSIX 目录同步失败也拒绝，不降级为覆盖复制。
文件 I/O 不持数据库事务。未完成 P3 的文件不列为可打开保存点，原工作库保留为恢复源。

上传导入先严格读取完整文件，再保留文件原 fileKey 登记受管文件；已有同 fileKey 仍返回
`FILE_CONFLICT`，不重新分配身份、覆盖或冒称另一次导入成功。若发布后响应中断，同字节
重试可在独立核验正式文件后，清除同 fileKey 且完整字节与身份均相同的暂存硬链接或副本；
未知、部分、异字节或符号链接暂存均保留。上传本身不激活运行。
下载只读取已经核验的准确受管文件，不接受任意路径。删除保存文件先排除活动/残留运行及
收据依赖，核验正式文件后先清除上述可证明同属该完整文件的暂存名，再删除正式文件；
中断不能先失去正式文件而遗留已确认的暂存。删除不结束运行，也不删除规则来源包。
精确放弃只处理明确的非活动 run：取得锁、核对分配/隔离材料与受控白名单后清理，保存点
保留。未知文件、身份不符或锁冲突均阻止清理，不能递归移除未知内容。

END 先重新核对 FINAL 与完整源状态并持久化清理材料；清理中断后只继续准确封存流程，
不能重新开放游戏。正常停机、浏览器关闭或 Session 到期仅释放进程资源，不隐式 END。
恢复原残留核对模型、计划、真实结构、规则、全部事实/历史及保存收据，不重放 DDL、seed
或游戏命令；缺库、部分结构或坏材料保持阻断。不能手删热 journal 或用旧文件覆盖新提交。

| 失败位置 | 恢复责任 |
|---|---|
| 规则/结构/输入预检 | 不分配运行、不写 DDL 或消费骰子 |
| 准备目录、锁或 input 写入中断 | 原输入不完整返回 `PREPARATION_INCOMPLETE`，仅可显式精确放弃；未知文件、超限、完整字节身份不符继续保留 |
| 完整 input、allocation 写入中断 | 核对原目录绑定与原输入，只补完准确分配前缀并继续原初始化；不生成另一次业务输入 |
| 初始化事务失败 | 保留可识别准备材料，完整回滚；部分/未知结构不修补 |
| 提交后首份检查点失败 | 保留原库，只继续独立读验与原检查点 |
| 业务提交/响应不明 | 阻断后续写，持久请求结果及完整事实显式查证，不重掷或重复后果 |
| P2 后/P3 前失败 | 保留已发布文件和原源，只完成同一保存意图 |
| 重启发现残留 | 阻止新建/打开，用户显式选择准确 run 恢复或放弃 |
| 封存清理失败 | 保持封存，仅按持久清理材料继续，未知文件不删除 |

创建、打开、恢复等技术操作的 HTTP 请求号与普通业务幂等结果不是同一协议；丢失响应先
查询当前状态。当前每命令 SQLite 耐久、INITIAL/MANUAL/FINAL 与完整残留恢复承担
保存责任；周期 AUTO、保留/轮换须先测量容量再实现，不作为运行先决条件。平台断电耐久、
各故障窗口和逐域验收只按[领域接合表](design/v2/domain-integration.md)记载的实际证据判断，
不由正常往返推定。

## 本地验证与运行环境

运行采用 Java 21+、外部 Tomcat 10.1、空闲的 `127.0.0.1:8080` 和仓库/WAR 外的本地
私有根。原生 Windows 使用 NTFS/ACL，Linux/WSL 使用 POSIX 私有权限；具体权限、锁、
无覆盖发布与落盘边界见[配置](configuration.md#sqlite-当前应用存储配置)。
Windows 进程中断和锁释放须由原生 Java/SQLite JDBC 验证，不能用 WSL 通过代替。
不能为隔离验收终止无关服务或改动已有 Tomcat。正式 WAR 与测试 classpath 启动的证据
分别记录，可重复命令见[测试指南](testing.md#v2-json-结构与文件存储的替换验收)。

测试仅使用一次性临时根，不连接 MySQL。人工验收先在页面 END，再停止该隔离进程，
保管需要的 JSON，最后仅清理明确生成的测试根。真实部署仍只按[部署指南](deployment.md)
执行停机、完整备份、WAR 审计、可回滚替换和独立授权；代码和测试存在不表示已经部署。
