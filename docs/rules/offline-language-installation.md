# 离线 DRAFT 目录制品

独立作者工具可构建和核对语言／工具目录制品。它检查准确文件清单、外部 manifest
摘要和两项 DRAFT 分区，不改变本机接受账本。正式作者目录的独立验证见[作者合同](language-author-package.md#独立验证)。

在 Linux／WSL 的本地文件系统运行下列命令。制品读取要求 JDK 文件系统提供安全目录句柄。

```bash
mvn compile
bash tools/rule-packages/rule-package.sh build rule-packages/srd51-complete /controlled/new-artifact
bash tools/rule-packages/rule-package.sh verify /controlled/new-artifact EXTERNAL_MANIFEST_SHA256
```

`RuleArtifactTest` 与 `RulePackageCliTest` 检查准确库存、摘要、严格解析、文件预算和实际命令。文件用例按照实际目录 provider 有条件执行，解析和不支持 provider 的拒绝在各平台验证。
该制品的 `PARTITION` 证据只覆盖 18 项语言和 37 项工具。生产 schema 2 原包走
[独立 ZIP 验证和本机接受](schema-2-installation-and-acceptance.md)。

旧来源安装与查证随匹配版本保管。[原版合同](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/offline-language-installation.md)
