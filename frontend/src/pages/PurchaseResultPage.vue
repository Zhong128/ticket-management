<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Clock3, Info, Ticket, X } from 'lucide-vue-next'
import { trade } from '../api/trade'
import { ApiError } from '../api/client'
import type { Reservation } from '../api/tradeTypes'
import { useAuthStore } from '../stores/auth'
import { forgetIntent, loadIntent, reservationView, saveIntent } from '../utils/purchase'
import { usePoll } from '../composables/usePoll'
import { money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const key = typeof route.query.requestKey === 'string' ? route.query.requestKey : ''
const saved = auth.user ? loadIntent(auth.user.id) : null
const reply = ref<Reservation | null>(
    saved?.requestKey === key && saved.response?.requestStatus === 'REJECTED'
        ? saved.response
        : null,
)
const error = ref('')
const retrying = ref(false)
const state = computed(() => (reply.value ? reservationView(reply.value) : 'processing'))
const titles: Record<string, string> = {
    processing: '正在创建你的订单',
    unknown: '正在确认购票结果',
    created: '订单已创建，请及时支付',
    paid: '订单已支付',
    released: '本次申请已释放',
    rejected: '本次申请未受理',
}
const poll = usePoll(async (signal) => {
    if (reply.value?.requestStatus === 'REJECTED') return false
    if (!key || key.length > 64) {
        error.value = '缺少有效的购票申请，请从确认页提交'
        return false
    }
    try {
        let data = await trade.reservation(key, signal)
        if (signal.aborted) return false
        if (
            data.requestStatus === 'UNKNOWN' &&
            saved?.requestKey === key &&
            saved.response?.requestStatus === 'REJECTED'
        )
            data = saved.response
        reply.value = data
        error.value = ''
        if (saved?.requestKey === key) {
            saved.response = data
            saveIntent(saved)
        }
        return ['processing', 'unknown'].includes(reservationView(data))
    } catch (cause) {
        if (!signal.aborted)
            error.value = cause instanceof Error ? cause.message : '暂时无法查询，请稍后重试'
        return true
    }
})
async function retryOriginal() {
    if (retrying.value || !saved || saved.requestKey !== key || state.value !== 'unknown') return
    retrying.value = true
    error.value = ''
    try {
        try {
            reply.value = await trade.reserve(saved.tierId, saved.quantity, key)
        } catch (cause) {
            if (
                cause instanceof ApiError &&
                cause.data &&
                typeof cause.data === 'object' &&
                'requestStatus' in cause.data
            )
                reply.value = cause.data as Reservation
            else throw cause
        }
        saved.response = reply.value || undefined
        saveIntent(saved)
        poll.restart()
    } catch (cause) {
        error.value = cause instanceof Error ? cause.message : '原申请重试失败，请继续查询'
    } finally {
        retrying.value = false
    }
}
function chooseAgain() {
    if (!auth.user || !['released', 'rejected', 'paid'].includes(state.value)) return
    const latest = loadIntent(auth.user.id)
    if (latest?.requestKey === key) forgetIntent(auth.user.id)
    router.push(saved ? `/events/${saved.eventId}` : '/events')
}
</script>
<template>
    <div class="container transaction-page">
        <div class="result-panel transaction-panel">
            <div class="result-symbol" :class="state">
                <Check v-if="['created', 'paid'].includes(state)" :size="38" />
                <X v-else-if="['released', 'rejected'].includes(state)" :size="34" />
                <Clock3 v-else :size="38" />
            </div>
            <h1>{{ titles[state] }}</h1>
            <p v-if="state === 'processing'">
                申请已经提交，系统正在处理。请留在此页，或稍后回来查看。
            </p>
            <p v-else-if="state === 'unknown'">
                暂时无法确认是否受理，请继续查询原申请，避免重复购买。
            </p>
            <p v-else-if="state === 'released'">
                {{ reply?.releaseReason || '申请已结束，预占将由系统释放。' }}
            </p>
            <p v-else-if="state === 'rejected'">
                {{ reply?.message || '本次申请未受理，请返回演出页检查场次和余票。' }}
            </p>
            <p v-else-if="state === 'created'">请在订单付款期限内完成支付。</p>
            <p v-else>可在订单详情查看当前订单状态。</p>
            <div v-if="reply?.orderNo" class="result-facts">
                <p>
                    订单号
                    <strong>{{ reply.orderNo }}</strong>
                </p>
                <p v-if="reply.totalAmount != null">
                    订单金额
                    <strong class="pink">¥{{ money(reply.totalAmount) }}</strong>
                </p>
                <p v-if="reply.expireTime">
                    付款截止
                    <strong>{{ reply.expireTime.replace('T', ' ') }}</strong>
                </p>
            </div>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <p v-if="poll.exhausted.value" class="inline-notice">
                结果仍在处理中。可以稍后继续查询，原申请编号已保留。
            </p>
            <div class="result-actions">
                <RouterLink
                    v-if="reply?.orderId && state === 'created'"
                    :to="`/orders/${reply.orderId}/pay`"
                    class="button primary"
                >
                    去支付
                </RouterLink>
                <RouterLink
                    v-if="reply?.orderId"
                    :to="`/account/orders/${reply.orderId}`"
                    class="button secondary"
                >
                    查看订单
                </RouterLink>
                <button
                    v-if="!reply?.orderId && !['released', 'rejected', 'paid'].includes(state)"
                    class="button secondary"
                    :disabled="poll.running.value"
                    @click="poll.restart"
                >
                    {{ poll.running.value ? '查询中…' : '继续查询' }}
                </button>
                <button
                    v-if="state === 'unknown' && saved?.requestKey === key"
                    class="button secondary"
                    :disabled="retrying"
                    @click="retryOriginal"
                >
                    使用原申请重试
                </button>
                <button
                    v-if="['released', 'rejected', 'paid'].includes(state)"
                    class="button primary"
                    @click="chooseAgain"
                >
                    重新选择演出
                </button>
                <RouterLink to="/account/orders" class="text-button">
                    <Ticket :size="16" />
                    我的订单
                </RouterLink>
            </div>
            <p class="request-reference">
                <Info :size="13" />
                申请编号：{{ key || '未提供' }}
            </p>
        </div>
    </div>
</template>
