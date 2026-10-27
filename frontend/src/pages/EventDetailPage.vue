<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CalendarDays, Check, MapPin, Minus, Plus, ShieldCheck, Ticket } from 'lucide-vue-next'
import { browse } from '../api/browse'
import type { Event, Session, Tier } from '../api/types'
import PosterImage from '../components/PosterImage.vue'
import LoadState from '../components/LoadState.vue'
import { dateLabel, money, saleState } from '../utils/format'

const route = useRoute()
const router = useRouter()
function buy() {
    if (!canBuy.value || !event.value) return
    router.push({
        path: '/checkout',
        query: {
            eventId: event.value.id,
            sessionId: sessionId.value,
            tierId: tierId.value,
            quantity: String(quantity.value),
        },
    })
}
const event = ref<Event | null>(null)
const sessions = ref<Session[]>([])
const tiers = ref<Tier[]>([])
const sessionId = ref('')
const tierId = ref('')
const quantity = ref(1)
const loading = ref(true)
const error = ref('')
const tierLoading = ref(false)
const tierError = ref('')
const retry = ref(0)
const tierRetry = ref(0)
const notice = ref('')
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval>
onMounted(() => {
    timer = setInterval(() => {
        now.value = Date.now()
    }, 1000)
})
onUnmounted(() => clearInterval(timer))
const state = computed(() => (event.value ? saleState(event.value, now.value) : ''))
const selectedTier = computed(() => tiers.value.find((tier) => tier.id === tierId.value))
const maxQuantity = computed(() =>
    Math.max(1, Math.min(5, selectedTier.value?.availableStock || 1)),
)
const canBuy = computed(
    () =>
        state.value === '售票中' &&
        !!selectedTier.value &&
        selectedTier.value.availableStock > 0 &&
        !tierLoading.value &&
        !tierError.value,
)
const description = computed(() => (event.value?.description || '演出详情待补充。').split('\n'))

watch(
    [() => String(route.params.id), retry],
    async ([id], _, onCleanup) => {
        const controller = new AbortController()
        onCleanup(() => controller.abort())
        event.value = null
        sessionId.value = ''
        loading.value = true
        error.value = ''
        notice.value = ''
        try {
            const detail = await browse.event(id, controller.signal)
            const shows = await browse.sessions(id, controller.signal)
            if (controller.signal.aborted) return
            event.value = detail
            sessions.value = shows.filter((show) => show.status === 1)
            sessionId.value = sessions.value[0]?.id || ''
            document.title = `${detail.name} · 有戏`
        } catch (cause) {
            if (!controller.signal.aborted)
                error.value = cause instanceof Error ? cause.message : '详情加载失败'
        } finally {
            if (!controller.signal.aborted) loading.value = false
        }
    },
    { immediate: true },
)
watch([sessionId, tierRetry], async ([id], _, onCleanup) => {
    tiers.value = []
    tierId.value = ''
    quantity.value = 1
    tierError.value = ''
    tierLoading.value = false
    notice.value = ''
    if (!id || !event.value) return
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    tierLoading.value = true
    try {
        const page = await browse.tiers(event.value.id, id, controller.signal)
        if (controller.signal.aborted) return
        tiers.value = page.records
        tierId.value = page.records.find((tier) => tier.availableStock > 0)?.id || ''
    } catch (cause) {
        if (!controller.signal.aborted)
            tierError.value = cause instanceof Error ? cause.message : '票档加载失败'
    } finally {
        if (!controller.signal.aborted) tierLoading.value = false
    }
})
function selectTier(id: string) {
    tierId.value = id
    quantity.value = 1
    notice.value = ''
}
</script>
<template>
    <div class="container detail-page">
        <p class="breadcrumb">
            <RouterLink to="/">首页</RouterLink>
            /
            <RouterLink to="/events">演出</RouterLink>
            / 演出详情
        </p>
        <LoadState :loading="loading" :error="error" @retry="retry++" />
        <template v-if="!loading && !error && event">
            <section class="detail-panel">
                <div class="detail-poster">
                    <PosterImage :src="event.coverImage" :alt="event.name" eager />
                    <div class="poster-note">
                        <ShieldCheck :size="16" />
                        本地演示 · 非真实售票
                    </div>
                </div>
                <div class="detail-info">
                    <span class="detail-category">
                        {{ event.categoryName }} / {{ event.cityName }}
                    </span>
                    <h1>{{ event.name }}</h1>
                    <p v-if="event.artistName" class="artist-line">{{ event.artistName }}</p>
                    <div class="detail-facts">
                        <p>
                            <CalendarDays :size="17" />
                            <span>
                                {{ dateLabel(event.eventStartTime) }}
                                <template
                                    v-if="
                                        dateLabel(event.eventStartTime) !==
                                        dateLabel(event.eventEndTime)
                                    "
                                >
                                    — {{ dateLabel(event.eventEndTime) }}
                                </template>
                            </span>
                        </p>
                        <p>
                            <MapPin :size="17" />
                            <span>{{ event.cityName }} · {{ event.venue }}</span>
                        </p>
                    </div>
                    <div class="booking-area">
                        <div class="booking-status">
                            <span>{{ state }}</span>
                            <p v-if="state === '即将开售'">
                                {{ dateLabel(event.saleStartTime) }}
                                {{ event.saleStartTime.slice(11, 16) }} 开售
                            </p>
                            <p v-else>请先选择场次与票档</p>
                        </div>
                        <div class="booking-row">
                            <span class="booking-label">场次</span>
                            <div class="option-list">
                                <button
                                    v-for="session in sessions"
                                    :key="session.id"
                                    :class="{ chosen: sessionId === session.id }"
                                    @click="sessionId = session.id"
                                >
                                    {{ dateLabel(session.sessionTime) }}
                                    {{ session.sessionTime.slice(11, 16) }}
                                    <small>{{ session.sessionName }}</small>
                                    <Check v-if="sessionId === session.id" :size="14" />
                                </button>
                                <p v-if="!sessions.length" class="muted">暂无可售场次</p>
                            </div>
                        </div>
                        <div class="booking-row">
                            <span class="booking-label">票档</span>
                            <div class="option-list">
                                <span v-if="tierLoading" class="muted" role="status">
                                    正在加载票档…
                                </span>
                                <template v-else-if="tierError">
                                    <p role="alert">{{ tierError }}</p>
                                    <button @click="tierRetry++">重试</button>
                                </template>
                                <template v-else>
                                    <button
                                        v-for="tier in tiers"
                                        :key="tier.id"
                                        :disabled="tier.availableStock <= 0"
                                        :class="{ chosen: tierId === tier.id }"
                                        @click="selectTier(tier.id)"
                                    >
                                        {{ tier.tierName }} ¥{{ money(tier.currentPrice) }}
                                        <small v-if="tier.availableStock <= 0">已售罄</small>
                                    </button>
                                    <p v-if="!tiers.length" class="muted">暂无可售票档</p>
                                </template>
                            </div>
                        </div>
                        <div class="booking-row">
                            <label class="booking-label" for="quantity">数量</label>
                            <div class="quantity-control">
                                <button
                                    :disabled="quantity <= 1 || !selectedTier"
                                    aria-label="减少数量"
                                    @click="quantity--"
                                >
                                    <Minus :size="15" />
                                </button>
                                <output id="quantity" aria-live="polite">{{ quantity }}</output>
                                <button
                                    :disabled="quantity >= maxQuantity || !selectedTier"
                                    aria-label="增加数量"
                                    @click="quantity++"
                                >
                                    <Plus :size="15" />
                                </button>
                                <span class="muted">单次最多 5 张</span>
                            </div>
                        </div>
                        <div class="booking-total">
                            <span>
                                合计
                                <strong>
                                    ¥{{ money((selectedTier?.currentPrice || 0) * quantity) }}
                                </strong>
                            </span>
                            <button class="button primary" :disabled="!canBuy" @click="buy">
                                <Ticket :size="18" />
                                {{
                                    state === '即将开售'
                                        ? '暂未开售'
                                        : state === '已停售'
                                          ? '已停售'
                                          : '立即购票'
                                }}
                            </button>
                        </div>
                        <p v-if="notice" class="booking-notice" role="status">{{ notice }}</p>
                        <p class="booking-footnote">
                            余票展示仅供参考，实际购票结果以服务端受理为准。
                        </p>
                    </div>
                </div>
            </section>
            <section class="description-panel">
                <h2>演出介绍</h2>
                <div class="description-body">
                    <p v-for="(paragraph, index) in description" :key="index">{{ paragraph }}</p>
                </div>
                <div class="purchase-tips">
                    <h3>购票说明</h3>
                    <p>
                        本站为学习演示项目，库存、开售时间及票档配置用于功能展示。真实演出安排及购票规则请以主办方发布的信息为准。
                    </p>
                </div>
            </section>
        </template>
    </div>
</template>
