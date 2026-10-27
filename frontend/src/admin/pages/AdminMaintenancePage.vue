<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { adminRequest } from '../api'
import AdminModal from '../components/AdminModal.vue'
const waitingMinutes = ref(10)
const timeoutMinutes = ref(35)
const action = ref<'compensation' | 'timeout-close' | null>(null)
const busy = ref(false)
const enabled = ref(false)
const error = ref('')
const message = ref('')
const life = new AbortController()
onBeforeUnmount(() => life.abort())
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
function confirm(kind: 'compensation' | 'timeout-close') {
    error.value = ''
    message.value = ''
    const n = kind === 'compensation' ? waitingMinutes.value : timeoutMinutes.value
    if (!Number.isSafeInteger(n) || n < 1) {
        error.value = '请输入大于0的整数分钟数。'
        return
    }
    action.value = kind
}
// 仅在页面仍活动时提交，失败保留确认内容供核对。
async function execute() {
    if (life.signal.aborted || !action.value || busy.value || !enabled.value) {
        return
    }
    busy.value = true
    error.value = ''
    try {
        await adminRequest(
            'post',
            `/admin/payments/${action.value}`,
            undefined,
            action.value === 'compensation'
                ? { waitingMinutes: waitingMinutes.value }
                : { timeoutMinutes: timeoutMinutes.value },
            life.signal,
        )
        if (life.signal.aborted) {
            return
        }
        message.value = '任务已受理。处理结果请在订单、退款记录及服务日志中核对。'
        action.value = null
    } catch (e) {
        if (!life.signal.aborted) {
            error.value = e instanceof Error ? e.message : '任务未受理，请核对后重试。'
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
                <h1>交易维护</h1>
                <p class="admin-muted">人工触发交易检查，执行前确认影响范围。</p>
            </div>
        </header>
        <p v-if="!enabled" class="admin-message">交易维护中或状态未确认，维护操作已停用。</p>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="message" role="status" class="admin-message success">{{ message }}</p>
        <div class="maintenance-grid">
            <form class="maintenance-panel" @submit.prevent="confirm('compensation')">
                <h2>支付补偿</h2>
                <p>检查超过等待时间的支付记录，查询渠道状态并补偿本地交易。</p>
                <label class="admin-field">
                    等待时间（分钟，正整数）
                    <input
                        v-model.number="waitingMinutes"
                        type="number"
                        min="1"
                        step="1"
                        required
                    />
                </label>
                <button class="admin-btn primary" :disabled="busy || !enabled">触发支付补偿</button>
            </form>
            <form class="maintenance-panel" @submit.prevent="confirm('timeout-close')">
                <h2>超时关单</h2>
                <p>检查超过阈值的未支付订单，可能关闭支付并释放库存。</p>
                <label class="admin-field">
                    超时时间（分钟，正整数）
                    <input
                        v-model.number="timeoutMinutes"
                        type="number"
                        min="1"
                        step="1"
                        required
                    />
                </label>
                <button class="admin-btn danger" :disabled="busy || !enabled">触发超时关单</button>
            </form>
        </div>
        <AdminModal :open="!!action" title="确认维护任务" :busy="busy" @close="action = null">
            <p v-if="action === 'compensation'">
                将检查等待超过 {{ waitingMinutes }} 分钟的支付记录，可能更新付款及订单状态。
            </p>
            <p v-else>
                将检查超过 {{ timeoutMinutes }} 分钟的未支付订单，可能关闭订单、支付并释放库存。
            </p>
            <p>操作影响符合条件的全部记录。任务受理不代表处理已完成，请执行后核对结果。</p>
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <button class="admin-btn danger" :disabled="busy || !enabled" @click="execute">
                {{ busy ? '正在提交…' : '确认执行' }}
            </button>
        </AdminModal>
    </section>
</template>
<style scoped>
.maintenance-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 24px;
}
.maintenance-panel {
    background: #fff;
    border: 1px solid #e5e7eb;
    padding: 28px;
    border-top: 3px solid #eb1761;
}
.maintenance-panel p {
    color: #64748b;
    line-height: 1.8;
}
.maintenance-panel .admin-btn {
    margin-top: 24px;
}
</style>
