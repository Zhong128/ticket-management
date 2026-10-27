# 有戏前端目录与入门阅读指南

2026-10-27补充：现已增加/admin管理端，逐文件说明见[管理端使用与源码导读](admin-guide.md)。App.vue现只选择两端外壳；本文原App顶栏、搜索、城市、页脚逻辑移动至UserLayout.vue，其余用户端学习顺序不变。

适用：有一点 HTML、CSS、JavaScript 基础，希望看懂、修改并讲解本项目。以下按当前代码整理，入口是项目根目录的 frontend/。建议边启动页面、边读一个小文件、边做一个小修改。

## 先认识本项目用到的东西

| 名称 | 在这里负责什么 | 初学时怎么理解 |
|---|---|---|
| HTML | 页面结构 | 标题、图片、输入框、按钮 |
| CSS | 排版与外观 | 颜色、间距、卡片、手机布局 |
| JavaScript | 交互与数据处理 | 点击、请求接口、处理返回结果 |
| TypeScript | 给JavaScript补类型检查 | 提示一个变量有哪些字段、一个函数收什么参数；浏览器最终执行的是构建后的JavaScript |
| Vue 3 | 组件和界面更新 | 数据变化后，模板中的显示自动更新 |
| Vue Router | 地址与页面对应、页面跳转 | /events打开演出列表，/login打开登录页 |
| Pinia | 跨页面共用状态 | 当前登录用户、选择的城市等 |
| Axios | HTTP请求 | 调用Java后端的/api接口 |
| Vite | 开发与打包 | npm run dev启动开发服务，npm run build生成dist |
| lucide-vue-next | 图标 | 搜索、箭头、用户等图标组件 |
| lossless-json | 保留大数字ID精度 | Java long类型ID在前端按字符串保留 |
| Vitest／Playwright | 自动测试 | 前者查小函数，后者操作浏览器验收 |

Vue不会替代三件套基础。本项目使用Vue 3的组合式API与 `<script setup lang="ts">`，阅读教程时选择这一写法。

## 目录全景

```text
frontend/
├── index.html                 浏览器最初加载的HTML，包含#app挂载点
├── package.json               依赖名称、npm命令
├── package-lock.json          锁定依赖版本，通常由npm维护
├── vite.config.ts             Vue构建、开发接口代理、单元测试配置
├── tsconfig.json              TypeScript检查规则
├── playwright.config.ts       常规浏览器测试配置
├── playwright.live.config.ts  隔离真实业务API联调专用配置
├── README.md                  启动、验证与项目说明
├── src/                       主要业务源码，学习重点
│   ├── main.ts                创建Vue应用、注册Pinia和路由、加载CSS
│   ├── App.vue                公共外壳：顶栏、搜索、城市、导航、页脚、页面出口
│   ├── router.ts              URL对应页面、登录拦截、页面标题与滚动
│   ├── styles.css             全局外观、首页和浏览相关样式
│   ├── transaction.css        登录、购票、订单、收银台样式
│   ├── account.css            个人中心和客服样式
│   ├── pages/                 完整页面
│   ├── components/            多个页面复用的界面块
│   ├── api/                   HTTP封装、接口函数、返回数据类型
│   ├── stores/                当前用户、城市和分类等共享状态
│   ├── composables/           复用的Vue交互逻辑
│   └── utils/                 格式化、校验、会话和购票辅助函数
├── tests/                     常规浏览器测试
├── live-tests/                隔离环境完整浏览器联调
├── node_modules/              npm安装的依赖，不作为业务源码阅读或修改
├── dist/                      构建产物，修改源码后重新生成
├── test-results/              常规测试产物，生成后才存在
└── test-results-live/         隔离联调产物，生成后才存在
```

扩展名也有含义：`.vue`是Vue组件，`.ts`是TypeScript代码，`.css`是样式，`.json`是配置或数据。`.test.ts`和`.spec.ts`在这里都是测试文件，不是用户正常打开页面时运行的业务代码。

## pages：每个页面是什么

| 文件 | 地址 | 功能与阅读重点 |
|---|---|---|
| HomePage.vue | / | 首页推荐、主海报、分类区；练习数据列表、计算属性、接口加载 |
| EventsPage.vue | /events | 搜索、城市分类、分页；练习URL参数与筛选条件 |
| EventDetailPage.vue | /events/:id | 演出详情、场次、票档与数量；练习选择状态和关联请求 |
| LoginPage.vue | /login | 密码登录、验证码注册、重发、登录回跳 |
| CheckoutPage.vue | /checkout | 核对购票信息、须知、提交申请；管理原申请编号 |
| PurchaseResultPage.vue | /purchase/result | 轮询申请，处理异步建单、失败与恢复 |
| OrdersPage.vue | /account/orders | 订单列表、状态筛选、分页 |
| OrderDetailPage.vue | /account/orders/:id | 单个订单详情、继续付款、取消入口 |
| PaymentPage.vue | /orders/:id/pay | 沙箱支付窗口、倒计时、付款状态确认 |
| PaymentReturnPage.vue | /payment/return | 支付回跳后查询本人真实结果；不会信任URL里的成功提示 |
| ProfilePage.vue | /account/profile | 加载资料、编辑表单、提交保存，适合学习表单流程 |
| SecurityPage.vue | /account/security | 旧密码、新密码及确认校验 |
| AddressesPage.vue | /account/addresses | 地址列表、新增编辑弹窗、默认地址、删除 |
| RefundsPage.vue | /account/refunds | 退款记录及状态筛选，只提供查询 |
| SupportPage.vue | /support | 客服状态、对话、清空；当前完整运行版显示维护提示 |
| NotFoundPage.vue | 未匹配地址 | 找不到页面时的提示 |

`:id`表示路径参数。例如 `/events/123` 中的123，就是要查询的演出ID。文件名本身不决定地址，对应关系写在router.ts。

## components：公共界面块

| 文件 | 功能 |
|---|---|
| EventCard.vue | 一张演出卡片：封面、标题、地点、时间、价格、跳转 |
| PosterImage.vue | 海报加载与失败占位 |
| CategoryShortcuts.vue | 首页“文字＋图案”分类导航，点击时带城市和分类参数 |
| CategoryIcon.vue | 分类对应的图案 |
| CategoryFloor.vue | 首页一整块分类推荐区域 |
| LoadState.vue | 共用的加载骨架、错误、空结果和重试入口 |
| AccountMenu.vue | 顶部账户下拉，处理鼠标、触摸、键盘和退出入口 |
| AccountNav.vue | 个人中心栏目导航，手机上保证当前栏目可见 |
| SaleSteps.vue | 购票步骤提示 |
| OrderSummary.vue | 共用的订单／购票摘要 |
| OrderStatus.vue | 订单状态显示 |
| CancelOrderButton.vue | 取消确认与取消请求 |

“页面”通常组织整个屏幕、获取需要的数据；“组件”负责其中一块。比如HomePage决定显示哪些演出，EventCard负责把一条演出画成卡片。公共组件的修改可能影响多个页面。

## api、stores、utils：数据和逻辑放哪里

| 文件 | 用途 |
|---|---|
| api/client.ts | 请求公共入口：/api前缀、Token、超时、错误、失效会话、大数字ID解析 |
| api/browse.ts | 城市、分类、推荐、演出列表详情、场次、票档接口 |
| api/trade.ts | 登录注册、抢票申请、订单、取消、支付接口 |
| api/account.ts | 资料、密码、地址、退款、客服及运行状态接口 |
| api/types.ts | 浏览数据结构：Event、City、Category、Session、Tier、分页等 |
| api/tradeTypes.ts | 登录、购票申请、订单和支付数据结构 |
| api/accountTypes.ts | 资料、地址、退款数据结构 |
| stores/auth.ts | 当前登录用户、接受登录结果、退出和清除会话 |
| stores/catalog.ts | 多页面共用的城市、分类与当前城市选择 |
| composables/usePoll.ts | 定时重复查状态、停止、重试、页面离开后取消；用于异步交易 |
| utils/format.ts | 日期、金额、是否未结束、售票状态 |
| utils/account.ts | 资料与地址提交字段整理、密码校验、退款和时间显示 |
| utils/session.ts | sessionStorage中读写登录会话 |
| utils/purchase.ts | 原购票申请编号、按用户保存恢复、登录回跳地址校验 |
| utils/payment.ts | 安全提取并提交沙箱隐藏表单，不执行返回HTML中的脚本 |

types文件可以先类比Java DTO：描述字段是什么，帮助检查写错字段和类型；它不会代替后端校验。stores可以理解为页面共同使用的数据区，但只有代码显式写入storage的部分才会在刷新后保留。

本项目会话存sessionStorage，城市选择和原购票申请等存localStorage；最终权限、库存、订单和支付状态都由后端决定。前端不直接连接MySQL，过程是“浏览器→HTTP接口→Java后端→数据库”。

## 一次页面显示是怎样发生的

先沿下面顺序认识入口：

```text
index.html 中的 <div id="app">
  → main.ts 创建应用并挂到#app
  → App.vue 显示公共顶栏、页脚以及RouterView
  → router.ts 根据地址选择一个pages页面
  → 页面调用api函数，拿到后端数据
  → 页面把数据交给components绘制
```

以首页演出卡片为例，第二遍追这条数据线：

```text
HomePage.vue：browse.events(...) 请求演出
  → api/browse.ts：/public/events/page
  → api/client.ts：加上/api前缀，通过Axios发请求
  → Vite代理（开发时）或同源后端（打包版）
  → 后端返回 { code, msg, data }
  → client.ts检查code并返回data
  → HomePage.vue取page.records，写入events.value
  → 模板把单个event传给EventCard.vue
  → {{ event.name }}、金额和海报显示到页面
```

所以，想改“卡片长什么样”先找EventCard和CSS；想改“取哪些数据”先找页面和api；想改“后端到底返回什么”还要看Controller、Service、Mapper。先区分这三件事，会少绕很多路。

## 如何读懂一个.vue文件

通常包含两部分，本项目的CSS多数单独放在三个全局CSS文件中：

```vue
<script setup lang="ts">
// 导入、变量、函数：页面的数据和行为。
</script>

<template>
    <!-- 类似HTML，添加Vue的数据绑定和事件语法。 -->
</template>
```

先读template，知道页面有哪些块，再回script找这些变量和函数。遇到class就去对应CSS搜索这个类名。下面的Vue写法优先认熟：

| 写法 | 含义 | 本项目例子 |
|---|---|---|
| `{{ value }}` | 把变量显示为文本 | EventCard显示event.name |
| `:src="value"` | 把表达式绑定到属性，冒号是v-bind简写 | 海报图片地址 |
| `@click="save"` | 点击时运行函数，@是v-on简写 | 表单保存、取消、搜索 |
| `v-if`／`v-else` | 满足条件才显示对应内容 | 价格为空、错误、维护提示 |
| `v-for`＋`:key` | 循环显示，key帮助Vue辨认每一项 | 分类和演出列表 |
| `v-model` | 输入框和变量同步 | 搜索、资料、登录输入框 |
| `defineProps` | 声明父页面传给组件的数据 | EventCard接收event |
| `defineEmits`／`$emit` | 子组件通知父组件 | LoadState发出retry |
| `ref` | 会触发界面更新的单个状态 | 加载中、错误、演出数组 |
| `reactive` | 把对象作为响应式状态 | ProfilePage的form对象 |
| `computed` | 从已有状态算出一个值 | 首页分类分组 |
| `watch` | 某个值变化时执行逻辑 | 换城市后重新请求首页 |
| `onMounted`／`onUnmounted` | 页面挂载／离开时执行逻辑 | 加载资料、取消未结束请求 |
| `RouterLink`／`RouterView` | 页面跳转／当前页面出口 | 卡片跳详情、App放置页面 |

例如 `const loading = ref(true)`：在script里通过 `loading.value = false`修改；在template中一般直接写 `loading`，不用.value。普通 `const`变量和Vue的ref不是一回事。

TypeScript先看懂 `string`、`number`、`boolean`、数组、`interface`和可选字段 `?`。例如 `ref<Event[]>([])`表示“初始空数组，之后装演出对象”；`get<Page<Event>>()`说明接口返回“演出分页对象”。先把类型当阅读提示，复杂泛型可以稍后学习。

## 适合你的实际学习顺序

### 第一步：补能直接读项目的三件套

HTML先掌握语义标签、链接图片、表单input/button；CSS先掌握选择器、盒模型、margin/padding、Flex、Grid、媒体查询；JavaScript先掌握const/let、对象数组、函数、import/export、解构、模板字符串、map/filter/find、Promise与async/await、try/catch。

练习：用普通HTML/CSS手写一张演出卡片，有海报、标题、时间、价格和按钮。用数组保存三条演出数据，尝试遍历处理。CSS目标是自己解释“为什么卡片横着排、为什么手机会换行”，不用先学复杂动效。

### 第二步：读最小的真实组件

顺序：EventCard.vue → PosterImage.vue → CategoryShortcuts.vue → LoadState.vue。

第一次只回答四个问题：拿到了什么数据、显示了什么、点击做什么、样式在哪里。然后在5173开发页面把“起”改成“起售”、调一下标题间距，观察变化，最后撤回练习修改。不要一开始从App.vue或支付页逐行读完整项目。

### 第三步：认识入口与Vue状态

读index.html → main.ts → router.ts → App.vue的template。再回到简单组件，认识插值、属性、条件、循环、事件、props。

理解ref、reactive、computed后，再读HomePage的events、featured与categoryGroups。watch中的取消请求可以先标记为“换条件时避免旧结果覆盖”，第二遍再细读。

### 第四步：完整追踪一次查数据

读HomePage → api/browse.ts → api/client.ts → api/types.ts → EventCard。浏览器按F12，在Network中筛选Fetch/XHR，刷新页面找到events/page，对照请求参数、Response中的records以及卡片字段。client.ts的普通请求先读懂，大ID与迟到401防护留到第二遍。

练习：找到一条演出的name、minPrice、coverImage在接口和页面中分别在哪里。回答“data有数据但页面不显示”时，应该检查返回结构、筛选条件还是绑定字段。

### 第五步：表单、路由与共享状态

先读ProfilePage的“加载→填表→提交→提示”，学习v-model和表单校验；再读EventsPage的筛选与URL同步；最后读LoginPage、auth.ts、session.ts与router.beforeEach。此时再理解“为什么刷新后登录还在”“为什么游客被带去登录页”“切换城市为什么影响多个页面”。

练习优先改提示文字、排列顺序或必填反馈，不随意改接口字段和业务校验。修改真实资料会保存到本地库，使用自己的演示账号。

### 第六步：读完整交易与测试

读EventDetailPage → CheckoutPage → PurchaseResultPage → OrdersPage／OrderDetailPage → PaymentPage／PaymentReturnPage，同时看trade.ts、purchase.ts、usePoll.ts和payment.ts。

这时重点理解：提交成功为什么还要等建单、断网为什么复用原requestKey、支付回跳为什么不等于支付成功、离开页面为什么要取消请求。这些牵涉前后端协作，适合有前面基础后再学。

最后读utils/format.test.ts等小测试，再读tests/browse.spec.ts理解浏览器断言。tests中的部分接口是受控响应；live-tests/purchase-flow.spec.ts由Java隔离测试启动，以真实业务API联调，外部渠道在测试后端替换。

## 学习时怎样启动和检查

1. 点击桌面“有戏-启动”，等后端和中间件就绪。
2. 在IDEA终端、项目根目录执行：

```powershell
cd frontend
npm run dev
```

3. 打开终端实际显示的开发地址，通常为http://127.0.0.1:5173。保存src文件后页面通常自动更新。
4. 使用F12的Elements查布局，Console看错误，Network看接口。
5. 修改完执行 `npm run build` 检查类型和构建；小工具函数可执行 `npm test`。

开发代理未设置API_PROXY_TARGET时读取本地运行记录中的后端端口，目前为8083。已有环境变量会优先覆盖；后端换端口后重启Vite。若5173被占用，以开发终端地址为准，别把旧开发服务当成这次启动的服务。

8083打包版不会随着src保存自动更新。学习改界面用Vite开发地址；想让打包版也更新，需要用 `scripts/start-local.ps1 -Restart`重新构建并启动。dist是生成结果，别直接在那里练习修改。只改静态卡片不一定需要后端；运行本项目真实列表、登录和交易需要后端接口。

桌面“有戏-关闭”停止后台JAR和独立中间件；手动启动的Vite在对应终端按Ctrl+C结束。sessionStorage/localStorage、密码、Token与日志不要复制进提交或学习截图。

## 学到什么程度算真正看懂

第一轮不要求逐行解释所有代码，先能自己做完下面这些事：

- 找到并修改一张演出卡片的文字、样式。
- 从某个URL定位它对应的页面文件。
- 从页面定位接口函数，再定位返回字段类型。
- 用Network确认接口到底成功还是失败。
- 解释一个列表为什么显示加载、错误、空结果三种状态。
- 解释props传数据、ref存状态、computed算结果的区别。
- 从确认购票一路追到订单，说明“前端提示”和“服务端确认”的区别。

建议第一轮目标设成“能改卡片＋能追通一个查询接口”。这比一开始背完整框架API，更容易和眼前的项目建立联系。

## 配合阅读的官方资料

- [MDN Web入门](https://developer.mozilla.org/zh-CN/docs/Learn_web_development)：补HTML、CSS、JavaScript。
- [Vue中文教程](https://cn.vuejs.org/tutorial/)：选择组合式API，边做边认识绑定与事件。
- [Vue组件基础](https://cn.vuejs.org/guide/essentials/component-basics.html)、[响应式基础](https://cn.vuejs.org/guide/essentials/reactivity-fundamentals.html)、[计算属性](https://cn.vuejs.org/guide/essentials/computed.html)：对应本项目组件和状态。

本指南已对照当前文件与Vue官方文档整理；目录新增或重命名后同步更新本指南。
