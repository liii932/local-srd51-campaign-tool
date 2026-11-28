# 快速开始

Local 5E Campaign Tool 以 WAR 部署到外部 Apache Tomcat 10.1，运行需要 JDK 21。
Web 应用使用随 WAR 交付的 SQLite JDBC，在本机私有目录保存战役。
DM 入口固定为 `http://127.0.0.1:8080`，玩家 HTTPS 默认关闭。

## 获取 WAR

使用 0.2.1 发行制品 `dnd-tool-se-0.2.1.war`。从源码构建时需要 Maven 3.9，在仓库根目录执行
`mvn -DskipTests package` 生成 `target/dnd-tool-se-0.11.0.war`。该命令跳过应用测试。
源码变更的验证范围与候选 WAR 审计见[测试指南](testing.md)。

## 启动与验收

1. 按[部署指南](deployment.md)审计 WAR，配置只绑定 `127.0.0.1:8080` 的 DM Connector，
   将制品部署为 `webapps/ROOT.war`，再启动 Tomcat。存储默认使用 Tomcat 运行账号的
   `${user.home}/.dndtool/campaigns`。需要另设私有根时按[配置说明](configuration.md#sqlite-当前应用存储配置)
   设置 `DND_STORAGE_ROOT`。
2. 打开 `http://127.0.0.1:8080`。`/` 和 `/host` 均返回 `303`，转到 `/host/storage`。
   `GET /health` 应返回 `200` 和 `OK`。就绪接口 `GET /api/host/storage/readiness` 在健康
   空闲或活动状态下返回 `200`、`mode=SQLITE` 和 `ready=true`。
3. 在工作台输入战役名称，保留默认的“标准规则”。服务器直接核验并组合 WAR 内的四个准确原包。
   自定义战役从本机规则库选择已接受的成员。首次主持可按[短冒险流程](storage-vitality-loop.md#跑一段短冒险)
   完成建队、探索、战斗，保存后可继续。

可选玩家入口见[部署指南](deployment.md#可选玩家-https-入口)，启动失败见[故障排查](troubleshooting.md)。
