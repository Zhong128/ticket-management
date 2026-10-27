<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminRequest } from '../api'
import AdminModal from '../components/AdminModal.vue'
interface Order {
    id: string
    orderNo: string
    userName: string
    userId: string
    eventName: string
    sessionName: string
    sessionTime: string
    venueName: string
    tierName: string
    tierPrice: string
    quantity: number
    totalAmount: string
    orderStatus: number
    payTime: string
    createTime: string
    updateTime: string
    expireTime: string
    cancelReason: string
    remark: string
}
const route = useRoute()
const order = ref<Order | null>(null)
const remark = ref('')
const loading = ref(false)
const busy = ref(false)
const enabled = ref(false)
const error = ref('')
const message = ref('')
const confirming = ref(false)
const labels: Record<number, string> = {
    0: '待支付',
    1: '已支付',
    2: '已取消',
    3: '已完成',
    4: '取消确认中',
}
const life = new AbortController()
let current: AbortController | undefined
onBeforeUnmount(() => {
    life.abort()
    current?.abort()
})
adminRequest<{ trade?: { enabled: boolean }; tradingEnabled?: boolean }>(
    'get',
    '/public/app-status',
    undefined,
    undefined,
    life.signal,
)
    .then((v) => {
        if (life.signal.aborted) {
            return
        }
        enabled.value = v.trade?.enabled ?? v.tradingEnabled === true
    })
    .catch(() => {})
// 离开页面或切换筛选后，旧请求不得覆盖当前列表。
async function load() {
    if (life.signal.aborted || !route.params.id || !route.path.startsWith('/admin/orders/')) {
        return
    }
    current?.abort()
    const request = new AbortController()
    current = request
    loading.value = true
    error.value = ''
    try {
        const data = await adminRequest<Order>(
            'get',
            `/admin/orders/${encodeURIComponent(String(route.params.id))}`,
            undefined,
            undefined,
            request.signal,
        )
        if (life.signal.aborted || request.signal.aborted) {
            return
        }
        order.value = data
        remark.value = data.remark || ''
    } catch (e) {
        if (!life.signal.aborted && !request.signal.aborted) {
            error.value = e instanceof Error ? e.message : '订单加载失败。'
        }
    } finally {
        if (!life.signal.aborted && !request.signal.aborted) {
            loading.value = false
        }
    }
}
watch(() => route.params.id, load, { immediate: true })
// 取消只提交状态，完成后重新查询实际订单状态。
async function save(cancel = false) {
    if (life.signal.aborted || !order.value || busy.value || (cancel && !enabled.value)) {
        return
    }
    const orderId = order.value.id
    busy.value = true
    error.value = ''
    message.value = ''
    try {
        await adminRequest(
            'put',
            `/admin/orders/${orderId}`,
            cancel ? { orderStatus: 2 } : { remark: remark.value },
            undefined,
            life.signal,
        )
        if (life.signal.aborted) {
            return
        }
        if (String(route.params.id) !== String(orderId)) {
            return
        }
        confirming.value = false
        await load()
        if (life.signal.aborted) {
            return
        }
        message.value = cancel
            ? order.value.orderStatus === 2
                ? '已重新查询：订单已取消。'
                : '取消请求已提交，当前状态尚待确认，请刷新核对。'
            : '备注已保存。'
    } catch (e) {
        if (!life.signal.aborted) {
            error.value = e instanceof Error ? e.message : '更新失败，请保留内容后重试。'
        }
    } finally {
        if (!life.signal.aborted) {
            busy.value = false
        }
    }
}
</script>
<template>
    <section class="admin-page">
        <header class="admin-heading">
            <div>
                <RouterLink to="/admin/orders" class="admin-muted">返回订单列表</RouterLink>
                <h1>订单详情</h1>
            </div>
            <button class="admin-btn" :disabled="busy || loading" @click="load">刷新状态</button>
        </header>
        <p v-if="error" class="admin-message error" role="alert">{{ error }}</p>
        <p v-if="message" class="admin-message success" role="status">{{ message }}</p>
        <p v-if="loading">正在加载订单…</p>
        <template v-if="order">
            <div class="admin-order-banner">
                <div>
                    <span class="admin-muted">订单号</span>
                    <h2>{{ order.orderNo }}</h2>
                </div>
                <span class="admin-badge">{{ labels[order.orderStatus] || '未知' }}</span>
                <strong>¥{{ order.totalAmount }}</strong>
            </div>
            <p v-if="order.orderStatus === 4" class="admin-message">
                取消确认中：正在核对支付渠道结果，尚不能认定取消成功。
            </p>
            <dl class="admin-order-facts">
                <template
                    v-for="(label, key) in {
                        userName: '用户',
                        userId: '用户ID',
                        eventName: '演出',
                        sessionName: '场次',
                        sessionTime: '演出时间',
                        venueName: '场馆',
                        tierName: '票档',
                        tierPrice: '单价',
                        quantity: '数量',
                        payTime: '付款时间',
                        createTime: '创建时间',
                        updateTime: '更新时间',
                        expireTime: '到期时间',
                        cancelReason: '取消原因',
                    }"
                    :key="key"
                >
                    <div>
                        <dt>{{ label }}</dt>
                        <dd>{{ order[key] ?? '—' }}</dd>
                    </div>
                </template>
            </dl>
            <form class="admin-form" @submit.prevent="save()">
                <label class="admin-field">
                    管理备注
                    <textarea v-model="remark" rows="4" maxlength="500" :disabled="busy" />
                </label>
                <div class="admin-actions">
                    <button class="admin-btn primary" :disabled="busy">
                        {{ busy ? '正在提交…' : '保存备注' }}
                    </button>
                    <button
                        v-if="order.orderStatus === 0"
                        type="button"
                        class="admin-btn danger"
                        :disabled="busy || !enabled"
                        @click="confirming = true"
                    >
                        取消订单
                    </button>
                </div>
            </form>
            <p v-if="!enabled" class="admin-muted">交易维护中或状态未确认，取消操作已停用。</p>
        </template>
        <AdminModal
            :open="confirming"
            title="确认取消订单"
            :busy="busy"
            @close="confirming = false"
        >
            <p>
                将取消订单
                {{ order?.orderNo }}，可能关闭支付并释放库存。付款状态以重新查询结果为准。
            </p>
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <button class="admin-btn danger" :disabled="busy || !enabled" @click="save(true)">
                {{ busy ? '正在提交…' : '确认取消订单' }}
            </button>
        </AdminModal>
    </section>
</template>
<style scoped>
.admin-order-banner {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 24px;
    background: #fff;
    border-left: 4px solid #eb1761;
    flex-wrap: wrap;
}
.admin-order-banner > div {
    min-width: 0;
    flex: 1 1 220px;
}
.admin-order-banner strong {
    margin-left: auto;
    font-size: 28px;
}
.admin-order-banner h2 {
    margin: 8px 0 0;
    overflow-wrap: anywhere;
}
.admin-order-facts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 24px;
    padding: 28px 0;
}
.admin-order-facts dt {
    color: #64748b;
    font-size: 13px;
}
.admin-order-facts dd {
    margin: 8px 0 0;
    overflow-wrap: anywhere;
}
</style>
