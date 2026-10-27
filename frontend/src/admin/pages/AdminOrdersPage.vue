<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest } from '../api'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
interface Order {
    id: string
    orderNo: string
    userName: string
    eventName: string
    quantity: number
    totalAmount: string
    orderStatus: number
    createTime: string
}
const route = useRoute()
const router = useRouter()
const filters = reactive({
    orderNo: '',
    userName: '',
    userEmail: '',
    eventName: '',
    orderStatus: '',
    minAmount: '',
    maxAmount: '',
    startTime: '',
    endTime: '',
})
const records = ref<Order[]>([])
const selected = ref<string[]>([])
const total = ref(0)
const page = ref(1)
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
let controller: AbortController | undefined
const life = new AbortController()
const isActive = () => !life.signal.aborted && route.path === '/admin/orders'
onBeforeUnmount(() => {
    controller?.abort()
    life.abort()
})
adminRequest<{ trade?: { enabled: boolean }; tradingEnabled?: boolean }>(
    'get',
    '/public/app-status',
    undefined,
    undefined,
    life.signal,
)
    .then((v) => {
        if (!isActive()) {
            return
        }
        enabled.value = v.trade?.enabled ?? v.tradingEnabled === true
    })
    .catch(() => {})
// 离开页面或切换筛选后，旧请求不得覆盖当前列表。
async function load() {
    if (!isActive()) {
        return
    }

    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    selected.value = []
    try {
        const value = await adminRequest<{ records: Order[]; total: number }>(
            'get',
            '/admin/orders/page',
            undefined,
            {
                ...filters,
                startTime: filters.startTime || undefined,
                endTime: filters.endTime || undefined,
                page: page.value,
                size: 20,
            },
            current.signal,
        )
        if (!isActive() || current.signal.aborted) {
            return
        }
        records.value = value.records
        total.value = value.total
    } catch (e) {
        if (isActive() && !current.signal.aborted) {
            error.value = e instanceof Error ? e.message : '订单加载失败，请重试。'
        }
    } finally {
        if (isActive() && !current.signal.aborted) {
            loading.value = false
        }
    }
}
watch(
    () => route.query,
    (q) => {
        for (const key of Object.keys(filters) as (keyof typeof filters)[]) {
            filters[key] = String(q[key] || '')
        }
        page.value = Math.max(1, Number(q.page) || 1)
        load()
    },
    { immediate: true },
)
function search(next = 1) {
    if (
        filters.minAmount &&
        filters.maxAmount &&
        Number(filters.minAmount) > Number(filters.maxAmount)
    ) {
        error.value = '最低金额不能高于最高金额。'
        return
    }
    router.replace({ query: { ...filters, page: next } })
}
// 请求受理不等于取消完成，刷新后逐项核对状态。
async function cancel() {
    if (!isActive() || !enabled.value || busy.value || !selected.value.length) {
        return
    }
    busy.value = true
    error.value = ''
    try {
        await adminRequest(
            'put',
            '/admin/orders/batch-cancel',
            [...selected.value],
            undefined,
            life.signal,
        )
        if (!isActive()) {
            return
        }
        confirming.value = false
        await load()
        if (!isActive()) {
            return
        }
        message.value = '取消请求已提交，已重新查询。请逐项核对状态，取消确认中尚未完成。'
    } catch (e) {
        if (isActive()) {
            error.value = e instanceof Error ? e.message : '取消请求失败，请重新查询核对。'
        }
    } finally {
        if (isActive()) {
            busy.value = false
        }
    }
}
</script>
<template>
    <section class="admin-page">
        <header class="admin-heading">
            <div>
                <h1>订单管理</h1>
                <p class="admin-muted">追踪演出票务订单，金额、数量和付款状态仅供核对。</p>
            </div>
            <button class="admin-btn" :disabled="loading" @click="load">刷新订单</button>
        </header>
        <p v-if="!enabled" class="admin-message">交易维护中或状态未确认，订单取消已停用。</p>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="message" role="status" class="admin-message success">{{ message }}</p>
        <form class="admin-filters" @submit.prevent="search()">
            <label
                v-for="(label, key) in {
                    orderNo: '订单号',
                    userName: '用户名',
                    userEmail: '用户邮箱',
                    eventName: '演出名称',
                }"
                :key="key"
                class="admin-field"
            >
                {{ label }}
                <input v-model="filters[key]" />
            </label>
            <label class="admin-field">
                订单状态
                <select v-model="filters.orderStatus">
                    <option value="">全部状态</option>
                    <option v-for="(label, key) in labels" :key="key" :value="key">
                        {{ label }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                最低金额
                <input v-model="filters.minAmount" type="number" min="0" step="0.01" />
            </label>
            <label class="admin-field">
                最高金额
                <input v-model="filters.maxAmount" type="number" min="0" step="0.01" />
            </label>
            <label class="admin-field">
                创建开始时间
                <input v-model="filters.startTime" type="datetime-local" step="1" />
            </label>
            <label class="admin-field">
                创建结束时间
                <input v-model="filters.endTime" type="datetime-local" step="1" />
            </label>
            <button class="admin-btn primary">查询订单</button>
        </form>
        <div class="admin-actions">
            <span class="admin-muted">已选 {{ selected.length }} 个待支付订单</span>
            <button
                class="admin-btn danger"
                :disabled="busy || !enabled || !selected.length"
                @click="confirming = true"
            >
                批量取消
            </button>
        </div>
        <p v-if="loading" role="status">正在加载订单…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>选择</th>
                        <th>订单 / 用户</th>
                        <th>演出</th>
                        <th>数量</th>
                        <th>金额</th>
                        <th>状态</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in records" :key="row.id">
                        <td>
                            <input
                                v-model="selected"
                                type="checkbox"
                                :value="row.id"
                                :disabled="row.orderStatus !== 0 || busy"
                                :aria-label="`选择订单${row.orderNo}`"
                            />
                        </td>
                        <td>
                            <strong>{{ row.orderNo }}</strong>
                            <div class="admin-muted">{{ row.userName }}</div>
                        </td>
                        <td>{{ row.eventName }}</td>
                        <td>{{ row.quantity }}</td>
                        <td>¥{{ row.totalAmount }}</td>
                        <td>
                            <span class="admin-badge">{{ labels[row.orderStatus] || '未知' }}</span>
                        </td>
                        <td>{{ row.createTime }}</td>
                        <td>
                            <RouterLink :to="`/admin/orders/${row.id}`" class="admin-btn small">
                                详情
                            </RouterLink>
                        </td>
                    </tr>
                </tbody>
            </table>
            <p v-if="!loading && !records.length" class="admin-empty">
                暂无符合条件的订单，调整筛选后重试。
            </p>
        </div>
        <AdminPager :page="page" :size="20" :total="total" @change="search" />
        <AdminModal
            :open="confirming"
            title="确认批量取消"
            :busy="busy"
            @close="confirming = false"
        >
            <p>
                将取消所选
                {{ selected.length }}
                个待支付订单，可能关闭支付并释放库存。提交后需核对每条订单状态。
            </p>
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <button class="admin-btn danger" :disabled="busy || !enabled" @click="cancel">
                {{ busy ? '正在提交…' : '确认取消订单' }}
            </button>
        </AdminModal>
    </section>
</template>
