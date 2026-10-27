<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Clock3, RotateCw } from 'lucide-vue-next'
import { trade } from '../api/trade'
import type { Order } from '../api/tradeTypes'
import { countdown, remainingSeconds } from '../utils/purchase'
import { usePoll } from '../composables/usePoll'
import OrderSummary from '../components/OrderSummary.vue'
import OrderStatus from '../components/OrderStatus.vue'
import CancelOrderButton from '../components/CancelOrderButton.vue'
import LoadState from '../components/LoadState.vue'

const route = useRoute()
const order = ref<Order | null>(null)
const error = ref('')
const loading = ref(true)
const now = ref(Date.now())
const timer = setInterval(() => {
    now.value = Date.now()
}, 1000)
onUnmounted(() => clearInterval(timer))
const remaining = computed(() => remainingSeconds(order.value?.expireTime, now.value))
const poll = usePoll(async (signal) => {
    try {
        const data = await trade.order(String(route.params.id), signal)
        if (signal.aborted) return false
        order.value = data
        error.value = ''
        return [0, 4].includes(data.orderStatus)
    } catch (cause) {
        if (!signal.aborted) error.value = cause instanceof Error ? cause.message : '查询订单失败'
        return true
    } finally {
        if (!signal.aborted) loading.value = false
    }
})
</script>
<template>
    <div class="container transaction-page">
        <p class="breadcrumb">
            <RouterLink to="/account/orders">我的订单</RouterLink>
            / 订单详情
        </p>
        <LoadState :loading="loading" :error="!order ? error : ''" @retry="poll.restart" />
        <template v-if="order">
            <div class="order-detail-heading">
                <div>
                    <span class="detail-category">订单详情</span>
                    <h1>{{ order.eventName }}</h1>
                </div>
                <OrderStatus :status="order.orderStatus" />
            </div>
            <div v-if="order.orderStatus === 0" class="order-status-banner">
                <Clock3 :size="21" />
                <div>
                    <h2>
                        {{
                            remaining
                                ? `请在 ${countdown(remaining)} 内完成支付`
                                : '付款期限已到，正在等待服务端确认状态'
                        }}
                    </h2>
                    <p>倒计时结束不代表订单已取消，最终结果以服务端为准。</p>
                </div>
            </div>
            <div v-else-if="order.orderStatus === 4" class="inline-notice">
                取消申请正在确认中，请勿重复操作。渠道确认后会更新订单状态。
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
                <dl class="order-facts">
                    <div>
                        <dt>订单号</dt>
                        <dd>{{ order.orderNo }}</dd>
                    </div>
                    <div>
                        <dt>创建时间</dt>
                        <dd>{{ order.createTime }}</dd>
                    </div>
                    <div>
                        <dt>付款期限</dt>
                        <dd>{{ order.expireTime }}</dd>
                    </div>
                    <div v-if="order.payTime">
                        <dt>付款时间</dt>
                        <dd>{{ order.payTime }}</dd>
                    </div>
                    <div v-if="order.cancelReason">
                        <dt>取消说明</dt>
                        <dd>{{ order.cancelReason }}</dd>
                    </div>
                </dl>
            </section>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <div class="result-actions order-detail-actions">
                <CancelOrderButton
                    v-if="order.orderStatus === 0"
                    :order-id="order.id"
                    @changed="poll.restart"
                />
                <button
                    class="button secondary"
                    :disabled="poll.running.value"
                    @click="poll.restart"
                >
                    <RotateCw :size="15" />
                    刷新状态
                </button>
                <RouterLink
                    v-if="order.orderStatus === 0 && remaining > 0"
                    :to="`/orders/${order.id}/pay`"
                    class="button primary"
                >
                    去支付
                </RouterLink>
                <RouterLink to="/account/orders" class="text-button">返回订单列表</RouterLink>
            </div>
        </template>
    </div>
</template>
