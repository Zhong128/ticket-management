# 支付宝沙箱验收记录与接入步骤

状态：**待接入真实支付宝沙箱**。2026-10-20 检查时，本机未设置 `ALIPAY_APP_ID`、`ALIPAY_MERCHANT_PRIVATE_KEY`、`ALIPAY_PUBLIC_KEY`、`ALIPAY_NOTIFY_URL`；项目作者确认尚未申请沙箱材料。因此现有自动化测试中的支付渠道、回调验签入口和退款渠道均使用测试替身，不能记为真实沙箱付款或退款成功。

项目现有 `AlipayPaymentGateway` 使用沙箱网关 `https://openapi-sandbox.dl.alipaydev.com/gateway.do`，调用电脑网站支付 `alipay.trade.page.pay`，并实现查单、关单、退款和退款查询；`PaymentController` 接收异步通知。支付宝开放平台说明，沙箱联调需沙箱 AppID、应用私钥、支付宝公钥与沙箱网关。参见 [支付宝开放平台接入说明](https://open.alipay.com/paymentServicer/paymentProvider.htm) 和 [沙箱相关支持入口](https://open.alipay.com/support/supportCenter.htm)。

## 取得材料后执行

1. 用已实名认证的支付宝账号登录 [支付宝开放平台](https://open.alipay.com/)，按 [官方接入流程](https://open.alipay.com/platform/accessProcessPage.htm) 创建自用型网页/移动应用，再进入应用的沙箱调试入口。先在 [沙箱支持中心](https://open.alipay.com/support/supportCenter.htm) 核对当前是否支持项目使用的电脑网站支付、查单、关单及退款接口；开放平台页面可能随账号和版本调整，以登录后的实际入口为准。
2. 在沙箱应用页记下 **沙箱 AppID**。使用开放平台的密钥工具在本机生成一对应用公私钥，只把应用公钥配置到沙箱应用，随后从平台复制 **支付宝公钥**；不要把“应用公钥”和“支付宝公钥”混用。[官方接入说明](https://open.alipay.com/paymentServicer/paymentProvider.htm)列出了沙箱 AppID、沙箱网关、应用私钥、支付宝公钥四项接口材料。再从沙箱账号/沙箱钱包入口取得测试买家账号。商户应用私钥仅保存在 Git 忽略的本地配置或环境变量里，绝不要发到聊天、写入仓库或提交日志。
3. 使用独立 MySQL、Redis、RabbitMQ 和独立应用实例；在该库执行 V1～V4 迁移并开启 `TRADE_ENABLED=true`。保持 `TRADE_MIGRATIONS_ENABLED=false` 作为正常运行配置。用环境变量设置 `ALIPAY_APP_ID`、`ALIPAY_MERCHANT_PRIVATE_KEY`、`ALIPAY_PUBLIC_KEY`；将 `ALIPAY_NOTIFY_URL` 设置为能转发到该隔离实例 `/api/public/payments/notify` 的临时 HTTPS 地址。回跳地址 `ALIPAY_RETURN_URL` 可另外指定，不能把回跳当作付款成功证据。
4. 创建一场只供沙箱验收的演出、场次和票档，用不同测试用户各下一笔订单。发起支付后用沙箱买家完成一笔付款，核对异步通知验签、应用 ID、交易号和金额校验，随后核对 `payment_record`、`order_info`、`stock_reservation`、Outbox 及库存。重复投递同一通知，确认状态与库存不重复变化。
5. 对另一笔未付流水验证查单、关单与取消；对已付款且需要退款的场景验证退款单号持久化、同号重试与查询。记录支付宝沙箱返回的状态、数据库结果和脱敏日志，不保存私钥、完整 Token 或敏感支付信息。
6. 停止隔离实例并回收临时环境。只有真实渠道调用、真实沙箱买家付款、实际异步通知和渠道退款/查询都得到证据，才把本记录的状态改为“通过”。

这一步不触及真实资金。沙箱材料缺失时，不能用本地伪造回调替代真实验收。
