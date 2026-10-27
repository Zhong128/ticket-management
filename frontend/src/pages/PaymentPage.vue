<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Check, ExternalLink, ShieldCheck, Wallet, RotateCw } from 'lucide-vue-next'
import { trade } from '../api/trade'
import type { Order, Payment } from '../api/tradeTypes'
import { useAuthStore } from '../stores/auth'
import { usePoll } from '../composables/usePoll'
import { countdown, remainingSeconds } from '../utils/purchase'
import { submitPaymentForm } from '../utils/payment'
import { money } from '../utils/format'
import SaleSteps from '../components/SaleSteps.vue'
import OrderSummary from '../components/OrderSummary.vue'
import LoadState from '../components/LoadState.vue'

const route = useRoute()
const auth = useAuthStore()
const orderId = String(route.params.id)
const ownerId = auth.user?.id
let disposed = false
let pendingPopup: Window | null = null
const storageKey = `youxi-payment:${auth.user?.id}:${orderId}`
const paymentNo = ref(localStorage.getItem(storageKey) || '')
const order = ref<Order | null>(null)
const payment = ref<Payment | null>(null)
const error = ref('')
const notice = ref('')
const loading = ref(true)
const busy = ref(false)
const now = ref(Date.now())
const timer = setInterval(() => {
    now.value = Date.now()
}, 1000)
onUnmounted(() => {
    disposed = true
    clearInterval(timer)
    if (busy.value) pendingPopup?.close()
})
const remaining = computed(() => remainingSeconds(order.value?.expireTime, now.value))
const paid = computed(() => !!order.value && [1, 3].includes(order.value.orderStatus))
const canPay = computed(() => order.value?.orderStatus === 0 && remaining.value > 0 && !busy.value)
const poll = usePoll(async (signal) => {
    try {
        const data = await trade.order(orderId, signal)
        if (signal.aborted) return false
        order.value = data
        error.value = ''
        if (paymentNo.value && data.orderStatus === 0) {
            const status = await trade.payment(paymentNo.value, signal)
            if (signal.aborted) return false
            payment.value = status
            if (status.payStatus === 2) {
                const refreshed = await trade.order(orderId, signal)
                if (!signal.aborted) order.value = refreshed
            }
        }
        return !!order.value && [0, 4].includes(order.value.orderStatus)
    } catch (cause) {
        if (!signal.aborted)
            error.value = cause instanceof Error ? cause.message : '暂时无法查询付款结果'
        return true
    } finally {
        if (!signal.aborted) loading.value = false
    }
})
async function pay() {
    if (!canPay.value) return
    const popup = window.open('about:blank', '_blank')
    if (!popup) {
        error.value = '请允许打开支付窗口后重试'
        return
    }
    pendingPopup = popup
    popup.document.title = '正在打开支付宝沙箱'
    popup.document.body.textContent = '正在准备支付，请稍候…'
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
        const result = await trade.pay(orderId)
        payment.value = result
        paymentNo.value = result.paymentNo
        localStorage.setItem(storageKey, result.paymentNo)
        if (disposed || auth.user?.id !== ownerId) {
            popup.close()
            return
        }
        if (result.payStatus === 2) {
            popup.close()
            poll.restart()
            return
        }
        if (popup.closed) throw new Error('支付窗口已关闭，请重新打开')
        submitPaymentForm(result.paymentPageHtml || result.qrCode || '', popup)
        notice.value = '支付页面已打开。完成付款后，请返回此页确认服务端结果。'
        poll.restart()
    } catch (cause) {
        popup.close()
        error.value = cause instanceof Error ? cause.message : '发起支付失败，请查询订单后重试'
    } finally {
        busy.value = false
    }
}
</script>
<template>
    <div class="container transaction-page">
        <SaleSteps :step="3" />
        <LoadState :loading="loading" :error="!order ? error : ''" @retry="poll.restart" />
        <template v-if="order">
            <div class="transaction-heading">
                <h1>{{ paid ? '支付成功，期待现场见' : '订单收银台' }}</h1>
                <p>{{ paid ? '订单已由服务端确认支付。' : '请在付款期限内完成支付。' }}</p>
            </div>
            <section class="transaction-panel">
                <OrderSummary
                    :title="order.eventName"
                    :cover="order.eventImage"
                    :venue="order.venueName"
                    :time="order.sessionTime"
                    :tier="order.tierName"
                    :quantity="order.quantity"
                    :amount="order.totalAmount"
                />
            </section>
            <section class="payment-panel transaction-panel">
                <div v-if="paid" class="payment-success">
                    <Check :size="38" />
                    <h2>已支付</h2>
                    <p>订单号：{{ order.orderNo }}</p>
                    <RouterLink :to="`/account/orders/${orderId}`" class="button primary">
                        查看订单详情
                    </RouterLink>
                </div>
                <template v-else>
                    <div class="payment-heading">
                        <h2>支付方式</h2>
                        <span v-if="order.orderStatus === 0">剩余 {{ countdown(remaining) }}</span>
                    </div>
                    <div class="payment-method">
                        <Wallet :size="32" />
                        <div>
                            <h3>支付宝沙箱</h3>
                            <p>将在独立窗口打开支付页面，不使用真实资金。</p>
                        </div>
                        <ShieldCheck :size="21" />
                    </div>
                    <div v-if="order.orderStatus !== 0 || !remaining" class="inline-notice">
                        {{
                            order.orderStatus === 4
                                ? '订单正在取消确认中，暂不可支付。'
                                : order.orderStatus === 2
                                  ? '订单已取消，不可继续支付。'
                                  : '当前不可支付，请刷新订单确认最新状态。'
                        }}
                    </div>
                    <p v-if="payment?.payStatus === 5" class="inline-notice">
                        该支付流水已退款，请查看订单最新状态。
                    </p>
                    <p v-if="notice" class="inline-notice" role="status">{{ notice }}</p>
                    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                    <p v-if="poll.exhausted.value" class="muted">
                        自动查询已暂停，可点击“查询支付结果”继续。
                    </p>
                    <div class="payment-actions">
                        <div>
                            应付金额
                            <strong>¥{{ money(order.totalAmount) }}</strong>
                        </div>
                        <button
                            class="button secondary"
                            :disabled="poll.running.value"
                            @click="poll.restart"
                        >
                            <RotateCw :size="15" />
                            查询支付结果
                        </button>
                        <button class="button primary" :disabled="!canPay" @click="pay">
                            {{ busy ? '正在准备…' : paymentNo ? '重新打开支付' : '前往支付宝支付' }}
                            <ExternalLink :size="16" />
                        </button>
                    </div>
                    <p class="transaction-note">
                        浏览器回跳仅作提示，支付成功以服务端查询结果为准。
                    </p>
                </template>
            </section>
            <RouterLink :to="`/account/orders/${orderId}`" class="text-button">
                返回订单详情
            </RouterLink>
        </template>
    </div>
</template>
