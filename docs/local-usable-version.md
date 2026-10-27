# 完整本地运行版

更新：2026-10-27。用户端与/admin管理端已接真实业务接口。默认尝试8082，被占用时自动选择8083至8092中的空闲端口；以脚本最终显示或自动打开的地址为准。本机此前因QQ.exe占用8082使用8083，实际地址可能变化。前端随Spring Boot JAR同源提供，不需要单独启动Vite。客服按用户要求显示“功能维护中”。后台账号与使用方式见[管理端指南](admin-guide.md)。

## 启动与停止

本机需要 Java 21、Node.js 22.12+、Maven 和已运行的 Docker Desktop。已有 `ticket-management/config/application-local.properties` 提供邮件和支付宝沙箱配置；该文件不能提交。首次启动需要下载依赖与容器镜像。

在项目根目录双击 `start-local.cmd`，或执行：

桌面已提供“有戏-启动”和“有戏-关闭”快捷方式，分别指向项目根目录的start-local.cmd／stop-local.cmd。启动会构建、启动独立中间件及后端并打开页面；关闭会停止本项目后端和三个独立中间件，保留数据。Docker Desktop本身及其他项目不关闭。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-local.ps1
```

修改代码后重新构建并重启：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Restart
```

已经构建过且代码未改变时，可加 `-NoBuild`。已运行时重复启动会返回现有地址；双击入口仍打开浏览器。默认端口冲突时自动在8082至8092内找空闲端口，不会停止其他软件；显式指定 `-AppPort` 时仍严格使用该端口，冲突则报错。检查脚本默认读取application.json中的实际端口，也可显式指定。

停止本运行版及其三个中间件，保留持久卷：

可直接双击桌面的“有戏-关闭”，或项目根目录的stop-local.cmd，也可执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-local.ps1
```

仅停止应用可加 `-KeepServices`。停止脚本核对应用标记与 JAR 路径，拒绝停止被其他进程复用的 PID。启动脚本已在 Windows PowerShell 5.1 验证，外部命令以退出码判断成功，Docker 的进度输出不会被误判为失败。

## 数据与配置

| 服务 | 本机端口 | 说明 |
|---|---|---|
| 完整应用 | 8082 | 仅监听回环地址；交易与消费者开启，自动迁移关闭 |
| MySQL | 3308 | 独立 `youxi_local` 库，显式迁移至 V6（扩宽角色列） |
| Redis | 6381 | 独立配额与会话 |
| RabbitMQ | 5673／15673 | 独立消息与管理端口、独立虚拟主机 |

Compose 项目与容器均为 `youxi-local` 前缀，使用独立命名卷。原 `3306/ticket` 库及原中间件卷没有被迁移、清空或接入本运行版。新库导入61条演出，原用户与订单不复制。再次启动按名称跳过已有演出；实测重启后仍是61条，本次导入0条。

封面与演出来源见 `ticket-management/src/main/resources/demo/catalog-2026-10.json`。排期、价格和库存用于演示，不代表官方售票信息。远程封面失败时有标题占位。

随机中间件凭据在被忽略的 `ticket-management/.env.youxi-local`，不要删除或改写后再连接已有卷。应用、日志、导入清单和本地验收账号在被忽略的 `output/youxi-local/`。验收账号文件 `acceptance-account.json` 含邮箱、密码及 Token，仅供本机查看，不能提交或分享。

重启和停止均不是备份。需要备份时，对 **youxi-local-mysql 的 youxi_local 库**使用 mysqldump，并安全保存对应凭据；不要用原库或原数据卷做恢复练习。Redis／RabbitMQ 持久卷也需一并保留以便未完成交易继续恢复。本轮没有执行删除卷或恢复数据库。

## 可用功能与实际验收

- 首页图文分类、城市、搜索、筛选、详情、票档与数量选择。
- 邮箱密码登录、验证码注册、登录回跳；个人资料、改密、地址与默认地址。
- 确认购票、原申请编号恢复、异步建单、订单查询／取消、沙箱收银台、退款记录查询。
- `/payment/return` 查询本人服务端流水与订单，不把 URL 中的“成功”当作付款证据；缺少登录时保留回跳地址。
- 客服维护页面保留订单与演出入口；服务端维护开关阻止模型调用。

经用户明确授权，本轮只在新库对自有账号完成：SMTP 向配置发件邮箱自身发送注册验证码并验证注册、真实异步建单、取消订单、生成支付宝沙箱签名页面、真实沙箱查单。查单返回 `ACQ.TRADE_NOT_EXIST`，表示渠道连通但该流水尚未形成已付款交易；**没有执行买家付款或使用真实资金**。邮件发送成功不等于独立确认邮箱收件箱投递。

真实 AI 最小调用返回 `AllocationQuota.FreeTierOnly`。按用户要求，默认启动写入 `customer-service.enabled=false`，前端显示维护提示；之后未再次调用模型，也未修改付费开关。用户恢复额度并通知后再复测。恢复前不使用 `-EnableCustomerService`；该参数只供后续明确恢复时使用。

本机支付宝通知地址指向回环地址，外部平台不能主动回调到本机；付款状态仍通过服务端主动查单确认。公网隧道、异步回调和本版人工沙箱付款尚未复验。退款记录是查询入口，未增加用户主动申请退款规则；邮箱只读，无头像上传接口。

## 验证证据

截至本次最终验收：前端9项单元、33项常规浏览器检查通过；打包版8082真实接口下8条桌面路由与4条320px路由通过，无页面异常或横向溢出，维护提示正常，重启后本人订单仍可读取。运行检查脚本确认就绪、交易开启、客服关闭与直接刷新页面。

后端96项回归与5项重点隔离集成全部通过（失败0、错误0、跳过0），包括完整浏览器购票、账户操作与静态资源边界，详见[验收记录](frontend-validation.md)。隔离联调中的邮件、支付网关与模型为测试替身，不能代替上述真实渠道结果。测试与截图只保留在本机忽略目录；没有推送或发布。

开发时在 `frontend/` 执行 `npm run dev`，优先使用API_PROXY_TARGET；未指定时读取output/youxi-local/application.json的实际端口，尚无运行记录时回退8082。后端更换端口后需重启Vite。历史5173／4173进程可能仍显式代理8081原库只读预览；体验完整交易请使用启动脚本最终显示的地址。
