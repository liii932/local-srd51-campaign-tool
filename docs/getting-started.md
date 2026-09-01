# 快速开始

Local 5E Campaign Tool 是部署在外部 Tomcat 10.1 上的 Java 21 WAR。项目目标是完整覆盖
SRD 5.1，但当前构建仍提供冻结的简化 v1 功能。它不是可执行 JAR，业务入口只允许本机
精确地址 `http://127.0.0.1:8080`。

## 前提

- JDK 21；
- Maven 3.9；
- Apache Tomcat 10.1；
- MySQL 8.0；
- Tomcat `lib` 中可用的 MySQL Connector/J；
- 已按 [数据库说明](database.md) 准备的 schema 与最小权限运行账号。

## 构建

在仓库根目录执行：

```powershell
mvn clean verify
```

成功构建会生成 `target/dnd-tool-se-0.1.0-SNAPSHOT.war`。必须以 Maven 最终退出码和 `BUILD SUCCESS` 为准；生产候选还应按[测试指南](testing.md)审计 WAR。

## 配置与启动

1. 复制 [ROOT.xml.example](../config/tomcat/ROOT.xml.example) 到 `%CATALINA_BASE%\conf\Catalina\localhost\ROOT.xml`。
2. 复制 [setenv.bat.example](../config/tomcat/setenv.bat.example) 为 `%CATALINA_BASE%\bin\setenv.bat`，保留环境变量属性源设置。
3. 在启动 Tomcat 的同一个 PowerShell 会话中安全输入运行账号口令，不要把口令写入仓库、命令行参数或 Java 选项。
4. 按[部署指南](deployment.md)备份现有根应用并部署经审计的候选为 `ROOT.war`。
5. 启动后访问 `http://127.0.0.1:8080/host`。

最小验收包括：`/health` 返回 `200` 和 `OK`；`/` 相对重定向到 `/host`；`/host` 返回安全响应头；错误 Host、`localhost`、`::1`、`/display` 和 `/api/public/*` 不暴露应用能力。

配置项见[配置说明](configuration.md)，常见失败见[故障排查](troubleshooting.md)。
