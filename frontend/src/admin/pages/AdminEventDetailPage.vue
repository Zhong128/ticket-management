<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { adminRequest } from '../api'
import PosterImage from '../../components/PosterImage.vue'
import type { Event, Session } from '../../api/types'
import { useAdminTask, eventStatus, timeText, safeCover, allOptions } from './contentSupport'

const route = useRoute()
const { busy, error, run } = useAdminTask()
const event = ref<Event | null>(null)
const sessions = ref<Session[]>([])

watch(
    () => route.params.id,
    () =>
        run(
            async (signal) => {
                const [detail, related] = await Promise.all([
                    adminRequest<Event>(
                        'get',
                        `/admin/events/${route.params.id}`,
                        undefined,
                        undefined,
                        signal,
                    ),
                    allOptions<Session>('/admin/sessions/page', signal, {
                        eventId: route.params.id,
                    }),
                ])
                return { detail, related }
            },
            ({ detail, related }) => {
                event.value = detail
                sessions.value = related
            },
        ),
    { immediate: true },
)
</script>
<template>
    <section class="admin-page">
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="busy" role="status">正在加载…</p>
        <template v-if="event">
            <div class="admin-heading">
                <div>
                    <h1>{{ event.name }}</h1>
                    <p class="admin-muted">
                        {{ event.artistName }} · {{ eventStatus(event.status) }}
                    </p>
                </div>
                <div class="admin-actions">
                    <RouterLink class="admin-btn" to="/admin/events">返回列表</RouterLink>
                    <RouterLink class="admin-btn primary" :to="`/admin/events/${event.id}/edit`">
                        编辑演出
                    </RouterLink>
                </div>
            </div>
            <div class="event-detail">
                <PosterImage
                    class="admin-detail-cover"
                    :src="safeCover(event.coverImage)"
                    :alt="event.name"
                />
                <div>
                    <h2>演出信息</h2>
                    <dl>
                        <dt>城市 / 分类</dt>
                        <dd>{{ event.cityName }} / {{ event.categoryName }}</dd>
                        <dt>场馆</dt>
                        <dd>{{ event.venue }}</dd>
                        <dt>售票时间</dt>
                        <dd>
                            {{ timeText(event.saleStartTime) }} 至 {{ timeText(event.saleEndTime) }}
                        </dd>
                        <dt>演出时间</dt>
                        <dd>
                            {{ timeText(event.eventStartTime) }} 至
                            {{ timeText(event.eventEndTime) }}
                        </dd>
                    </dl>
                    <h2>演出介绍</h2>
                    <p class="description">{{ event.description || '暂无介绍' }}</p>
                </div>
            </div>
            <div class="admin-heading">
                <h2>关联场次</h2>
                <RouterLink
                    class="admin-btn"
                    :to="{ path: '/admin/sessions', query: { eventId: event.id } }"
                >
                    管理全部场次
                </RouterLink>
            </div>
            <div class="admin-table-scroll">
                <table class="admin-table">
                    <thead>
                        <tr>
                            <th>场次</th>
                            <th>时间</th>
                            <th>状态</th>
                            <th>票档</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="session in sessions" :key="session.id">
                            <td>{{ session.sessionName }}</td>
                            <td>{{ timeText(session.sessionTime) }}</td>
                            <td>{{ session.status === 1 ? '已上架' : '已下架' }}</td>
                            <td>
                                <RouterLink
                                    :to="{
                                        path: '/admin/ticket-tiers',
                                        query: { eventId: event.id, sessionId: session.id },
                                    }"
                                >
                                    查看票档
                                </RouterLink>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <p v-if="!sessions.length" class="admin-empty">暂无场次，前往场次管理新增。</p>
        </template>
    </section>
</template>
<style scoped>
.admin-detail-cover { width: 180px; min-height: 240px; aspect-ratio: 3/4; overflow: hidden; }
.event-detail {
    display: grid;
    grid-template-columns: 180px 1fr;
    gap: 32px;
    margin: 24px 0 40px;
}
.event-detail > img {
    width: 180px;
    aspect-ratio: 3/4;
    object-fit: cover;
    border-radius: 6px;
}
h2 {
    font-size: 18px;
}
dl {
    display: grid;
    grid-template-columns: 100px 1fr;
    gap: 14px;
}
dt {
    color: #777d8b;
}
dd {
    margin: 0;
}
.description {
    white-space: pre-wrap;
    line-height: 1.8;
}
@media (max-width: 650px) {
    .event-detail {
        grid-template-columns: 1fr;
    }
    dl {
        grid-template-columns: 1fr;
        gap: 6px;
    }
}
</style>
