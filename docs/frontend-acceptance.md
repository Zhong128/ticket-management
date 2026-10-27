# 用户端整体验收与运行边界

2026-10-27补充：管理端已实施，同源入口/admin；当前最新跨两端回归及V6角色扩宽结果见[管理端验收](admin-validation.md)。下文仍保留用户端阶段历史记录。

日期：2026-10-26。范围：首页至购票、订单、个人中心和客服，不包含管理端。详细过程见[历史验收记录](frontend-validation.md)，完整接口参数见[接口文档](frontend-api.md)。

**当前完整运行入口为8082**，独立库交易已启用，客服维护中。一键启动、数据位置与最新真实渠道结果见[完整本地运行版](local-usable-version.md)。下文三种环境和95／30项测试为第四阶段历史快照，最新回归结果见历史验收记录末尾。

## 页面与接口

| 页面 | 功能 | 主要真实接口（均以/api开头） |
|---|---|---|
| / | 图文分类、城市、推荐、海报 | /public/cities、/public/categories、/public/home/recommend/default、/public/events/page |
| /events | 名称／艺人、城市／分类、分页和URL恢复 | /public/events/page |
| /events/:id | 演出详情、场次票档、数量、停售与售罄 | /public/events/:id、/public/sessions、/public/ticket-tiers/page |
| /login | 邮箱密码登录、验证码注册、重发、回跳 | /auth/user/login-or-register、/auth/user/verify-registration、/public/verification/resend |
| /checkout | 核对信息、勾选须知、持久化原申请编号 | 公开详情／场次／票档、POST /user/seckill |
| /purchase/result | 受理／未知／建单／释放，刷新恢复与原编号重试 | /user/seckill/requests/by-key、POST /user/seckill |
| /orders/:id/pay | 沙箱窗口、到期倒计时、后台确认付款 | /user/orders/:id、/user/payments/create、/user/payments/status/:no |
| /payment/return | 支付回跳、登录恢复、查询本人真实结果 | /user/payments/status/:no、/user/orders/:id |
| /account/orders、/account/orders/:id | 状态筛选、分页、详情、取消与继续支付 | /user/orders/page、/user/orders/:id、PUT /user/orders/:id/cancel |
| /account/profile | 资料编辑与保存 | GET／PUT /user/profile |
| /account/security | 旧密码校验、新密码确认 | PUT /user/password（表单请求体） |
| /account/addresses | 地址增改删、默认地址；首个地址自动默认 | /user/address及/:id、/:id/default |
| /account/refunds | 最近100条自动退款、状态筛选、关联订单 | GET /user/refunds |
| /support | 查询客服、失败重编辑、清空本人上下文 | /user/ai-customer-service/chat、DELETE /user/ai-customer-service/clear-history |
| 全局运行状态 | 就绪、交易与客服维护标志 | GET /public/app-status |

账户下拉提供个人信息／账户设置／订单管理／退出登录；游客菜单提供登录入口。各受保护页面保留登录回跳。个人中心手机横向导航自动显示当前栏目。

## 三种运行环境

| 环境 | 实际行为 |
|---|---|
| 日常预览：5173 → 8081 | 真实本地API与原数据库；交易、自动迁移及消费者关闭。当前8080属于另一项目。没有静态mock回退 |
| 生产构建预览：4173 → 8081 | dist构建结果，验证SPA刷新、懒加载资源、公开API、登录保护与404；不是已发布站点 |
| 隔离完整联调：5183 → Spring随机本机端口 | 测试启动一次性MySQL8、Redis7、RabbitMQ3.12；交易启用，所有业务API真实透传，外部网关／邮件／模型为后端测试替身 |

5183专用配置要求LIVE_ISOLATED_ACCEPTANCE=true及本机随机高端口，不复用用户5173服务。普通账户／交易浏览器测试中的受控API响应与生产页面分离；真实联调仅拦截支付宝沙箱网站页面，禁止拦截业务API。

## 本轮修复

- 手机导航自动把当前栏目滚入可见范围，窗口大小变化后也会重新定位。
- 登录页面离开或会话变化后，迟到的成功响应不再覆盖会话／强制跳转；请求中禁用凭据输入。
- 离开收银台时关闭尚未提交的空白支付窗口，迟到响应只保存原用户的支付编号，不提交外部表单。
- 长订单编号自然换行；通用加载提示不再把资料／订单都称为演出；窄屏客服字数计数保持一行。
- 完整联调发现客服回复被Result.success(String)放入msg，现明确返回Result.success("回复成功", response)，正文在data；接口验收增加正文断言。

## 验证与限制

最终测试数量与结果以[第四阶段验收记录](frontend-validation.md)为准。本轮9项前端单元、30项常规浏览器测试、95项后端回归与4项重点隔离集成全部通过；4项集成包含1项完整真实浏览器联调与3项HTTP链路验收。独立审查未发现阻断项。测试在Windows＋Edge完成，不声称跨平台浏览器通过。

仍未执行：真实模型额度／可用性、真实邮箱送达、支付宝外部授权及人工沙箱付款、原库交易V5迁移与启用、发布部署、压测。以上不能由隔离测试替代。退款仅只读查询，没有新增用户申请退款规则；邮箱只读、无头像上传。AI页面刷新不取回旧消息，服务器内存重启后不保留历史。

## 重跑

常规前端：在frontend执行npm test、npm run build、npm run test:e2e，要求本地公开API与61条演示数据存在。

完整真实联调：后端模块使用Java21与Docker执行：

```powershell
mvn '-Dtest=TradeApplicationIT#frontendBrowserCompletesRealPurchaseAndAccountJourney' test
```

本机JDK21／Mockito仍需[已有测试启动参数](transaction-hardening.md)，采用显式Mockito agent及项目target临时目录。浏览器与前端npm依赖需先安装，5183必须空闲。测试自己启动／关闭Vite，不要手动给5183接原库运行。普通测试与真实联调的产物分别在被忽略的test-results和test-results-live，日志在output/frontend-live-browser.log和output/frontend-phase4-live-it.log。
