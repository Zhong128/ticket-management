# 用户端接口与展示数据

当前完整入口与数据隔离方式见[完整本地运行版](local-usable-version.md)：8082同源打包、独立库交易开启、客服维护中。本文较早阶段的8081说明仅针对原库只读预览。

## 运行状态与维护

`GET /api/public/app-status` 为公开只读接口，返回 `data.ready`、`data.tradingEnabled`、`data.customerServiceEnabled`（布尔值）和 `data.instanceId`（本地运行实例标记，不是凭据）。检查脚本校验就绪和实例一致，页面据客服标志展示维护提示。

`customer-service.enabled=false` 时，已登录用户调用聊天接口得到业务响应 `{code:503,msg:"智能客服功能维护中，请稍后再试"}`，不调用外部模型。完整本地启动脚本默认关闭该功能。后续由用户恢复额度并明确要求复测后再恢复。

支付宝沙箱同步回跳到前端 `/payment/return?out_trade_no=...`。页面通过现有 `GET /api/user/payments/status/{paymentNo}` 及 `GET /api/user/orders/{id}` 查询本人结果；URL状态不参与入账。后端原异步通知接口不变，本机回环地址不能接收公网通知。

## 浏览接口

所有返回沿用 `Result<T>`，`code=1` 成功，`code=0` 或其他值是业务提示／失败。前端解析 JSON 时将 ID 保留为字符串，金额与数量保持数值。

| 方法和路径 | 参数／行为 |
|---|---|
| GET /api/public/cities | 新增；仅返回 status=1 的城市，按既有热门顺序 |
| GET /api/public/categories | 沿用启用分类列表 |
| GET /api/public/events/page | 沿用 name、artistName、categoryId、cityId、page、size；新增 keyword（最多100字）同时匹配演出名称或艺人；公共接口强制 status=1 |
| GET /api/public/events/{id} | 新增；未发布／下架／不存在统一返回业务失败，不向游客返回草稿 |
| GET /api/public/home/recommend/default | 新增可选 cityId；省略时仍取默认城市，有城市时按指定城市推荐；limit限制1–12 |
| GET /api/public/sessions?eventId= | 沿用启用场次列表 |
| GET /api/public/ticket-tiers/page?eventId=&sessionId= | 沿用票档分页，详情页每场次最多请求100条 |

`EventVO.minPrice` 为新增可空字段，按本次结果集批量查询最低票价，仅计算启用场次和启用票档，校验场次与票档的演出归属。售罄票档仍可用于价格展示；无有效票档为 null，前端显示“暂无可售票档”。不改变下单计价或库存逻辑。

前端 URL 中 `name` 为搜索框内容，实际请求后端 `keyword`；旧的 `name` 参数仍保持只查演出名称。列表城市以 URL 为准并同步顶栏与本地选择，刷新、前进后退和再次搜索均保留当前城市。

首页推荐接口遍历启用分类，支持新增演出类型；省略 cityId 仍按原默认城市推荐。前端全国首页通过公共分页取最多100条演出，排除已结束记录后按分类生成推荐区；选择城市后使用城市推荐接口。分类入口与“查看全部”均保留当前城市。

## 展示数据维护

`ticket-management/src/main/resources/demo/catalog-2026-10.json` 当前存放61条演出事实字段、公开来源链接、远程海报地址和单独的 demo 配置。采集日期2026-10-25。首批24条，本轮从大麦北京首页公开推荐及国家大剧院公开活动补充37条。来源包括大麦公开详情、国家大剧院艺术活动与临沂大剧院；来源排期为“待定”的项目如实记录，不推断官方演出时间。

本地场次、票档、价格、库存与开售时间均是模拟配置，已写入演出介绍并在页面提示。封面通过来源URL展示；未取得第三方商业转载授权，不将这些素材当作自有素材发布。图片失败有占位。

在后端模块目录先编译并构建运行时 classpath：

```powershell
mvn -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$runtime = (Get-Content target/runtime-classpath.txt -Raw).Trim()
java -cp "target/classes;$runtime" org.example.ticketmanagement.maintenance.DemoCatalogTool inspect
java -cp "target/classes;$runtime" org.example.ticketmanagement.maintenance.DemoCatalogTool import src/main/resources/demo/catalog-2026-10.json ../output/catalog-import.json
```

工具读取本地配置及环境中的数据库凭据，仅允许localhost/127.0.0.1的显式MySQL地址；默认本机3306的ticket库。不启动Spring、支付客户端、RabbitMQ监听或Flyway，不执行DDL，不覆盖已有演出。整批在一个MyBatis事务中插入，失败回滚。按演出名称跳过已有项目；清单路径必须不存在，重复导入使用新的清单文件以保留历史记录。请单进程手动执行，勿同时运行多个导入进程。

采集脚本 `scripts/collect-demo-catalog.py` 是维护工具，仅用Python标准库访问公开元数据，不下载海报、不绕过验证挑战；按演出名称向已有JSON追加，保留已有演示配置。国家大剧院场馆取各活动实际发布的地点，不统一写成音乐厅。网站结构变化时需要人工核对。样本日期是固定的本次验收数据，后续更新应同时调整demo排期和验证日期。

## 登录与交易接口（第二阶段）

页面路由：`/login`、`/checkout?eventId=&sessionId=&tierId=&quantity=`、`/purchase/result?requestKey=`、`/account/orders`、`/account/orders/:id`、`/orders/:id/pay`。除登录页外要求登录；失效后携带原地址回到登录，登录后不会自动提交申请。

| 方法和路径 | 请求／用途 |
|---|---|
| POST /api/auth/user/login-or-register | email、password（6–20位）；code=1登录成功；code=200且无登录数据表示进入邮箱验证 |
| POST /api/auth/user/verify-registration | email、code（4–6位）、新增可选password；页面始终传选定密码，由既有register加密一次 |
| POST /api/public/verification/resend | email、type=REGISTER；页面60秒冷却，服务端最终校验；remaining-time是验证码有效期 |
| POST /api/auth/logout | 退出并清本地会话 |
| GET /api/user/profile | 校验会话并展示用户名 |
| POST /api/user/seckill | tierId、quantity（1–5）、requestKey（最长64）；提交前持久化编号，超时沿用原编号；保留失败data以区分UNKNOWN与REJECTED |
| GET /api/user/seckill/requests/by-key | requestKey；按当前用户查原申请，UNKNOWN不代表失败，有orderId才进入已建单页面 |
| GET /api/user/orders/page | page、size=6、可选orderStatus |
| GET /api/user/orders/{id} | 当前用户订单，使用服务端金额与付款期限 |
| PUT /api/user/orders/{id}/cancel | 申请取消后重新查状态，4为确认中，2才是已取消 |
| POST /api/user/payments/create | orderId、payType=1；新增paymentPageHtml明确HTML表单含义，保留同内容的旧qrCode，不作为图片src |
| GET /api/user/payments/status/{paymentNo} | 当前用户支付记录并同步渠道；成功后再查订单 |

鉴权头沿用ticket-token，只发往同源/api。业务401和HTTP401仅在当前令牌仍等于请求时令牌时清除登录，避免旧响应覆盖新会话。密码不持久化。

订单0待支付、1已支付、2已取消、3已完成、4取消确认中；支付0待支付、1支付中、2成功、3失败、4关闭、5退款。按北京时间解析无时区付款期限，本地倒计时结束只禁用支付，不推断取消完成。

轮询串行退避，离开页面取消请求，隐藏页面暂停，两分钟后手动继续；重启阻止旧响应覆盖。SDK HTML不注入页面、不执行脚本，仅提取HTTPS沙箱域名openapi-sandbox.dl.alipaydev.com／openapi.alipaydev.com的/gateway.do POST隐藏字段，在独立窗口重建表单。现有同步return仍返回JSON，父页面查询后端确认，无需改真实回调。

## 个人中心与智能客服（第三阶段）

页面：/account/profile（/account重定向到此）、/account/security、/account/addresses、/account/refunds、/support；均受登录保护，订单页共用个人中心导航。

| 方法与路径 | 页面用途及约束 |
|---|---|
| GET /api/user/profile | 读取username、email、phone、nickname、avatar、realName、gender、birthday；原DTO不返回id，不用它覆盖会话ID |
| PUT /api/user/profile | 编辑username、nickname、phone、realName、gender、birthday；页面登录邮箱只读，不发送身份／角色／状态字段。新增具名UserMapper.updateProfile只更新资料列，COALESCE保留未提交值；保留原事务与唯一性检查，不写回密码／状态／证件快照 |
| PUT /api/user/password | 原接口RequestParam不变，oldPassword与newPassword放application/x-www-form-urlencoded请求体，不放URL。Service新密码长度6–20与登录保持一致；前端确认密码只本地校验，密码不持久化 |
| GET /api/user/address | 当前用户地址列表，沿用VO的fullAddress与isDefault |
| POST /api/user/address | receiverName、receiverPhone、province、city、district、detailAddress、postalCode；省市区文本填写，空可选邮编省略；不传JsonIgnore的id／userId／isDefault |
| PUT /api/user/address/{id} | 编辑地址；默认标记未提交时保留既有值，不默认为0；先验证所有权 |
| POST /api/user/address/{id}/default | 独立设置默认，成功后重读地址列表 |
| DELETE /api/user/address/{id} | 仅能删除本人非默认地址，页面弹窗确认；失败保留原列表 |
| GET /api/user/refunds | 当前用户最近100条，原Map使用refund_no、payment_no、order_id、amount、reason、status、created_at、updated_at；前端解码补充_id结尾的长整数保留为字符串，时间兼容字符串与数组 |
| POST /api/user/ai-customer-service/chat | message非空且最多2000字；按当前Sa-Token用户显式传ChatMemory.CONVERSATION_ID=user_ID，不接受客户端指定他人会话；60秒超时、离开页面取消，无自动重发 |
| DELETE /api/user/ai-customer-service/clear-history | 直接清除当前用户ChatMemory，不向模型发送clear、不影响其他用户。成功后清页面消息，失败保留 |

退款状态：REQUESTED待退款、PROCESSING处理中、UNKNOWN结果确认中、RETRYABLE_FAILED等待重试、MANUAL_REQUIRED待人工、SUCCESS成功，未知新状态显示“状态待确认”。原用户端仅能查记录，未增加申请退款、管理员重试或订单退款规则。

客服只注册现有查询工具（演出／场次／本人资料／地址／订单／购票资格），排除资料和地址写入，不注册模型代下单／取消。固定系统提示与工具能力一致，回复以纯文本渲染，无v-html、Markdown执行或外部链接注入。无历史GET接口，页面只显示当前打开期间的消息，不写浏览器持久存储；服务器沿用内存记忆，重启不保留。内存隔离实现参考Spring AI官方Chat Memory文档：https://docs.spring.io/spring-ai/reference/api/chat-memory.html。

本次预览后端改用8081（8080被另一个Docker应用占用），5173已通过API_PROXY_TARGET=http://127.0.0.1:8081代理；项目默认端口配置未修改。仍关闭原库交易、自动迁移和消费者。


## 第四阶段联调补充

2026-10-27客服阶段1升级：HTTP请求及回复字符串契约保持不变，Controller委托独立Agent Service；现开放13个只读工具，补齐演出场次、票档价格、实时库存、本人订单详情与分页筛选、购票申请状态。工具使用服务端ToolContext身份、统一区分空数据/条件拒绝/系统异常，个人字段经裁剪脱敏。当前仍为内存上下文，未新增多会话、流式或交易执行接口，详见[Agent设计](agent-design.md)。

AI聊天成功响应明确为{code:1,msg:"回复成功",data:"回复正文"}。原Result.success(String)只设置msg，现已修正，前端继续只读取data，不兼容吞掉错误契约。完整浏览器联调验证所有业务API真实透传，模型／资金／邮件仍在隔离后端替换。

登录页离开或登录会话改变后，迟到响应不写会话；收银台离开后迟到响应只保存原用户流水，不提交沙箱表单。此为前端生命周期处理，没有修改接口参数或支付状态机。最新页面／接口总表见frontend-acceptance.md。
