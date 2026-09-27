# 语言分区链路等价核验

语言分区核验连接现有作者读取、离线安装、源库只读适配与运行镜像仓储，检查每个边界是否保留
准确内容。范围只有 `dnd5e2014_srd51_se` / `1` 的 18 项 `character.language`，不是完整规则
目录、执行上下文或存档验收。旧 RELEASED v1 仍为默认，完整规则族保持 DRAFT，archive 2
发布门不变。

字段与投影以[语言作者分区](language-author-package.md)为准；安装、连接及事务所有权分别沿用
[离线安装](offline-language-installation.md)、[源分区读取](source-language-reading.md)和
[运行语言快照](runtime-language-snapshot.md)。本核验不引入生产装配器或新的分区摘要协议。

## 独立期望与链路

无数据库测试 `LanguagePartitionEquivalenceTest` 从正式作者文件构建制品，经过生产安装逻辑
写入事务 JDBC 代理；源只读适配器读取这些实际写入的行，再交给运行仓储写入和回读，最后
通过指定 run/snapshot 身份对读取分区视图。后续边界不以重新生成的期望行替换上一步的结果。

独立测试期望逐项列出语言键、显示名、说明、分类、来源页和排序号，不由生产 reader、mapper
或写入后像生成。每个边界同时检查六字段、18 条定义、36 条类型化属性及规范字节；语言关系
为空。`catalog.category` 必须是 IDENTIFIER，`source.page` 必须是 INTEGER，属性顺序为
category 在 page 前。规范键序与显式显示顺序分别检查。

基准字节使用既有 `src/test/resources/module-canonical-v2-languages.hex`，其独立编码来源见
[作者分区的独立验证](language-author-package.md#独立验证)。测试使用的窄目录适配只为调用
既有 canonical-v2 encoder，不进入生产运行路径。这个向量和测试中计算的字节不作为完整
`content_sha256`、COMPLETE 观测或发布批准值保存。

## 故障与等价边界

| 输入或故障 | 必须观察的结果 |
|---|---|
| 正式作者源经过安装、源读取、镜像写入和分区读取 | 各边界均与独立字段和字节期望相符 |
| 作者数组或 JDBC 返回行次序改变 | 规范内容不变，显式 `sort_order` 不被数组位置替换 |
| 合法显示名、说明、来源页或排序改变 | 新内容逐字段保留，规范字节改变，旧基准不能继续通过 |
| 源与镜像同样丢失说明，或把来源页编码成 TEXT | 即使往返相等，独立字段或字节核验仍失败 |
| 源本地 ID、安装代次改变而内容相同 | 规范内容不变，源技术身份不进入镜像规则内容 |
| 相同语言键出现在两个内容不同的快照 | 按准确身份对得到各自内容；错配身份或带其他快照身份的行失败 |
| 缺字段、缺项、额外项、非法分类/排序/Unicode/JDBC 类型 | 失败关闭，不补齐、修复或发布部分结果 |
| 镜像写入、实际读回或调用方后续操作失败 | 调用方回滚整笔事务，登记、头与语言均无部分提交 |
| 源连接不可用后读取已有镜像 | 分区读取不借用源连接；损坏的镜像仍失败关闭 |

这张表不把分区只读证明升级为全部业务路径的源库隔离。运行仓储返回的是不可变语言分区，
不是可执行的完整规则视图；不能据此开放初始化、导入、战役绑定或 format 2 存档。

补充平面文本正例将显示名填至 120 码点、说明填至 1000 码点，贯穿相同链路与逐字段核验，
并比较 UTF-8 规范字节。它与非 NFC 读取负例一起保护文本保留行为；不把代理的字符串保存
等同于真实 MySQL 字符集和驱动转换验收。

## 可重复验证与证据边界

```bash
mvn '-Dtest=LanguagePartitionEquivalenceTest,MySqlIntegrationTestSupportTest' test
mvn clean verify
```

第一条命令同时重验数据库测试目标保护；两组测试都不连接真实数据库。链路代理记录生产 SQL
和参数，并以事务状态模拟提交/回滚。这些结果证明 Java 边界上的字段保留、类型、编码、身份
与事务调用协议，不证明 MySQL 引擎、驱动、权限、触发器、外键或真实持久化的行为。

真实 JDBC 验收须单独授权物理隔离、可销毁的 MySQL 实例，按[测试指南](../testing.md#31-自动化数据库验证顺序)
原样重放运行 V001—V019 及独立规则迁移链，分别验证账本、结构和最小权限，再执行完整制品
安装、源只读读取、调用方事务镜像写入及指定身份读取。维护证据必须来自实际独立审计，不能
使用代理测试中的示例报告充当安装授权或真实历史证明。

在真实链路上复用同一独立字段矩阵与受审字节向量，验证合法内容变化、重复安装、不同快照、
读取损坏及写入/回读/调用方失败后的持久状态；非法 SQL 行若先被数据库约束拒绝，应分别记录
数据库拒绝与 Java reader 拒绝的证据。源 installer、源只读与运行写入账号各守其授权边界。
不复用部署实例或临时表测试库，不放宽 `MySqlIntegrationTestSupport` 的目标保护。

### 隔离 JDBC 入口

Linux/WSL 的复用入口为
[`verify-language-jdbc.py`](../../tools/rule-packages/verify-language-jdbc.py)。默认仅核对生产清单和
批准 SQL 载荷，不连接 Docker 或数据库：

```bash
python3 -B tools/rule-packages/verify-language-jdbc.py
python3 -B tools/rule-packages/tests/verify-language-jdbc-test.py -v
```

获得创建/销毁隔离实例、迁移、账号授权及测试写入的批准后，使用本地已有的 `mysql:8.0` 镜像：

```bash
python3 -B tools/rule-packages/verify-language-jdbc.py --execute-disposable
```

脚本先运行 `MySqlIntegrationTestSupportTest` 和 `LanguageJdbcAcceptanceTest`。随后通过本地
Docker socket 创建唯一带标签容器，固定执行已检查的镜像 ID，唯一数据挂载为 tmpfs，并核对
内核实际挂载；不接受现有容器、JDBC URL、schema 或 volume 参数。Linux host 网络使 MySQL 的
连接来源保持 `127.0.0.1`，服务只绑定该地址的随机非默认端口，关闭 MySQL X 和 binlog。
迁移按批准清单原始字节送入独立客户端，校验和执行绑定同一份字节；范围外内容的运行中变更也
会被完整文件摘要拒绝。迁移失败立即终止本次实例，不在部分成功的库上盲重跑。

迁移后分别以只读身份核对账本，以迁移身份读取完整定义，以管理员身份审计账号、角色、跨 schema
入向外键及其他对象。脚本把实际结构、触发器/DEFINER、CHECK、FK、授权和空历史写到仓库外的
0700 审计目录，并停在 `accept-schema` 输入点。操作人员须先独立核对这些材料与原始迁移及授权
模板；这一步是现场审计结论，不得由安装器或假报告替代。接受审计后才生成限定于本实例、一小时
有效的维护证据，整个票据历史共用一个 state 目录。
触发器正文采用 [`SHOW CREATE TRIGGER`](https://dev.mysql.com/doc/refman/8.0/en/show-create-trigger.html)
的 `SQL Original Statement` 比对原 SQL，保留字符集标记及字面值；`information_schema` 用于核对
触发时机、表和 DEFINER，其正文投影不充当原语句。

`LanguagePartitionJdbcIT` 是一个有序的真实验收场景，明确启用后才运行；普通构建不选择它。
每次连接在测试 SQL 前核对服务 UUID、默认 schema 和 CURRENT_USER。源安装、源只读、运行写入
和运行核验分别使用四个固定账号，密码只存在于进程环境。失败后的再次验收创建全新实例。
成功要求新生成的 Surefire 报告有实际测试且无失败、错误或跳过；报告留在外部审计目录。
无论成功、失败或可处理的中断，脚本先结束测试进程，再按精确 ID 和本次标签销毁容器并核对结果。

真实场景复用独立六字段期望及基准字节向量，覆盖重复安装、五种合法内容变化、不同快照、最大
Unicode 文本、真实 CHECK 拒绝与新连接查询的回滚结果。SQL 的 NULL、闭集键/分类及范围错误
分别记录为引擎拒绝；非 NFC 文本实际写入后由 reader 拒绝。读回异常在真实查询之后注入，后续
调用方异常由调用方注入，两者验证真实事务回滚。源不可用场景在 DataSource 取连接处注入失败，
已有镜像仍通过真实 JDBC 读取；它不声称整个 MySQL 实例停机，也不覆盖响应丢失或介质断电。

报告必须分列无数据库测试与真实隔离 JDBC 结果。未运行或未获授权的数据库项保留未验收状态，
不得用代理、静态 SQL 检查、历史通过记录或跳过测试关闭。普通构建不启动数据库或部署；测试
类、独立期望、作者包、离线安装类和维护材料均不得进入 WAR。
