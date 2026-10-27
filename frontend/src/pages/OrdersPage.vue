<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, RotateCw, Ticket } from 'lucide-vue-next'
import { trade } from '../api/trade'
import type { Order } from '../api/tradeTypes'
import type { Page } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { loadIntent, remainingSeconds } from '../utils/purchase'
import AccountNav from '../components/AccountNav.vue'
import LoadState from '../components/LoadState.vue'
import OrderSummary from '../components/OrderSummary.vue'
import OrderStatus from '../components/OrderStatus.vue'
import CancelOrderButton from '../components/CancelOrderButton.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const result = ref<Page<Order> | null>(null)
const loading = ref(true)
const error = ref('')
const retry = ref(0)
const pending = computed(() => (auth.user ? loadIntent(auth.user.id) : null))
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const selected = computed(() =>
    typeof route.query.status === 'string' && /^[0-4]$/.test(route.query.status)
        ? Number(route.query.status)
        : undefined,
)
const tabs = [
    { label: '全部订单', value: undefined },
    { label: '待支付', value: 0 },
    { label: '已支付', value: 1 },
    { label: '取消确认中', value: 4 },
    { label: '已取消', value: 2 },
    { label: '已完成', value: 3 },
]
function filter(value?: number) {
    router.push({
        path: '/account/orders',
        query: { status: value == null ? undefined : String(value) },
    })
}
function move(value: number) {
    router.push({ path: '/account/orders', query: { ...route.query, page: String(value) } })
}
watch(
    [page, selected, retry],
    async (_, __, onCleanup) => {
        const controller = new AbortController()
        onCleanup(() => controller.abort())
        loading.value = true
        error.value = ''
        try {
            const data = await trade.orders(page.value, selected.value, controller.signal)
            if (!controller.signal.aborted) result.value = data
        } catch (cause) {
            if (!controller.signal.aborted)
                error.value = cause instanceof Error ? cause.message : '订单加载失败'
        } finally {
            if (!controller.signal.aborted) loading.value = false
        }
    },
    { immediate: true },
)
</script>
<template>
    <div class="container account-page">
        <AccountNav />
        <div class="account-content">
            <div class="transaction-heading orders-heading">
                <div>
                    <span class="detail-category">个人中心</span>
                    <h1>我的订单</h1>
                    <p>每一次期待，都在这里。</p>
                </div>
                <button class="text-button" :disabled="loading" @click="retry++">
                    <RotateCw :size="15" />
                    刷新订单
                </button>
            </div>
            <div v-if="pending" class="inline-notice">
                有一笔购票申请记录。
                <RouterLink
                    :to="{ path: '/purchase/result', query: { requestKey: pending.requestKey } }"
                >
                    查看申请结果
                </RouterLink>
            </div>
            <nav class="order-tabs" aria-label="订单状态筛选">
                <button
                    v-for="tab in tabs"
                    :key="tab.label"
                    :class="{ active: selected === tab.value }"
                    @click="filter(tab.value)"
                >
                    {{ tab.label }}
                </button>
            </nav>
            <LoadState :loading="loading" :error="error" @retry="retry++" />
            <template v-if="!loading && !error">
                <section
                    v-for="order in result?.records"
                    :key="order.id"
                    class="order-card transaction-panel"
                >
                    <div class="order-card-heading">
                        <span>订单号：{{ order.orderNo }}</span>
                        <OrderStatus :status="order.orderStatus" />
                    </div>
                    <OrderSummary
                        :title="order.eventName"
                        :cover="order.eventImage"
                        :venue="order.venueName"
                        :time="order.sessionTime"
                        :tier="order.tierName"
                        :quantity="order.quantity"
                        :amount="order.totalAmount"
                    />
                    <div class="order-card-actions">
                        <span>{{ order.createTime }}</span>
                        <div>
                            <CancelOrderButton
                                v-if="order.orderStatus === 0"
                                :order-id="order.id"
                                @changed="retry++"
                            />
                            <RouterLink
                                :to="`/account/orders/${order.id}`"
                                class="button secondary"
                            >
                                查看详情
                            </RouterLink>
                            <RouterLink
                                v-if="
                                    order.orderStatus === 0 &&
                                    remainingSeconds(order.expireTime) > 0
                                "
                                :to="`/orders/${order.id}/pay`"
                                class="button primary"
                            >
                                继续支付
                            </RouterLink>
                        </div>
                    </div>
                </section>
                <div v-if="!result?.records.length" class="state-panel">
                    <Ticket :size="40" />
                    <h2>这里还没有订单</h2>
                    <p>去找一场值得期待的演出吧。</p>
                    <RouterLink to="/events" class="button primary">发现演出</RouterLink>
                </div>
                <nav
                    v-if="result && result.totalPages > 1"
                    class="pagination"
                    aria-label="订单分页"
                >
                    <button :disabled="result.page <= 1" @click="move(result.page - 1)">
                        <ArrowLeft :size="16" />
                        上一页
                    </button>
                    <span>{{ result.page }} / {{ result.totalPages }}</span>
                    <button
                        :disabled="result.page >= result.totalPages"
                        @click="move(result.page + 1)"
                    >
                        下一页
                        <ArrowRight :size="16" />
                    </button>
                </nav>
            </template>
        </div>
    </div>
</template>
