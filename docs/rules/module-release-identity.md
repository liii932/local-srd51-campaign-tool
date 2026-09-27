# 规则发布身份与批准描述符

本合同规定 `BuiltinModuleReleaseRegistry` 的纯值校验和应用独立批准边界。
它不证明规则已安装、源目录完整或可执行。冻结 v1 的规范字节仍由
[SRD 5.1 v1](srd-5.1.md#11-规范哈希要求) 定义；完整规则族的发布门见
[完整规则目标](srd-5.1-complete.md)。

## 1. 精确身份

发布身份是 `(module_key, release_version)` 有序对，两项均按完整原文逐字符比较。

| 字段 | 词法与范围 |
|---|---|
| `module_key` | 1—128 个 ASCII 字符，`[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*` |
| `release_version` | 1—64 个 ASCII 字符，`[A-Za-z0-9][A-Za-z0-9._-]{0,63}` |

不 trim、不执行 NFC、不转换大小写、不按数字或 SemVer 比较、不展开别名或版本范围。
`1`、`01`、`1.0` 是三个合法且不同的版本字符串；`vA` 与 `va` 也不同。
首尾空格、NBSP、控制字符、全角字符、其他非 ASCII 字符和非法 Unicode 均拒绝。
展示文本的规范化规则不能用于修正身份输入；canonical `IDENTIFIER` 不做文本规范化，
既有 `TEXT` 的换行与 NFC 编码规则保持不变。

`Identity` 构造时拒绝非法词法；null 违反非空前置条件。公开查询不将坏输入变成构造异常：

| 查询结果 | 含义 |
|---|---|
| `INVALID_IDENTITY` | 输入为 null，或身份词法/长度不合法 |
| `UNKNOWN_RELEASE` | 身份合法，但应用注册表没有精确条目 |
| `UNPUBLISHED_RELEASE` | 精确条目存在，应用仍将其登记为 DRAFT |
| `READY` | 精确条目为 RELEASED，返回其批准描述符 |

只有 `READY` 携带描述符，其含义限于注册表批准。`find` 对非法或未知身份返回空值，
可以返回 DRAFT 描述符供查看；它不授予执行许可。

## 2. 描述符、格式与固定映射

`Descriptor` 是应用批准值，不是不可信源发布头。构造时验证：

- identity、hash algorithm 和 release status 非 null；
- canonical/archive format 是正 Java `int`，范围为 1—2147483647；
- hash algorithm 精确为 ASCII `SHA-256`；
- RELEASED 的批准摘要必须符合 `[0-9a-f]{64}`；
- DRAFT 的批准摘要必须严格为 null，空串、畸形值和合法摘要都不能替代 null。

null 前置条件抛出 `NullPointerException`，其他非法纯值抛出 `IllegalArgumentException`。
注册表继续拒绝重复身份、null 条目，以及缺失或 DRAFT 的默认身份。

正整数的结构合法性与本体实际支持是两个判定。构造一个格式为 3 的描述符不会注册或
实现格式 3；支持取决于该精确身份的显式映射及实际 encoder、codec 和完整领域算法。
不从 release 数字大小、应用构建版本或“已有某格式编码器”推断发布支持。

标准注册表仅有以下映射：

| module key / release version | canonical | archive | 应用状态 | 默认 |
|---|---:|---:|---|---|
| `dnd5e2014_srd51_se_v1` / `1` | 1 | 1 | RELEASED | 是 |
| `dnd5e2014_srd51_se` / `1` | 2 | 2 | DRAFT | 否 |

v1 的唯一固定批准摘要为
`8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e`。
完整规则族没有批准摘要；登记其开发格式映射不会激活 archive 2 或切换新战役默认。

## 3. 批准摘要与观测摘要

应用批准注册表是独立信任来源。`BuiltinModuleHashManifest.expectedSha256` 仅在精确身份
获应用 RELEASED 批准、canonical format 与 hash algorithm 精确匹配时返回批准摘要。
它不读取源头自报摘要或状态来生成、修改或学习批准值。`ModuleCatalog.Release` 没有
archive format 字段，该格式仍由 registry 提供并由对应文件消费者核对。

因此，来源摘要为空、畸形或合法但不同，以及来源状态为 DRAFT，都不会改变上述独立
expected 查询结果。查询得到 expected 值不等于来源可准入：消费层仍须分别核对源身份、
格式、状态、摘要词法、批准摘要的精确相等，并验证完整目录后重算 canonical 摘要。
合法摘要只说明词法正确；同一身份的摘要相差一个十六进制字符仍是不匹配。来源自报
RELEASED、安装成功或重算出同一个摘要，均不能把未知或应用 DRAFT 条目升格。

摘要的所有者必须区分：

| 边界 | DRAFT 与摘要的关系 |
|---|---|
| 应用批准描述符（B） | DRAFT 严格没有批准摘要，即 null |
| 完整源投影或完整快照材料（P/X） | DRAFT 可以保存完整验证后得到的观测摘要；有摘要不等于已获执行批准，可执行视图仍只允许已验 RELEASED |
| PARTITION 验证材料 | 只证明分区，不产生或伪填完整规则摘要 |

规则内容摘要、作者文件原始摘要、安装清单原始摘要、存档原始摘要和 WAR 原始摘要各有
独立输入域，不能互换。展示元数据、安装证据和应用版本不自动进入既有 canonical 发布头。

## 4. 消费边界与验证

本纯值边界不解析 JSON。格式整数的文本协议要求 `[1-9][0-9]*` 且不超过 2147483647；
严格 reader 必须从原 token 区分数字与字符串并拒绝小数、指数和前导零，不能先转成 Java
`int` 再声称验证过原词法。版本字符串 `01` 合法不意味着格式整数 token `01` 合法。
既有 v1 reader 的冻结行为不由此改写。

纯测试覆盖身份与长度、四种查询状态、描述符状态/摘要组合、格式结构与精确映射、
重复/默认拒绝以及独立 expected 查询。完整目录校验、内容重算、文件读取、安装、事务、
数据库约束和运行视图仍由各自消费者负责；本合同不新增安装入口、迁移或格式 2 发布能力。
重复验证命令与完整构建要求见 [测试指南](../testing.md#4-迁移与规则测试)。
