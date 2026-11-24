# 共同业务写入与初始化准备

当前 SQLite 通过独占接纳及单个 `BEGIN IMMEDIATE` 事务提交业务事实、根子历史、版本和原操作接受收据。请求与冻结材料先于随机性核验，提交结果不明时进入显式恢复。初始计划作为领域事实参与保存。

[当前事务和恢复合同](../design/v2/runtime-and-transactions.md)。

既有资料的[保管责任](../database.md)与[原版合同](https://github.com/liii932/local-srd51-campaign-tool/blob/4f485b3158c3bb3148463c68f9ada53839edaa1e/docs/rules/business-write-transactions.md)。
