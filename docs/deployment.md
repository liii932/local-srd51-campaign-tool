# 部署

本指南描述外部 Tomcat 10.1 根上下文的可回滚部署流程。部署会改变仓库外状态，应由明确授权的操作者执行；构建成功不等于已经部署。

## 1. 构建候选

在仓库根目录执行：

```powershell
mvn clean verify
```

确认最终退出码为 0、测试计数完整且出现 `BUILD SUCCESS`。候选默认为 `target/dnd-tool-se-0.1.0-SNAPSHOT.war`。

记录候选的字节数、SHA-256 和 ZIP 条目数。检查必需的应用类、JSP、JavaScript、`web.xml` 和 V001—V017 资源存在；检查私钥、凭据、外部 Tomcat 配置、日志、备份、真实存档、结果捕获与本地文件名不存在。还应阅读生产资源/config 差异，文件名扫描不能代替内容审计。

## 2. 数据库前提

新环境的数据库准备与 WAR 部署是两个独立步骤：

1. migrator 按顺序应用尚未执行的前向迁移；
2. 使用 [database/verify](../database/verify/) 中的只读脚本核对结构和 `schema_meta`；
3. 单独应用 [database/grants](../database/grants/) 中与当前应用职责匹配的最小运行授权；
4. 以只读诊断确认安装的最新迁移与应用预期一致。

V001—V017 已应用后不可修改、合并、重命名或重跑。SQL 文件存在、迁移成功、授权成功和应用启动是四种不同证据。不要把广泛 schema 权限授给运行账号。

数据库操作的原则与账号边界见[数据库文档](database.md)。

## 3. 外部配置

将无秘密示例按实际 `%CATALINA_BASE%` 复制到外部配置：

- [config/tomcat/ROOT.xml.example](../config/tomcat/ROOT.xml.example) → `%CATALINA_BASE%\conf\Catalina\localhost\ROOT.xml`
- [config/tomcat/setenv.bat.example](../config/tomcat/setenv.bat.example) → `%CATALINA_BASE%\bin\setenv.bat`

Tomcat Connector 只绑定 `127.0.0.1:8080`。Connector/J 位于 `%CATALINA_BASE%\lib`。数据库口令由启动进程交互提供，不写入 XML、批处理、JVM 参数或仓库。

## 4. 可回滚替换

1. 停止 Tomcat，并确认相关 Java 进程和 8005/8080 listener 已退出。
2. 对当前 `webapps\ROOT.war` 和完整展开目录 `webapps\ROOT` 创建仓库外、带时间戳的回滚副本。
3. 确认目标路径精确无误后，移除旧展开目录，复制经审计候选为 `webapps\ROOT.war`。
4. 比较候选和活动 `ROOT.war` 的 SHA-256；确保没有临时 WAR、旧并行 context 或部分展开目录。
5. 若启动需要交互输入数据库口令，保持 Tomcat 停止并交给操作者启动。

不要因两个独立 ZIP 构建的时间戳差异自动替换活动 WAR。只有已审计的明确候选才能进入部署流程。

## 5. 启动与验收

启动后只检查最新日志段，确认没有新的 `SEVERE`、`ERROR`、连接或 schema/hash 失败。系统层面应只有一个预期 Java/Tomcat 实例，8005/8080 只监听 loopback，8443 不监听。

最小 HTTP 验收：

| 请求 | 预期 |
|---|---|
| `GET http://127.0.0.1:8080/health` | `200`、UTF-8 `text/plain`、正文 `OK` |
| `GET /` | `302`，相对定位到 `/host` |
| `GET /host` | `200`、安全响应头、主机 Session Cookie |
| 主机数据库诊断 | `200` 且精确识别最新迁移/规则状态 |
| 错误 Host、`localhost`、`::1` | 普通 `404` |
| `/display`、`/api/public/*` | `404` |

只有 UI 或客户端 JavaScript 变化时才需要真实浏览器验收；应检查主要页面、表单和控制台错误。不要用 HTTP 探测执行真实业务写入，除非该写入验收被单独授权并有恢复方案。

## 6. 回滚

若启动或验收失败：停止 Tomcat，保留失败候选和最新日志证据，恢复整套旧 `ROOT.war` 与展开目录，再确认恢复后的活动哈希。数据库迁移是前向历史，不能通过替换 WAR 自动回滚；数据库恢复必须使用[备份与恢复](backup-and-restore.md)中的单独授权流程。

常见错误见[故障排查](troubleshooting.md)。
