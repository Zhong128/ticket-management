<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { adminRequest } from '../api'
import type { Event, Page } from '../../api/types'
import AdminPager from '../components/AdminPager.vue'
import AdminModal from '../components/AdminModal.vue'
import PosterImage from '../../components/PosterImage.vue'
import { useAdminTask, referenceOptions, eventStatus, timeText, safeCover } from './contentSupport'

const route = useRoute()
const router = useRouter()
const { busy, error, run } = useAdminTask()
const keyword = ref('')
const cityId = ref('')
const categoryId = ref('')
const status = ref('')
const cities = ref<{ id: string; name: string }[]>([])
const categories = ref<{ id: string; name: string }[]>([])
const records = ref<Event[]>([])
const total = ref(0)
const page = ref(1)
const selected = ref<Event | null>(null)

async function load() {
    keyword.value = String(route.query.keyword || '')
    cityId.value = String(route.query.cityId || '')
    categoryId.value = String(route.query.categoryId || '')
    status.value = String(route.query.status ?? '')
    page.value = Math.max(1, Number(route.query.page) || 1)
    await run(
        async (signal) => {
            const [result, options] = await Promise.all([
                adminRequest<Page<Event>>(
                    'get',
                    '/admin/events/query',
                    undefined,
                    {
                        keyword: keyword.value || undefined,
                        cityId: cityId.value || undefined,
                        categoryId: categoryId.value || undefined,
                        status: status.value || undefined,
                        page: page.value,
                        size: 10,
                    },
                    signal,
                ),
                referenceOptions(signal),
            ])
            return { result, options }
        },
        ({ result, options }) => {
            records.value = result.records
            total.value = result.total
            cities.value = options.cities
            categories.value = options.categories
        },
    )
}

function search(next = 1) {
    router.push({
        query: {
            keyword: keyword.value || undefined,
            cityId: cityId.value || undefined,
            categoryId: categoryId.value || undefined,
            status: status.value || undefined,
            page: next,
        },
    })
}

async function remove() {
    if (busy.value) {
        return
    }
    if (!selected.value) {
        return
    }
    await run(
        (signal) =>
            adminRequest(
                'delete',
                `/admin/events/${selected.value!.id}`,
                undefined,
                undefined,
                signal,
            ),
        () => {
            selected.value = null
        },
    )
    if (!error.value) {
        await load()
    }
}

watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
    <section class="admin-page">
        <div class="admin-heading">
            <div>
                <h1>演出管理</h1>
                <p class="admin-muted">维护海报、演出信息与售票时间。</p>
            </div>
            <RouterLink class="admin-btn primary" to="/admin/events/new">新增演出</RouterLink>
        </div>
        <form class="admin-filters" @submit.prevent="search()">
            <label class="admin-field">
                搜索演出或艺人
                <input
                    v-model="keyword"
                    aria-label="搜索演出或艺人"
                    maxlength="100"
                    placeholder="演出名称 / 艺人"
                />
            </label>
            <label class="admin-field">
                城市
                <select v-model="cityId">
                    <option value="">全部城市</option>
                    <option v-for="city in cities" :key="city.id" :value="city.id">
                        {{ city.name }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                分类
                <select v-model="categoryId">
                    <option value="">全部分类</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">
                        {{ category.name }}
                    </option>
                </select>
            </label>
            <label class="admin-field">
                状态
                <select v-model="status">
                    <option value="">全部状态</option>
                    <option value="0">草稿</option>
                    <option value="1">已发布</option>
                    <option value="2">已结束</option>
                </select>
            </label>
            <button class="admin-btn" :disabled="busy">查询</button>
        </form>
        <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
        <p v-if="busy" role="status">正在加载…</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th>演出</th>
                        <th>城市 / 场馆</th>
                        <th>演出时间</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="event in records" :key="event.id">
                        <td>
                            <div class="event-cell">
                                <PosterImage
                                    class="admin-cover-thumb"
                                    :src="safeCover(event.coverImage)"
                                    :alt="event.name"
                                />
                                <div>
                                    <RouterLink :to="`/admin/events/${event.id}`">
                                        {{ event.name }}
                                    </RouterLink>
                                    <small>{{ event.artistName }}</small>
                                </div>
                            </div>
                        </td>
                        <td>
                            {{ event.cityName }}
                            <small>{{ event.venue }}</small>
                        </td>
                        <td>{{ timeText(event.eventStartTime) }}</td>
                        <td>
                            <span class="admin-badge">{{ eventStatus(event.status) }}</span>
                        </td>
                        <td>
                            <div class="admin-actions">
                                <RouterLink :to="`/admin/events/${event.id}/edit`">编辑</RouterLink>
                                <RouterLink
                                    :to="{ path: '/admin/sessions', query: { eventId: event.id } }"
                                >
                                    场次
                                </RouterLink>
                                <button
                                    class="admin-btn small danger"
                                    :disabled="busy || event.status === 0"
                                    @click="selected = event"
                                >
                                    下架
                                </button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <p v-if="!busy && !records.length" class="admin-empty">
            没有符合条件的演出，请调整筛选或新增演出。
        </p>
        <AdminPager :page="page" :size="10" :total="total" @change="search" />
        <AdminModal :open="!!selected" title="下架演出" :busy="busy" @close="selected = null">
            <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
            <p>确定下架“{{ selected?.name }}”？历史订单与关联记录将保留。</p>
            <button class="admin-btn danger" :disabled="busy" @click="remove">确认下架</button>
        </AdminModal>
    </section>
</template>
<style scoped>
.admin-cover-thumb { width: 44px; height: 60px; flex-shrink: 0; overflow: hidden; }
.admin-cover-thumb :deep(span) { display: none; }
.admin-cover-thumb :deep(svg) { min-width: 18px; }
.event-cell {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 240px;
}
.event-cell img {
    width: 44px;
    height: 60px;
    object-fit: cover;
    border-radius: 4px;
}
small {
    display: block;
    color: #777d8b;
    margin-top: 5px;
}
</style>
