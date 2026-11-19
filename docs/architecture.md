# 架构

Local 5E Campaign Tool 以本机 SQLite 运行给一位 DM 使用的战役桌，默认只有环回 HTTP。
DM 新建或打开战役后可使用角色卡、冒险、检定和当前规则支持的战斗。受邀请且经批准的
玩家可选用独立 HTTPS 入口。产品边界与规则能力见[产品说明](product.md)、
[运行合同](storage-vitality-loop.md)和[规则包指南](../rule-packages/srd51-runtime/package-guide.md)。

## 启动与 HTTP 边界

应用采用 Java 21+、Jakarta Servlet 6、外部 Tomcat 10.1 和随 WAR 打包的 SQLite JDBC。
`WEB-INF/web.xml` 显式注册生产 Servlet、Filter 和 `StorageApplicationListener`。
监听器按[配置优先级](configuration.md#sqlite-当前应用存储配置)解析私有根、取得实例锁并
装配 `CampaignStorageService`。启动时保留已有残留供显式处理。根不可用时战役读写
失败关闭，就绪入口返回稳定类别。

| 路径 | 责任 |
| --- | --- |
| `/`、`/host` | 进入本机战役桌 `/host/storage` |
| `/health` | 进程存活检查 |
| `/api/host/storage/*` | 读取战役、管理文件及规则库、提交 DM 命令 |
| `/api/host/storage/readiness` | 只读核验存储服务、保存文件列表及活动状态 |
| `/api/host/storage/players/*` | DM 管理玩家席位、权限、共享和裁定 |
| `/player`、`/api/player/*` | 独立 HTTPS 的有限玩家页面和允许的 API |

Host 入口固定为 `http://127.0.0.1:8080`，应用检查连接、Host 和请求方法。Host 写请求
还校验来源、CSRF、会话的权威 epoch、版本和请求标识/摘要。玩家连接使用独立凭据和会话，
受战役席位和当前授权控制。默认关闭的玩家入口在启用时仅接受准确 IPv4、TLS 与允许的
路径和方法。完整边界见[安全合同](security.md)。

## 规则和业务状态

- Servlet 负责有界解析、请求形状和 HTTP 响应。服务拥有生命周期、授权、幂等、
  冻结规则验证和事务入口。领域代码拥有规则解释、合法目标、状态变更和审计事实。
- 标准新战役由随应用交付的准确原包组合创建。自定义组合从本机规则库选择已经
  导入并接受的准确原包。组合投影、绑定和完整解释链进入活动战役并冻结。
- 每个活动运行由私有 SQLite 工作库承载。新建或打开按受审 JSON schema 声明初始化
  数据库。同版残留恢复校验结构和原有状态，保留 run 和收据并建立新 epoch。
- `CampaignStorageService` 串行化受管生命周期和写入。`CampaignDomainRepository` 在
  调用方事务内保存聚合、关系投影、历史、revision 和请求收据。完整请求及准确上下文
  先通过验证，再执行需要的随机判定。事件、效果、快照和状态共同提交或回滚。
- `PlayerGatewayService` 负责玩家会话和有限请求。投影层只给客户端发布有权看到的
  私有角色和共享视图，动作由服务器给出候选。业务授权与运行身份在每次执行时核验。

## 文件、恢复与离线工具

受管根隔离 `runs/`、`saves/` 和 `rule-library/`。完整文件保存冻结规则、业务状态和
历史。运行锁、工作库和 journal 保存执行空间，规则库的原包与账本须共同备份。
完整文件打开建立新执行身份。故障恢复继续核对原运行及其事务记录。
见[存储运行合同](storage-vitality-loop.md)和[备份恢复](backup-and-restore.md)。

MySQL 迁移、JDBC 核验和规则源维护属于[独立数据库工具](database.md)。已有数据库和
存档从生产 WAR 之外保管。发布所需的行为验证、WAR 审计与实际部署见
[测试指南](testing.md)和[部署指南](deployment.md)。
