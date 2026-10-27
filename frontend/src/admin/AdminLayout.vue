<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
    Ticket,
    LayoutDashboard,
    CalendarDays,
    MapPin,
    Tags,
    UsersRound,
    ClipboardList,
    WalletCards,
    Wrench,
    Layers3,
    Armchair,
    Menu,
    LogOut,
    ExternalLink,
    X,
} from 'lucide-vue-next'
import { useAdminAuth, isAdminRole } from './auth'
import { adminRequest } from './api'
const auth = useAdminAuth(),
    router = useRouter(),
    route = useRoute()
const navigation = [
    { label: '工作台', path: '/admin/dashboard', icon: LayoutDashboard },
    { label: '演出管理', path: '/admin/events', icon: Ticket },
    { label: '场次管理', path: '/admin/sessions', icon: CalendarDays },
    { label: '票档与库存', path: '/admin/ticket-tiers', icon: Armchair },
    { label: '城市管理', path: '/admin/cities', icon: MapPin },
    { label: '分类管理', path: '/admin/categories', icon: Tags },
    { label: '用户管理', path: '/admin/users', icon: UsersRound },
    { label: '订单管理', path: '/admin/orders', icon: ClipboardList },
    { label: '退款管理', path: '/admin/refunds', icon: WalletCards },
    { label: '交易维护', path: '/admin/maintenance', icon: Wrench },
]
const navDialog = ref<HTMLDialogElement>(),
    leaving = ref(false),
    tradeEnabled = ref<boolean | null>(null)
const controller = new AbortController()
function expired() {
    auth.clear()
    void router.replace({ path: '/admin/login', query: { redirect: route.fullPath } })
}
onMounted(async () => {
    window.addEventListener('youxi-admin-auth-expired', expired)
    await refreshStatus()
})
async function refreshStatus() {
    if (controller.signal.aborted) return
    try {
        const info = await adminRequest<{ role: string }>(
            'get',
            '/auth/userinfo',
            undefined,
            undefined,
            controller.signal,
        )
        if (!controller.signal.aborted && !isAdminRole(info.role)) expired()
    } catch {
        if (!controller.signal.aborted && !auth.loggedIn) expired()
    }
    try {
        const status = await adminRequest<{ tradingEnabled: boolean }>(
            'get',
            '/public/app-status',
            undefined,
            undefined,
            controller.signal,
        )
        if (!controller.signal.aborted) tradeEnabled.value = status.tradingEnabled
    } catch {
        if (!controller.signal.aborted) tradeEnabled.value = null
    }
}
watch(() => route.path, refreshStatus)
onUnmounted(() => {
    controller.abort()
    window.removeEventListener('youxi-admin-auth-expired', expired)
    navDialog.value?.close()
})
async function logout() {
    leaving.value = true
    try {
        await adminRequest('post', '/auth/logout')
    } catch {
        // 网络不可用时仍清理本端会话，重新登录后再继续操作。
    } finally {
        auth.clear()
        leaving.value = false
        void router.replace('/admin/login')
    }
}
</script>
<template>
    <div class="admin-app">
        <a class="admin-skip" href="#admin-main">跳到主要内容</a>
        <aside class="admin-sidebar">
            <RouterLink class="admin-brand" to="/admin/dashboard">
                <Ticket :size="29" />
                <span>
                    有戏
                    <small>演出运营</small>
                </span>
            </RouterLink>
            <nav aria-label="管理端导航">
                <RouterLink
                    v-for="item in navigation"
                    :key="item.path"
                    :to="item.path"
                    :class="{ active: route.path.startsWith(item.path) }"
                >
                    <component :is="item.icon" :size="19" />
                    {{ item.label }}
                </RouterLink>
            </nav>
            <div class="admin-sidebar-bottom">
                <Layers3 :size="18" />
                <span>
                    把喜欢的现场
                    <br />
                    安排妥当
                </span>
            </div>
        </aside>
        <div class="admin-workspace">
            <header class="admin-topbar">
                <div class="admin-actions">
                    <button
                        class="admin-icon-btn admin-menu-toggle"
                        aria-label="打开管理菜单"
                        @click="navDialog?.showModal()"
                    >
                        <Menu :size="22" />
                    </button>
                    <span>演出运营管理</span>
                    <span class="admin-environment">本地工作区</span>
                </div>
                <div class="admin-actions">
                    <span class="admin-status-dot" :class="{ enabled: tradeEnabled }" />
                    <span class="admin-status-label">
                        {{
                            tradeEnabled === null
                                ? '运行状态待确认'
                                : tradeEnabled
                                  ? '交易已开启'
                                  : '交易已关闭'
                        }}
                    </span>
                    <RouterLink class="admin-site-link" to="/" target="_blank" rel="noopener">
                        <ExternalLink :size="15" />
                        查看用户端
                    </RouterLink>
                    <span class="admin-operator">{{ auth.user?.username }}</span>
                    <button
                        class="admin-icon-btn"
                        aria-label="退出管理端"
                        :disabled="leaving"
                        @click="logout"
                    >
                        <LogOut :size="18" />
                    </button>
                </div>
            </header>
            <main id="admin-main" class="admin-main" tabindex="-1">
                <RouterView :key="route.fullPath" />
            </main>
            <footer class="admin-footer">有戏演出运营 · 数据以服务端最新结果为准</footer>
        </div>
        <dialog ref="navDialog" class="admin-mobile-nav" aria-label="管理菜单">
            <div class="admin-modal-heading">
                <strong>有戏 · 演出运营</strong>
                <button
                    class="admin-icon-btn"
                    aria-label="关闭管理菜单"
                    @click="navDialog?.close()"
                >
                    <X :size="20" />
                </button>
            </div>
            <nav>
                <RouterLink
                    v-for="item in navigation"
                    :key="item.path"
                    :to="item.path"
                    :class="{ active: route.path.startsWith(item.path) }"
                    @click="navDialog?.close()"
                >
                    <component :is="item.icon" :size="19" />
                    {{ item.label }}
                </RouterLink>
            </nav>
        </dialog>
    </div>
</template>
