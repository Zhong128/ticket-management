# 管理端接口与页面

日期：2026-10-27。后台位于同一前端的 `/admin`，业务接口统一以 `/api` 开头。延续 `{code,msg,data}` 响应和PageResult分页结构。管理Token使用ticket-token请求头，浏览器会话单独保存为youxi-admin-auth；用户端仍为youxi-auth。

## 页面与接口

| 页面 | 查询与写入接口 |
|---|---|
| /admin/login | POST /auth/admin/login；GET /auth/userinfo核验role；POST /auth/logout |
| /admin/dashboard | GET /admin/dashboard/summary、/admin/sessions/page、/admin/events/query |
| /admin/events、/new、/:id、/:id/edit | GET /admin/events/query；GET/PUT/DELETE /admin/events/{id}；POST /admin/events |
| /admin/sessions | GET /admin/sessions/page；POST /admin/sessions；GET/PUT/DELETE /admin/sessions/{id} |
| /admin/ticket-tiers | GET /admin/ticket-tiers/page、/stock-statistics、/sold-out；POST /admin/ticket-tiers；GET/PUT/DELETE /admin/ticket-tiers/{id}；POST /admin/ticket-tiers/{id}/stock-adjustments |
| /admin/cities | GET /admin/cities/query；城市增改查删接口 |
| /admin/categories | GET /admin/categories/query；分类增改查删接口 |
| /admin/users | GET /admin/user、/admin/user/{id}；PUT资料、状态与批量状态；DELETE单个及批量 |
| /admin/orders、/:id | GET /admin/orders/page、/{id}；PUT /admin/orders/{id}；PUT /admin/orders/batch-cancel |
| /admin/refunds | GET /admin/refunds/page；POST /admin/refunds/{refundNo}/retry |
| /admin/maintenance | POST /admin/payments/compensation?waitingMinutes=10；POST /admin/payments/timeout-close?timeoutMinutes=35；GET /public/app-status |

## 新增查询契约

`GET /admin/sessions/page`：page、size、eventId、status、eventStatus、startTime、endTime、ascending。时间使用ISO本地日期时间，含查询结束边界；ascending默认false，true按场次时间及id升序。status为场次状态，eventStatus为父演出状态，省略则包括下架内容。分页返回EventSessionVO，含eventName、artistName、venue。工作台传status=1、eventStatus=1、ascending=true；管理列表保留下架条目。

`GET /admin/refunds/page`：page、size、refundNo、orderNo、status，筛选值精确匹配。返回兼容分页，记录沿用原退款字段snake_case，如refund_no、order_id、payment_no、status、amount。原GET /admin/refunds最近100条保持兼容。只有MANUAL_REQUIRED可重新入队；返回false表示未受理，不显示成功。

两个分页接口限制size不超过200，并校验分页偏移溢出。SQL使用参数绑定，不能传任意SQL或排序片段。

`GET /admin/dashboard/summary`返回：

```json
{
  "code": 1,
  "data": {
    "onSaleEventCount": 0,
    "upcomingSessionCount": 0,
    "pendingOrderCount": 0,
    "refundAttentionCount": 0,
    "asOf": "2026-10-27T12:00:00"
  }
}
```

汇总基于项目Clock同一时刻：在售演出status=1、开售<=当前<停售、演出尚未结束；场次为当前至7天后（不含上界）且场次和父演出均启用；待支付订单status=0；人工退款status=MANUAL_REQUIRED。数据不是营收，不将库存占用当已付款销量。

## 权限和操作语义

- SUPER_ADMIN继承ADMIN和USER；ADMIN继承USER。后端逐请求查角色，前端路由守卫只负责体验，不替代鉴权。
- 管理端用户详情返回安全UserVO，含role/status，不返回密码。资料编辑只接收UserProfileDTO允许字段，password、role、status、id和未知字段直接拒绝。状态走独立接口。
- ADMIN、SUPER_ADMIN账号只读，编辑、启停、删除及混入批量操作均由Service与SQL保护。批量先检查整批对象。
- 管理资料与状态使用具名列更新；用户改密也已改用密码专用Mapper，避免旧快照覆盖管理员并发禁用。
- 原Sa-Token配置is-share=true：同一账号若两端拿到相同服务端Token，注销会令同一Token失效；浏览器两端存储隔离不等于两个独立服务端登录域。未为本次页面建设改变全局登录策略。
- 演出、场次、票档DELETE为下架保留历史；城市分类删除按后端引用约束处理，失败留在弹窗。
- 已有票档PUT不提交库存字段；库存调整走delta接口，须先下架并且无未结预占/订单。请求结果不明时核实后重开，不自动重发。
- 售罄筛选调用既有sold-out接口，固定启用状态，再按票价过滤并按10条前端分页；退出恢复原状态筛选。售罄行编辑仍先GET完整票档详情，不假造原价或所属关系。
- 订单只改备注或进入取消流程，金额/数量/付款状态只读；状态4显示取消确认中。批量取消重新读取服务端状态。
- 交易关闭或状态未确认时，退款重试、取消及维护等交易操作不可执行。补偿和关单仅表示已受理，没有伪造任务日志或完成状态。

## 迁移与兼容

新增V6__admin_role_capacity.sql：user.role由VARCHAR(10)扩至VARCHAR(20)，保留默认USER和可空语义，解决SUPER_ADMIN存储长度不足。既有迁移未改。应用自动迁移仍关闭；本地启动脚本只对独立3308/youxi_local显式执行最新迁移，原3306/ticket不迁移。无新增运行或测试依赖。
