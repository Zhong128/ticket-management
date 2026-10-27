<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ShieldCheck } from 'lucide-vue-next'
import { browse } from '../api/browse'
import { trade } from '../api/trade'
import { ApiError } from '../api/client'
import type { Event, Session, Tier } from '../api/types'
import type { Reservation } from '../api/tradeTypes'
import { useAuthStore } from '../stores/auth'
import { getOrCreateIntent, loadIntent, saveIntent } from '../utils/purchase'
import { saleState, money } from '../utils/format'
import SaleSteps from '../components/SaleSteps.vue'
import OrderSummary from '../components/OrderSummary.vue'
import LoadState from '../components/LoadState.vue'

let disposed = false
onUnmounted(() => {
    disposed = true
})
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const event = ref<Event | null>(null)
const show = ref<Session | null>(null)
const tier = ref<Tier | null>(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const agreed = ref(false)
const retry = ref(0)
const text = (name: string) =>
    typeof route.query[name] === 'string' ? (route.query[name] as string) : ''
const quantity = computed(() => Number(text('quantity')))
const previous = computed(() => (auth.user ? loadIntent(auth.user.id) : null))
const amount = computed(() => (tier.value?.currentPrice || 0) * quantity.value)
watch(
    retry,
    async (_, __, onCleanup) => {
        const controller = new AbortController()
        onCleanup(() => controller.abort())
        loading.value = true
        error.value = ''
        try {
            if (
                !/^\d+$/.test(text('eventId')) ||
                !/^\d+$/.test(text('sessionId')) ||
                !/^\d+$/.test(text('tierId')) ||
                !Number.isInteger(quantity.value) ||
                quantity.value < 1 ||
                quantity.value > 5
            )
                throw new Error('购票信息不完整，请返回演出页重新选择')
            const detail = await browse.event(text('eventId'), controller.signal)
            const [shows, prices] = await Promise.all([
                browse.sessions(detail.id, controller.signal),
                browse.tiers(detail.id, text('sessionId'), controller.signal),
            ])
            if (controller.signal.aborted) return
            event.value = detail
            show.value = shows.find((item) => item.id === text('sessionId')) || null
            tier.value = prices.records.find((item) => item.id === text('tierId')) || null
            if (!show.value || !tier.value) throw new Error('所选场次或票档已不可售，请重新选择')
            if (saleState(detail) !== '售票中') throw new Error('当前演出不在售票时间内')
            if (tier.value.availableStock < quantity.value)
                throw new Error('当前余票不足，请重新选择数量')
        } catch (cause) {
            if (!controller.signal.aborted)
                error.value = cause instanceof Error ? cause.message : '确认信息加载失败'
        } finally {
            if (!controller.signal.aborted) loading.value = false
        }
    },
    { immediate: true },
)
async function submit() {
    if (
        busy.value ||
        !agreed.value ||
        !auth.user ||
        !event.value ||
        !show.value ||
        !tier.value ||
        error.value
    )
        return
    busy.value = true
    try {
        const intent = getOrCreateIntent(loadIntent(auth.user.id), {
            userId: auth.user.id,
            eventId: event.value.id,
            sessionId: show.value.id,
            tierId: tier.value.id,
            quantity: quantity.value,
        })
        saveIntent(intent)
        try {
            intent.response = await trade.reserve(intent.tierId, intent.quantity, intent.requestKey)
        } catch (cause) {
            if (cause instanceof ApiError && cause.code === 401) return
            if (
                cause instanceof ApiError &&
                cause.data &&
                typeof cause.data === 'object' &&
                'requestStatus' in cause.data
            )
                intent.response = cause.data as Reservation
            else intent.response = { requestStatus: 'UNKNOWN' }
            if (intent.response && cause instanceof Error) intent.response.message = cause.message
        }
        saveIntent(intent)
        if (!disposed && auth.user?.id === intent.userId)
            await router.replace({
                path: '/purchase/result',
                query: { requestKey: intent.requestKey },
            })
    } catch (cause) {
        error.value = cause instanceof Error ? cause.message : '申请暂不可提交'
    } finally {
        busy.value = false
    }
}
</script>
<template>
    <div class="container transaction-page">
        <SaleSteps :step="1" />
        <div class="transaction-heading">
            <h1>确认购票信息</h1>
            <p>核对场次与票档，确认后提交购票申请。</p>
        </div>
        <LoadState :loading="loading" :error="error" @retry="retry++" />
        <template v-if="!loading && !error && event && show && tier">
            <section class="transaction-panel">
                <OrderSummary
                    :title="event.name"
                    :cover="event.coverImage"
                    :venue="event.venue"
                    :time="show.sessionTime"
                    :tier="tier.tierName"
                    :quantity="quantity"
                    :amount="amount"
                />
                <div class="purchase-rules">
                    <h2>购票须知</h2>
                    <p>
                        提交后系统将处理购票申请，受理成功后仍需等待订单创建。支付期限以服务端返回的时间为准。
                    </p>
                    <p>不支持选座。本项目为学习演示，展示库存与票价不代表官方售票信息。</p>
                </div>
            </section>
            <div v-if="previous" class="inline-notice">
                你有一笔已提交的购票申请，请先查看原申请结果。
                <RouterLink
                    :to="{ path: '/purchase/result', query: { requestKey: previous.requestKey } }"
                >
                    查看购票结果
                </RouterLink>
            </div>
            <div class="checkout-footer">
                <label class="agreement">
                    <input v-model="agreed" type="checkbox" />
                    我已核对演出信息并了解购票须知
                </label>
                <div>
                    <span>
                        预计合计
                        <strong>¥{{ money(amount) }}</strong>
                    </span>
                    <button
                        class="button primary"
                        :disabled="busy || !agreed || !!previous"
                        @click="submit"
                    >
                        {{ busy ? '正在提交…' : '确认并提交申请' }}
                    </button>
                </div>
            </div>
            <p class="transaction-note">
                <ShieldCheck :size="14" />
                最终金额和购票结果以服务端返回为准，请勿重复提交。
            </p>
        </template>
        <RouterLink v-if="error" to="/events" class="text-button">重新选择演出</RouterLink>
    </div>
</template>
