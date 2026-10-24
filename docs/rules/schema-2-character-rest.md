# Schema 2 休息作者声明

本合同只定义休息规则的作者字段、纯组合约束和来源事实。当前尚未接入该类型的运行镜像或
休息、时间、生命骰、治疗及资源恢复业务；声明存在不能作为“已经完成休息”的输入。

模组通过完整 `module_key / rule_type / rule_key` 标识 `adventure.rest`；规则键使用 `rest.` 前缀，
后续各段非空且符合稳定键字符规则。字段为 `display_name`、`description`、`source_page`、
`algorithm` 和可选 `author_note`。文本、Unicode、作者字段与启用状态遵守 schema 2 通用合同。

当前 `source_page` 只接受整数 87，`algorithm` 只接受准确的 `SRD_2014_REST_V1`，对应本地
[SRD 第 87 页](../references/srd-5.1/pages/page-087.md)。组合最多允许一个启用的休息声明；
未知算法、重复启用声明、坏字段或坏引用失败关闭。声明的禁用和字段修改保留完整来源链。

[来源账本](schema-2-source-ledger.md)保存原始休息元数据、作者序号、稳定身份和算法，并与准确
原 ZIP 逐列比较。资源容量与恢复时机另由[成长和资源作者合同](schema-2-character-statistics.md)的
`maximum_profile`/`recovery_profile` 声明；模组不能提交代码、数据库连接或客户端骰点。

时间所有权、活动和中断、健康与生命骰、恢复目标、长休收益间隔、版本、幂等和完整历史是业务
消费者必须另行闭合的责任。本作者模型不实现这些责任，也不扩大安装、运行、Host 或 archive 2 资格。
