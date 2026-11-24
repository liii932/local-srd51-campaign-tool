# Schema 2 制品验证与本机接受

当前本机规则库按[准确 ZIP 导入、DM 审阅和独立接受](../design/v2/local-rule-library.md)维护。准确接受记录绑定
原 ZIP、逻辑身份和审阅事实。计划创建的新战役还需通过应用的组合资格门。

在 Linux／WSL 的可安全读取目录中，作者可独立运行传输验证：

```bash
mvn compile
bash tools/rule-packages/rule-package.sh schema2-verify /controlled/module.zip
```

`schema2-verify` 输出模组、准确版本、角色、生产 profile 与原 ZIP／逻辑摘要，不写入规则库。
验证完整组合与行为另用[独立作者场景](../../rule-packages/srd51-runtime/scenarios/README.md)。

原 MySQL 来源安装、审阅及查证已退出产品。真实原包与原账本的保管见[数据库说明](../database.md)。
[原版合同](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/schema-2-installation-and-acceptance.md)
