# 部署

这里描述外部 Tomcat 10.1 的 SQLite ROOT 应用部署。实际停启服务、替换 WAR、变更设备
信任和防火墙均由获授权的操作者执行。

## 1. 构建候选

使用发行制品 `dnd-tool-se-0.2.1.war`，或在仓库根从源码生成 `target/dnd-tool-se-0.2.1.war`。
按[测试指南](testing.md)选取受影响的应用测试和模组检查。应用结果可复用时，或本次只改规则包时，
运行 `mvn -DskipTests package`。旧输出可能影响候选时，在打包命令中加入 `clean`。

交付可运行模组变更时，用候选 WAR 执行受影响的作者场景，核对实际加载的组合和 ZIP 身份。
应用与模组一并改动时，先完成定向应用验证及候选构建，再用新 WAR 执行这些场景。
记录各项实际使用的命令、制品和结果。

按 [WAR 审计方法](testing.md#6-war-审计)记录候选字节数、SHA-256 和 ZIP 条目数。核对
生产描述符、Servlet 和 Filter、SQLite JDBC、结构 JSON、五个规则原包、对应的 `bundled-members.tsv` 身份清单和 Host/玩家
静态资源。逐项核对交付类与源码、实际依赖以及清单，审计退役的运行与来源适配器、SQL、离线工具、MySQL 驱动及旧路由。复核资源和配置差量中的秘密、外部容器配置、日志、备份及私有战役资料。[原数据保管](database.md#迁移合同)

候选须通过[无 MySQL 采用验收](testing.md#mysql-free-acceptance)，包括空 Maven 库解析、真实 WAR 启动、两个环境间打开完整文件和原工作库恢复。正式替换按本页备份与回滚流程处理，旧库保管见[数据库说明](database.md)。

<a id="isolated-war-candidate"></a>

### 隔离外部 Tomcat 候选

使用全新 Tomcat 10.1 发行包和私有的一次性 base、运行根、证据目录。启动前核对 8080、
8005 及启用玩家时的 8443 空闲。Windows 使用[原生候选工具](testing.md#原生-windows-外部-tomcat-候选验收)。
Linux 在环境 A 运行以下准备步骤。变量分别填入干净 Tomcat 解压目录、JDK 目录、审计后 WAR 的
绝对路径；其余目录均在本地 POSIX 文件系统。以本次测试账号执行，使用该账号的干净启动环境。

```bash
set -eu
umask 077
: "${clean_tomcat_home:?}" "${candidate_java_home:?}" "${candidate_war:?}"
for candidate_input in "$clean_tomcat_home" "$candidate_java_home" "$candidate_war"; do
  case "$candidate_input" in /*) ;; *) exit 1 ;; esac
done
test -x "$clean_tomcat_home/bin/catalina.sh"
test -x "$candidate_java_home/bin/java"
test -f "$candidate_war"
candidate_dir="$(mktemp -d)"
candidate_shutdown="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
export CATALINA_HOME="$clean_tomcat_home" JAVA_HOME="$candidate_java_home"
export CATALINA_BASE="$candidate_dir/base"
export DND_STORAGE_ROOT="$candidate_dir/storage"
mkdir -p "$CATALINA_BASE"/{conf/Catalina/localhost,logs,temp,webapps,work,lib}
mkdir -p "$candidate_dir"/{home,evidence}
cp "$CATALINA_HOME"/conf/{web.xml,catalina.properties,logging.properties} "$CATALINA_BASE/conf/"
cp "$candidate_war" "$CATALINA_BASE/webapps/ROOT.war"
printf '<Context />\n' > "$CATALINA_BASE/conf/context.xml"
printf '<Context />\n' > "$CATALINA_BASE/conf/Catalina/localhost/ROOT.xml"
cat > "$CATALINA_BASE/conf/server.xml" <<XML
<Server port="8005" address="127.0.0.1" shutdown="$candidate_shutdown">
  <Service name="Catalina">
    <Connector address="127.0.0.1" port="8080" protocol="HTTP/1.1" connectionTimeout="20000" maxPostSize="16778240" />
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false" deployOnStartup="true" />
    </Engine>
  </Service>
</Server>
XML
unset candidate_shutdown
export CATALINA_OPTS="-Duser.home=\"$candidate_dir/home\""
cmp -s "$candidate_war" "$CATALINA_BASE/webapps/ROOT.war"
sha256sum "$candidate_war" "$CATALINA_BASE/webapps/ROOT.war"
"$CATALINA_HOME/bin/catalina.sh" run
```

核对复制前后摘要，再从另一终端检查监听和就绪。正常停机使用同一 home/base 的
`catalina.sh stop`，待进程和端口退出。做中断验收时，用实际 Java 进程的启动参数、
PID 和启动时间确认准确 base，再终止该候选进程。Windows 用候选工具的 Status/Stop
核对进程身份。变动 Context、环境变量和 JVM 参数前停止一次性实例，保留旧运行根及
已有证据，按[启动分支](testing.md#mysql-free-acceptance)准备新夹具。
[Tomcat 安装与启动](https://tomcat.apache.org/tomcat-10.1-doc/setup.html)

退役验收记录两侧实际启动命令、类加载来源与连接尝试。JDK 可加 `-Xlog:class+load=info`
并保留启动标准输出。A 可在前述 `catalina.sh run` 前加
`strace -ff -e trace=connect -o "$candidate_dir/evidence/connect"`。B 可在隔离虚拟机上
用获授权的管理员终端运行以下追踪，Tomcat 仍以测试运行账号启动。

```powershell
netsh trace show status
netsh trace start provider=Microsoft-Windows-TCPIP provider=Microsoft-Windows-Winsock-AFD report=disabled persistent=no "tracefile=$traceFile"
# 从另一个终端启动候选，完成就绪检查及本次业务后正常停止。
netsh trace stop
netsh trace convert "input=$traceFile" "output=$traceText" dump=XML report=no
```

为每次追踪选择新证据路径，保持已存在的追踪会话。将 Bash 的 `$closed_port` 或 PowerShell
的 `$closedPort` 设为已确认未监听的环回端口，执行以下探针。预期连接失败，并在追踪中看见
连接尝试。A 使用独立 strace 输出前缀，B 在同一 ETW 追踪中按探针 PID 核对。

```bash
strace -ff -e trace=connect -o "$candidate_dir/evidence/control-connect" \
  curl --noproxy '*' --connect-timeout 2 "http://127.0.0.1:${closed_port}/"
```

```powershell
curl.exe --noproxy '*' --connect-timeout 2 "http://127.0.0.1:${closedPort}/"
```

按候选 Java PID 和运行时段核对实际事件，排除数据库
驱动加载及未知目标连接；正常健康探针、候选 HTTPS 和停止命令的环回流量单独标明。
无法观测启动或连接尝试时保留对应证据缺口。追踪不替代产物、配置及启动调用链检查。
[Windows 网络追踪](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/netsh-trace) ·
[Winsock 事件与进程对应](https://learn.microsoft.com/en-us/windows/win32/winsock/control-of-winsock-tracing)

## 2. 存储前提

确认 Tomcat 的实际运行账号、[私有根和文件系统](configuration.md#sqlite-当前应用存储配置)、
锁和已有残留。Windows 使用本地固定 NTFS 私有目录。按[原生 Windows 候选验收](testing.md#原生-windows-外部-tomcat-候选验收)
验证进程中断恢复。活动部署更换前先保管已有数据库、完整战役文件和外部配置。

## 3. 外部配置

WAR 自带 SQLite JDBC，启动无需 JNDI 数据源、MySQL 服务或 Connector/J。默认存储根为
实际 Tomcat 运行账号的 `${user.home}/.dndtool/campaigns`。需要改根时给 Tomcat 进程设置
绝对路径 `DND_STORAGE_ROOT`，或在 `%CATALINA_BASE%\conf\Catalina\localhost\ROOT.xml`
提供优先的[外部 Context 参数](../config/tomcat/ROOT.xml.example)。检查无效覆盖时的就绪错误，
保留真实根和残留供处理。从旧配置切换时保管完整旧 Context 和启动配置，当前 Context
使用 SQLite 参数及所需的其他外部容器设置。

DM Connector 固定绑定 `127.0.0.1:8080`，shutdown listener 同样限 loopback。按
[配置合同](configuration.md#web-connector)设置容器、JVM 和表单解析限制。完整文件导入
另由应用强制限制为 16 MiB。[setenv 示例](../config/tomcat/setenv.bat.example)仅在
批处理启动方式使用。Windows 服务直接读取服务配置。

### 可选玩家 HTTPS 入口

玩家 HTTPS 默认关闭。确需启用时，先完成候选准备、可回滚替换和 Host 基线验收，再按批准的
网络边界配置独立 HTTPS。

1. 选定私有、链路本地或用于隔离验收的 loopback IPv4 和固定端口 `8443`，按
   [玩家请求边界](configuration.md#可选玩家-https-原型配置)填写两项应用参数。
2. 使用[独立 Connector 示例](../config/tomcat/player-https-connector.xml.example)及
   SAN 准确包含该 IPv4 的证书。将 PKCS12 私钥库和口令保管在 Git/WAR 外，由受控启动
   环境提供占位符。实际玩家设备须信任该证书的颁发者。
3. 仅为选定私有网络上的 HTTPS 端口配置所需防火墙访问。DM Connector 继续只监听
   `127.0.0.1:8080`。
4. 用实际桌面和移动设备执行[玩家验收矩阵](design/v2/player-participation.md#验收与采用门)。
   核对证书信任、目标地址、两设备授权隔离、权限撤销和重连查证。
5. 关闭时移除相应应用参数、HTTPS Connector 和防火墙规则，保管运行及完整文件。

隔离 WAR 验收使用 `127.0.0.1:8443`，单独准备空白根和停止状态的候选 base。用 JDK
`keytool -genkeypair` 生成短期 PKCS12 测试证书，设置 `-ext SAN=ip:127.0.0.1`。将
示例 Connector 放在该 base 的 `server.xml` 中，与 DM Connector 同属一个 `Service`。
在候选 Java 上加入 `-Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource`，
从测试启动环境设置 `DND_PLAYER_BIND_ADDRESS=127.0.0.1`、`DND_PLAYER_ORIGIN=https://127.0.0.1:8443`、
`DND_PLAYER_KEYSTORE` 和 `DND_PLAYER_KEYSTORE_PASSWORD`。Linux 使用 `CATALINA_OPTS` 补 JVM
参数，Windows 候选工具从临时 `JDK_JAVA_OPTIONS` 读取。检查实际监听后，按[玩家参与验证](testing.md#玩家参与原型验证)
使用该测试证书和对应浏览器工具。

### 自动化部署身份

无人值守部署身份 `dnd-tool-deployer` 只需读候选、写仓库外回滚目录、控制指定的
Tomcat 服务、替换该服务的 ROOT WAR 与展开目录、读取最新日志和本地端口状态。
实际 Tomcat 运行身份拥有受管根所需的私有权限。MySQL 维护、账号和库的权限按数据库
合同单独授予。

## 4. 可回滚替换

1. 停止目标 Tomcat，确认 Java 进程和该实例的 8005、8080 以及已启用的 8443 退出。
2. 备份活动 `webapps\ROOT.war`、完整 `webapps\ROOT`、ROOT Context、`server.xml`
   和启动配置，记录原本不存在的对象。仓库外整体保管该实例实际使用的 SQLite 根，
   包括规则库原包及账本、全部运行库和 journal、保存文件、准备和清理材料。
3. 核对目标路径，移除旧展开目录，将审计过的候选复制到 `webapps\ROOT.war`。
   核对候选和活动 WAR 的 SHA-256，并检查目标 context 的配套配置。
4. 使用同一配置和运行账号启动目标实例，然后按下节验收。

## 5. 启动与验收

检查最新启动日志、目标 Java 进程和 listener。8080 及 shutdown listener 只监听
loopback。应用启用了玩家入口时核对精确 8443 地址，并执行设备验收。

| 请求 | 预期 |
| --- | --- |
| `GET /health` | `200`，UTF-8 纯文本 `OK` |
| `GET /`、`GET /host` | `303`，转到 `/host/storage` |
| `GET /host/storage` | `200`，战役桌与资源正常加载 |
| `GET /api/host/storage/state` | 读取活动运行、可见保存文件及残留状态 |
| `GET /api/host/storage/readiness` | `mode=SQLITE`。IDLE/ACTIVE 返回 `200` 和 `ready=true`，阻断、残留或存储根故障返回 `503` 和稳定代码 |
| 错误 Host、`localhost`、`::1` 访问 DM 路由 | `404` |
| DM Connector 上的 `/player`、`/api/player/*` | `404` |
| `GET /api/host/diagnostics/database`、`GET /display`、`GET /api/public/state` | `404` |

只读与拒绝路径由 `tools/storage/verify-war-http.cjs` 复验。涉及 UI 的候选验证桌面
和窄屏主要流程及控制台。业务写入使用经授权的隔离根和可恢复样本。

`STORAGE_RECOVERY_REQUIRED` 需在战役桌显式恢复或精确放弃相应 run。
单个损坏的独立保存文件在列表标为不可用，健康的运行或空闲根仍可通过就绪检查。
就绪检查和故障排查保留原材料，按[配置](configuration.md#sqlite-当前应用存储配置)和
[运行合同](storage-vitality-loop.md)处理其他返回类别。

### 日常打开与关闭

将 `http://127.0.0.1:8080` 保存为书签。Windows 交互启动使用已配置的
`CATALINA_HOME`、`CATALINA_BASE`、`JAVA_HOME` 和对应的 `startup.bat`；
服务方式使用已配置的服务。需要结束运行时在战役桌执行 END，核对保存完成和空闲状态，
再操作 `shutdown.bat` 或停止服务。直接关浏览器或服务后，原 run 按残留流程处理。

## 6. 回滚

停止实例并保管失败候选及日志。在仓库外另外保管当前 SQLite 根，恢复相匹配的旧 WAR、
展开目录、外部配置和已有备份，按恢复版本的合同校验活动 WAR 哈希和数据库或存储根。
跨应用版本时使用该版本支持的数据，避免用旧程序解释新格式的文件和残留。
SQLite 数据恢复须独立授权，按[同版恢复流程](backup-and-restore.md#sqlite-完整文件与同版恢复)执行。
