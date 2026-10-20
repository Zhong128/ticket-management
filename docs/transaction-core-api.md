# 交易核心接口变更

写入交易前需在数据库副本验证迁移，并显式配置 `trade.enabled=true`。默认关闭新交易写入。

## 申请和查询

`POST /api/user/seckill` 仍接受 `tierId`、`quantity`，单次1至5张；新增可选 `requestKey`（最多64字符）。同一用户使用相同key和购买参数会返回同一申请；同key改参数会被拒绝。不带key的旧调用会由服务端生成key。

成功响应表示申请已持久化受理，包含 `requestId`、`requestStatus`、`orderNo` 和绝对 `expireTime`，落单前 `orderId` 可以为空。客户端通过 `GET /api/user/seckill/requests/{requestId}` 查询自己的申请，状态为 `RESERVED`、`ORDER_CREATED`、`PAID`、`RELEASED`。

库存与Outbox在同一MySQL事务写入；落单直接使用申请快照，不再二次扣库存。付款截止时间始终为预占成功后30分钟。三次落单失败后释放；前两次重试分别等待10秒、30秒。

## 取消及后台订单编辑

取消先进入 `CANCELING(4)`，渠道确认关闭后才进入 `CANCELED(2)` 并释放一次库存。渠道结果未知时保留库存及 `ORDER_CLOSE` 恢复任务。渠道确认成功支付会通过统一状态机入账；合法付款的延迟通知可以在仍持有库存时落账。

后台订单编辑不能修改金额、数量或直接设置已支付等状态；取消须走渠道关单流程，备注最多500字符。

## 票档维护

普通票档编辑 `PUT /api/admin/ticket-tiers/{id}` 不得提交 `totalStock`、`availableStock`，即使数值与当前显示一致也会拒绝。创建票档仍要求提交两个库存字段。存在有效申请或订单时，不能改变票档的演出或场次归属。

管理员通过 `POST /api/admin/ticket-tiers/{id}/stock-adjustments` 提交 `{"delta": 5}` 或负数，调整总库存与可用库存相同数量。操作要求票档已下架、无未完成预占和待支付/取消确认中订单，调整后库存不得为负。调整与库存版本递增、`STOCK_CHANGED` Outbox事件在同一事务完成。

`DELETE /api/admin/ticket-tiers/{id}` 现为软下架，只将状态设为0，保留票档及订单历史引用。已有申请仍可正常落单、取消和释放库存。

`DELETE /api/admin/events/{id}` 和 `DELETE /api/admin/sessions/{id}` 同样只下架对应演出或场次，保留父记录及其场次、票档引用，避免触发旧表的级联删除。演出或场次下架后不能发起新预占；已有订单仍可取消并按原票档回库。
