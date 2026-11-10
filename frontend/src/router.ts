import { createRouter, createWebHistory } from 'vue-router'
import { readSession } from './utils/session'
import { isAdminRole } from './admin/auth'

export const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/admin/login',
            component: () => import('./admin/pages/AdminLoginPage.vue'),
            meta: { title: '管理员登录 · 有戏', admin: true },
        },
        {
            path: '/admin',
            component: () => import('./admin/AdminLayout.vue'),
            meta: { admin: true, requiresAdmin: true },
            children: [
                { path: '', redirect: '/admin/dashboard' },
                {
                    path: 'dashboard',
                    component: () => import('./admin/pages/AdminDashboardPage.vue'),
                    meta: { title: '工作台 · 有戏管理' },
                },
                {
                    path: 'events',
                    component: () => import('./admin/pages/AdminEventsPage.vue'),
                    meta: { title: '演出管理 · 有戏管理' },
                },
                {
                    path: 'events/new',
                    component: () => import('./admin/pages/AdminEventEditorPage.vue'),
                    meta: { title: '新增演出 · 有戏管理' },
                },
                {
                    path: 'events/:id/edit',
                    component: () => import('./admin/pages/AdminEventEditorPage.vue'),
                    meta: { title: '编辑演出 · 有戏管理' },
                },
                {
                    path: 'events/:id',
                    component: () => import('./admin/pages/AdminEventDetailPage.vue'),
                    meta: { title: '演出详情 · 有戏管理' },
                },
                {
                    path: 'sessions',
                    component: () => import('./admin/pages/AdminSessionsPage.vue'),
                    meta: { title: '场次管理 · 有戏管理' },
                },
                {
                    path: 'ticket-tiers',
                    component: () => import('./admin/pages/AdminTiersPage.vue'),
                    meta: { title: '票档与库存 · 有戏管理' },
                },
                {
                    path: 'cities',
                    component: () => import('./admin/pages/AdminReferencePage.vue'),
                    meta: { title: '城市管理 · 有戏管理' },
                },
                {
                    path: 'categories',
                    component: () => import('./admin/pages/AdminReferencePage.vue'),
                    meta: { title: '分类管理 · 有戏管理' },
                },
                {
                    path: 'users',
                    component: () => import('./admin/pages/AdminUsersPage.vue'),
                    meta: { title: '用户管理 · 有戏管理' },
                },
                {
                    path: 'orders',
                    component: () => import('./admin/pages/AdminOrdersPage.vue'),
                    meta: { title: '订单管理 · 有戏管理' },
                },
                {
                    path: 'orders/:id',
                    component: () => import('./admin/pages/AdminOrderDetailPage.vue'),
                    meta: { title: '订单详情 · 有戏管理' },
                },
                {
                    path: 'refunds',
                    component: () => import('./admin/pages/AdminRefundsPage.vue'),
                    meta: { title: '退款管理 · 有戏管理' },
                },
                {
                    path: 'maintenance',
                    component: () => import('./admin/pages/AdminMaintenancePage.vue'),
                    meta: { title: '交易维护 · 有戏管理' },
                },
            ],
        },
        {
            path: '/payment/return',
            component: () => import('./pages/PaymentReturnPage.vue'),
            meta: { title: '付款结果 · 有戏' },
        },
        { path: '/account', redirect: '/account/profile' },
        {
            path: '/account/profile',
            component: () => import('./pages/ProfilePage.vue'),
            meta: { title: '个人资料 · 有戏', requiresAuth: true },
        },
        {
            path: '/account/security',
            component: () => import('./pages/SecurityPage.vue'),
            meta: { title: '账户安全 · 有戏', requiresAuth: true },
        },
        {
            path: '/account/addresses',
            component: () => import('./pages/AddressesPage.vue'),
            meta: { title: '收货地址 · 有戏', requiresAuth: true },
        },
        {
            path: '/account/refunds',
            component: () => import('./pages/RefundsPage.vue'),
            meta: { title: '退款记录 · 有戏', requiresAuth: true },
        },
        {
            path: '/support/knowledge/:entryId',
            component: () => import('./pages/KnowledgePage.vue'),
            meta: { title: '客服参考规则 · 有戏', requiresAuth: true },
        },
        {
            path: '/support',
            component: () => import('./pages/SupportPage.vue'),
            meta: { title: '智能客服 · 有戏', requiresAuth: true },
        },
        {
            path: '/login',
            component: () => import('./pages/LoginPage.vue'),
            meta: { title: '登录 · 有戏' },
        },
        {
            path: '/checkout',
            component: () => import('./pages/CheckoutPage.vue'),
            meta: { title: '确认购票 · 有戏', requiresAuth: true },
        },
        {
            path: '/purchase/result',
            component: () => import('./pages/PurchaseResultPage.vue'),
            meta: { title: '购票结果 · 有戏', requiresAuth: true },
        },
        {
            path: '/orders/:id/pay',
            component: () => import('./pages/PaymentPage.vue'),
            meta: { title: '订单收银台 · 有戏', requiresAuth: true },
        },
        {
            path: '/account/orders',
            component: () => import('./pages/OrdersPage.vue'),
            meta: { title: '我的订单 · 有戏', requiresAuth: true },
        },
        {
            path: '/account/orders/:id',
            component: () => import('./pages/OrderDetailPage.vue'),
            meta: { title: '订单详情 · 有戏', requiresAuth: true },
        },
        {
            path: '/',
            component: () => import('./pages/HomePage.vue'),
            meta: { title: '有戏 · 让喜欢发生在现场' },
        },
        {
            path: '/events',
            component: () => import('./pages/EventsPage.vue'),
            meta: { title: '发现演出 · 有戏' },
        },
        {
            path: '/events/:id',
            component: () => import('./pages/EventDetailPage.vue'),
            meta: { title: '演出详情 · 有戏' },
        },
        {
            path: '/:pathMatch(.*)*',
            component: () => import('./pages/NotFoundPage.vue'),
            meta: { title: '页面未找到 · 有戏' },
        },
    ],
    scrollBehavior(to, from, saved) {
        if (saved) return saved
        if (to.path === from.path) return undefined
        return { top: 0 }
    },
})
router.beforeEach((to) => {
    if (to.meta.requiresAdmin) {
        const session = readSession('admin')
        if (!session?.token || !isAdminRole(session.role))
            return { path: '/admin/login', query: { redirect: to.fullPath } }
        return
    }
    if (to.meta.requiresAuth && !readSession())
        return { path: '/login', query: { redirect: to.fullPath } }
})
router.afterEach((to) => {
    document.title = String(to.meta.title)
})
