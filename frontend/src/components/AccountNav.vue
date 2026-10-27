<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
    UserRound,
    LockKeyhole,
    MapPin,
    ReceiptText,
    Ticket,
    MessagesSquare,
} from 'lucide-vue-next'
const route = useRoute()
const nav = ref<HTMLElement>()
function revealCurrent() {
    const container = nav.value
    const current = container?.querySelector<HTMLElement>('a.active')
    if (!container || !current || container.scrollWidth <= container.clientWidth) return
    const parent = container.getBoundingClientRect()
    const item = current.getBoundingClientRect()
    container.scrollLeft += item.left - parent.left - (container.clientWidth - item.width) / 2
}
onMounted(() => {
    revealCurrent()
    window.addEventListener('resize', revealCurrent)
})
onUnmounted(() => window.removeEventListener('resize', revealCurrent))
const links = [
    { path: '/account/orders', label: '我的订单', icon: Ticket },
    { path: '/account/refunds', label: '退款记录', icon: ReceiptText },
    { path: '/account/profile', label: '个人资料', icon: UserRound },
    { path: '/account/security', label: '账户安全', icon: LockKeyhole },
    { path: '/account/addresses', label: '收货地址', icon: MapPin },
    { path: '/support', label: '智能客服', icon: MessagesSquare },
]
</script>
<template>
    <aside class="account-sidebar">
        <div class="account-sidebar-title">
            我的有戏
            <small>把期待，安排妥当。</small>
        </div>
        <nav ref="nav" aria-label="个人中心导航">
            <RouterLink
                v-for="link in links"
                :key="link.path"
                :to="link.path"
                :class="{
                    active:
                        route.path === link.path ||
                        (link.path === '/account/orders' &&
                            route.path.startsWith('/account/orders/')),
                }"
            >
                <component :is="link.icon" :size="18" />
                {{ link.label }}
            </RouterLink>
        </nav>
    </aside>
</template>
