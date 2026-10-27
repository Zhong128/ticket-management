<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { CalendarDays, ArrowUpRight, RefreshCw, Plus, Ticket } from 'lucide-vue-next'
import { adminRequest } from '../api'
import type { Page, Session, Event } from '../../api/types'
import PosterImage from '../../components/PosterImage.vue'
interface Summary {
    onSaleEventCount: number
    upcomingSessionCount: number
    pendingOrderCount: number
    refundAttentionCount: number
    asOf: string
}
const summary = ref<Summary | null>(null),
    sessions = ref<Session[]>([]),
    events = ref<Event[]>([]),
    loading = ref(false),
    error = ref('')
let controller: AbortController | undefined
onUnmounted(() => controller?.abort())
function localTime(date: Date) {
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
}
async function load() {
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    error.value = ''
    try {
        const now = new Date(),
            end = new Date(now.getTime() + 7 * 86400000)
        const [counts, shows, listing] = await Promise.all([
            adminRequest<Summary>('get', '/admin/dashboard/summary', undefined, undefined, signal),
            adminRequest<Page<Session>>(
                'get',
                '/admin/sessions/page',
                undefined,
                {
                    page: 1,
                    size: 8,
                    status: 1,
                    eventStatus: 1,
                    ascending: true,
                    startTime: localTime(now),
                    endTime: localTime(end),
                },
                signal,
            ),
            adminRequest<Page<Event>>(
                'get',
                '/admin/events/query',
                undefined,
                { page: 1, size: 5, status: 1 },
                signal,
            ),
        ])
        if (signal.aborted) return
        summary.value = counts
        sessions.value = shows.records
        events.value = listing.records
    } catch (cause) {
        if (!signal.aborted)
            error.value = cause instanceof Error ? cause.message : '数据暂时无法加载'
    } finally {
        if (!signal.aborted) loading.value = false
    }
}
onMounted(load)
</script>
<template>
    <div class="admin-page">
        <div class="admin-heading">
            <div>
                <h1>演出运营工作台</h1>
                <p>近期排期与待办，在这里接着处理。</p>
            </div>
            <div class="admin-actions">
                <button class="admin-btn" :disabled="loading" @click="load">
                    <RefreshCw :size="16" />
                    刷新数据
                </button>
                <RouterLink class="admin-btn primary" to="/admin/events/new">
                    <Plus :size="17" />
                    新增演出
                </RouterLink>
            </div>
        </div>
        <p v-if="error" role="alert" class="admin-message error">
            {{ error }}，可点击刷新数据重试。
        </p>
        <p v-if="loading" role="status" class="admin-muted">正在读取最新运营数据…</p>
        <div v-if="summary" class="admin-metrics">
            <RouterLink to="/admin/events?status=1">
                <span>在售演出</span>
                <strong>{{ summary.onSaleEventCount }}</strong>
                <small>当前售票中的启用演出</small>
            </RouterLink>
            <RouterLink to="/admin/sessions?status=1">
                <span>未来7天场次</span>
                <strong>{{ summary.upcomingSessionCount }}</strong>
                <small>启用演出下的近期排期</small>
            </RouterLink>
            <RouterLink to="/admin/orders?orderStatus=0">
                <span>待支付订单</span>
                <strong>{{ summary.pendingOrderCount }}</strong>
                <small>以订单最新状态为准</small>
            </RouterLink>
            <RouterLink to="/admin/refunds?status=MANUAL_REQUIRED">
                <span>待人工处理退款</span>
                <strong>{{ summary.refundAttentionCount }}</strong>
                <small>查看原因，再决定是否重试</small>
            </RouterLink>
        </div>
        <div class="admin-dashboard-columns">
            <section class="admin-schedule">
                <div class="admin-section-heading">
                    <h2>
                        <CalendarDays :size="20" />
                        近期排期
                    </h2>
                    <RouterLink to="/admin/sessions">
                        管理场次
                        <ArrowUpRight :size="15" />
                    </RouterLink>
                </div>
                <div v-if="!sessions.length" class="admin-empty">
                    未来7天暂无启用场次，可以先完善演出排期。
                </div>
                <RouterLink
                    v-for="item in sessions"
                    :key="item.id"
                    class="admin-schedule-row"
                    :to="{ path: '/admin/sessions', query: { eventId: item.eventId } }"
                >
                    <div class="admin-date-block">
                        <strong>{{ item.sessionTime?.slice(8, 10) }}</strong>
                        <small>{{ item.sessionTime?.slice(0, 7) }}</small>
                    </div>
                    <div>
                        <strong>{{ item.sessionName }}</strong>
                        <p>{{ item.sessionTime?.replace('T', ' ').slice(0, 16) }}</p>
                    </div>
                    <ArrowUpRight :size="17" />
                </RouterLink>
            </section>
            <section class="admin-programs">
                <div class="admin-section-heading">
                    <h2>
                        <Ticket :size="20" />
                        演出资料
                    </h2>
                    <RouterLink to="/admin/events">
                        查看全部
                        <ArrowUpRight :size="15" />
                    </RouterLink>
                </div>
                <div v-if="!events.length" class="admin-empty">
                    还没有启用的演出，从新增一场开始。
                </div>
                <RouterLink
                    v-for="event in events"
                    :key="event.id"
                    class="admin-program-row"
                    :to="`/admin/events/${event.id}`"
                >
                    <PosterImage :src="event.coverImage" :alt="event.name" />
                    <div>
                        <strong>{{ event.name }}</strong>
                        <p>{{ event.cityName }} · {{ event.venue }}</p>
                    </div>
                    <ArrowUpRight :size="16" />
                </RouterLink>
            </section>
        </div>
        <p v-if="summary" class="admin-updated">
            数据更新于
            {{ summary.asOf?.replace('T', ' ').slice(0, 19) }}，库存占用不等于已付款销量。
        </p>
    </div>
</template>
