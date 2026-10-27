<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest } from '../api'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
interface Refund {
    refund_no: string
    order_no?: string
    order_id: string
    amount: string
    status: string
    attempts: number
    last_error?: string
    created_at?: string
}
const route = useRoute()
const router = useRouter()
const statuses: Record<string, string> = {
    REQUESTED: '待处理',
    PROCESSING: '处理中',
    UNKNOWN: '待核对',
    RETRYABLE_FAILED: '等待重试',
    MANUAL_REQUIRED: '待人工处理',
    SUCCESS: '退款成功',
}
const filters = reactive({ refundNo: '', orderNo: '', status: '' })
const records = ref<Refund[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const busy = ref(false)
const enabled = ref(false)
const message = ref('')
const error = ref('')
const target = ref<Refund | null>(null)
let controller: AbortController | undefined
const life = new AbortController()
const isActive = () => !life.signal.aborted && route.path === '/admin/refunds'
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
    try {
        const value = await adminRequest<{ records: Refund[]; total: number }>(
            'get',
            '/admin/refunds/page',
            undefined,
            { ...filters, page: page.value, size: 20 },
            current.signal,
        )
        if (!isActive() || current.signal.aborted) {
            return
        }
        records.value = value.records
        total.value = value.total
    } catch (e) {
        if (isActive() && !current.signal.aborted) {
            error.value = e instanceof Error ? e.message : '退款记录加载失败，请重试。'
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
    router.replace({ query: { ...filters, page: next } })
}
// 仅人工处理记录可重试，布尔结果为true才表示受理。
function askRetry(row: Refund) {
    target.value = row
    error.value = ''
}

async function retry() {
    if (!isActive() || !target.value || busy.value || !enabled.value) {
        return
    }
    busy.value = true
    error.value = ''
    message.value = ''
    try {
        const accepted = await adminRequest<boolean>(
            'post',
            `/admin/refunds/${encodeURIComponent(target.value.refund_no)}/retry`,
            undefined,
            undefined,
            life.signal,
        )
        if (!isActive()) {
            return
        }
        if (accepted === true) {
            message.value = '重试已受理，请刷新查看退款结果。'
            target.value = null
            await load()
        } else {
            error.value = '未受理：退款状态可能已变化，请刷新后核对。'
        }
    } catch (e) {
        if (isActive()) {
            error.value = e instanceof Error ? e.message : '重试请求失败，请核对后重试。'
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
                <h1>退款处理</h1>
                <p class="admin-muted">核对退款状态，仅人工处理记录可重新进入重试队列。</p>
            </div>
            <button class="admin-btn" :disabled="loading" @click="load">刷新记录</button>
        </header>
        <p v-if="!enabled" class="admin-message">交易维护中或状态未确认，退款重试已停用。</p>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="message" role="status" class="admin-message success">{{ message }}</p>
        <form class="admin-filters" @submit.prevent="search()">
            <label class="admin-field">
                退款单号
                <input v-model="filters.refundNo" />
            </label>
            <label class="admin-field">
                订单号
                <input v-model="filters.orderNo" />
            </label>
            <label class="admin-field">
                退款状态
                <select v-model="filters.status">
                    <option value="">全部状态</option>
                    <option v-for="(label, key) in statuses" :key="key" :value="key">
                        {{ label }}
                    </option>
                </select>
            </label>
            <button class="admin-btn primary">查询</button>
        </form>
        <p v-if="loading" role="status">正在加载退款记录…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>退款 / 订单</th>
                        <th>退款金额</th>
                        <th>状态</th>
                        <th>尝试次数</th>
                        <th>最近异常</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in records" :key="row.refund_no">
                        <td>
                            <strong>{{ row.refund_no }}</strong>
                            <div class="admin-muted">{{ row.order_no || row.order_id }}</div>
                        </td>
                        <td>¥{{ row.amount }}</td>
                        <td>
                            <span class="admin-badge">
                                {{ statuses[row.status] || row.status }}
                            </span>
                        </td>
                        <td>{{ row.attempts }}</td>
                        <td class="refund-error">{{ row.last_error || '—' }}</td>
                        <td>
                            <button
                                v-if="row.status === 'MANUAL_REQUIRED'"
                                class="admin-btn small danger"
                                :disabled="!enabled || busy"
                                @click="askRetry(row)"
                            >
                                重试
                            </button>
                            <span v-else class="admin-muted">无需操作</span>
                        </td>
                    </tr>
                </tbody>
            </table>
            <p v-if="!loading && !records.length" class="admin-empty">
                暂无符合条件的退款，调整筛选后重试。
            </p>
        </div>
        <AdminPager :page="page" :size="20" :total="total" @change="search" />
        <AdminModal :open="!!target" title="确认退款重试" :busy="busy" @close="target = null">
            <p>
                退款
                {{ target?.refund_no }}
                将重新进入处理队列，可能触发真实退款。请先核对支付与退款流水。
            </p>
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <button class="admin-btn danger" :disabled="busy || !enabled" @click="retry">
                {{ busy ? '正在提交…' : '确认重试' }}
            </button>
        </AdminModal>
    </section>
</template>
<style scoped>
.refund-error {
    max-width: 260px;
    overflow-wrap: anywhere;
}
</style>
