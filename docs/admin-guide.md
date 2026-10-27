# 管理端使用与源码导读

管理端与用户端共用一键启动。点击桌面“有戏-启动”，等待就绪后，在脚本实际地址后加 `/admin`，例如 http://127.0.0.1:8083/admin 。本机端口可能因其他软件占用自动改变，以启动窗口为准。

## 第一次登录

后台使用管理员用户名和密码，不能用普通邮箱注册自动获得管理权限。已有管理员直接登录。

若独立运行库没有管理员，先完成启动构建，再在项目根目录**显式执行一次**：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-local-admin.ps1 -Username youxi_admin
```

工具只允许本机3308端口的youxi_local，拒绝修改已有用户名；随机密码写入被Git忽略的 `output/local-admin-youxi_admin.txt`，不打印到聊天或日志。不由启动脚本自动执行，不提权原有用户。本轮交付没有自动创建本机管理员；隔离测试已验证创建、BCrypt和主键回填。

打开该本地文件查看账号密码后登录。文件不要提交或分享。如果用户名或同名凭据文件已存在，工具会拒绝覆盖，应使用已有账号或明确选择另一个新用户名。

## 推荐管理顺序

1. 城市和分类：先配置演出的基础选项，按需要启用、排序或设置热门城市。
2. 新增演出：填写名称、城市分类、场馆、时间及海报链接。描述按纯文本保存。演出状态沿用现有后端定义。
3. 新增场次：选择演出、场次名称和时间；时间需处于演出起止区间，不能与同演出场次冲突。
4. 新增票档：选择该演出下的场次、原价现价、库存及状态。
5. 查看用户端，确认演出、场次和价格与配置一致。下架后仍可在管理端查询恢复。

工作台显示真实汇总与近期排期；库存统计是占用口径，不能当作实收销售额。订单、退款和交易维护请先阅读影响说明；管理页连接真实本地业务库，点击取消、库存调整、重试或维护会实际影响交易，不能把它们当展示按钮。

退款页面只处理后端已有退款任务；不提供任意人工退款。用户管理仅编辑普通用户资料和状态，管理员账号只读，不提供授予角色或重置密码。AI仍保持维护状态。

## 代码位置

```text
frontend/src/
├── App.vue                    根据路由选择后台或用户端外壳
├── UserLayout.vue             原用户端的顶栏、搜索、城市与页脚
├── router.ts                  包含/admin子路由及两端登录守卫
├── api/client.ts              共用请求解析，按user/admin选择Token与失效事件
├── utils/session.ts           两端独立sessionStorage键
└── admin/
    ├── AdminLayout.vue        后台侧栏、移动菜单、运行状态和页面出口
    ├── admin.css              以admin命名空间限定的全局后台样式
    ├── api.ts                 固定使用管理端会话的请求函数
    ├── auth.ts                后台Pinia状态及角色判断
    ├── components/
    │   ├── AdminModal.vue     原生dialog、焦点、Esc与忙碌关闭保护
    │   └── AdminPager.vue     通用分页
    └── pages/
        ├── AdminLoginPage.vue        登录与服务端角色核验
        ├── AdminDashboardPage.vue    真实汇总、排期、演出资料
        ├── AdminEventsPage.vue       演出列表
        ├── AdminEventEditorPage.vue  新增/编辑长表单
        ├── AdminEventDetailPage.vue  演出及关联场次
        ├── AdminSessionsPage.vue     场次配置
        ├── AdminTiersPage.vue        票档与受限库存调整
        ├── AdminReferencePage.vue    城市/分类共用管理页
        ├── AdminUsersPage.vue        用户资料与账户状态
        ├── AdminOrdersPage.vue       订单筛选与批量取消
        ├── AdminOrderDetailPage.vue  只读交易信息、备注、取消
        ├── AdminRefundsPage.vue      退款查询与人工任务重试
        ├── AdminMaintenancePage.vue  补偿和超时关单
        └── contentSupport.ts         查询生命周期、选项分页与显示辅助
```

适合入门的阅读顺序：AdminPager → AdminModal → AdminReferencePage → AdminEventsPage → api.ts → auth.ts与AdminLayout，最后读订单及库存处理。原[前端入门指南](frontend-learning-guide.md)仍适用，App原有完整外壳现移动至UserLayout。

学习修改界面仍使用frontend目录的npm run dev，保存会更新开发页面；打包版需要重新构建。关闭项目和中间件仍使用桌面“有戏-关闭”，手动启动的Vite在终端Ctrl+C结束。

完整接口和规则见[管理端接口说明](admin-api.md)，实际通过及未执行验收见[管理端验收记录](admin-validation.md)。
