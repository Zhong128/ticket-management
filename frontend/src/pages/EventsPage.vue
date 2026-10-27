<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, SlidersHorizontal, X } from 'lucide-vue-next'
import { browse } from '../api/browse'
import type { Event, EventFilters, Page } from '../api/types'
import { useCatalogStore } from '../stores/catalog'
import EventCard from '../components/EventCard.vue'
import LoadState from '../components/LoadState.vue'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()
const loading = ref(true)
const error = ref('')
const result = ref<Page<Event> | null>(null)
const retry = ref(0)
const text = (key: string) =>
    typeof route.query[key] === 'string' ? (route.query[key] as string) : undefined
const page = computed(() => Math.max(1, Number(text('page')) || 1))
const filters = computed<EventFilters>(() => ({
    keyword: text('name'),
    artistName: text('artistName'),
    cityId: text('cityId'),
    categoryId: text('categoryId'),
    page: page.value,
    size: 12,
}))

function filter(key: string, value?: string) {
    router.push({
        path: '/events',
        query: { ...route.query, [key]: value || undefined, page: undefined },
    })
}
function changePage(value: number) {
    router.push({ path: '/events', query: { ...route.query, page: String(value) } })
    window.scrollTo({ top: 200, behavior: 'instant' })
}
watch(
    [filters, retry],
    async ([params], _, onCleanup) => {
        const controller = new AbortController()
        onCleanup(() => controller.abort())
        loading.value = true
        error.value = ''
        try {
            const data = await browse.events(params, controller.signal)
            if (!controller.signal.aborted) result.value = data
        } catch (cause) {
            if (!controller.signal.aborted)
                error.value = cause instanceof Error ? cause.message : '演出加载失败'
        } finally {
            if (!controller.signal.aborted) loading.value = false
        }
    },
    { immediate: true },
)
</script>
<template>
    <div class="container listing-page">
        <div class="listing-title">
            <div>
                <p class="breadcrumb">
                    <RouterLink to="/">首页</RouterLink>
                    / 发现演出
                </p>
                <h1>{{ text('name') ? `“${text('name')}”的演出` : '发现你的下一场热爱' }}</h1>
            </div>
            <SlidersHorizontal :size="24" />
        </div>
        <section class="filter-panel" aria-label="筛选演出">
            <div class="filter-row">
                <span class="filter-label">城市</span>
                <div>
                    <button :class="{ active: !text('cityId') }" @click="filter('cityId')">
                        全国
                    </button>
                    <button
                        v-for="city in catalog.cities"
                        :key="city.id"
                        :class="{ active: text('cityId') === city.id }"
                        @click="filter('cityId', city.id)"
                    >
                        {{ city.name }}
                    </button>
                </div>
            </div>
            <div class="filter-row">
                <span class="filter-label">分类</span>
                <div>
                    <button :class="{ active: !text('categoryId') }" @click="filter('categoryId')">
                        全部
                    </button>
                    <button
                        v-for="category in catalog.categories"
                        :key="category.id"
                        :class="{ active: text('categoryId') === category.id }"
                        @click="filter('categoryId', category.id)"
                    >
                        {{ category.name }}
                    </button>
                </div>
            </div>
            <div v-if="text('name') || text('artistName')" class="filter-row">
                <span class="filter-label">关键词</span>
                <div>
                    <button v-if="text('name')" class="keyword-chip" @click="filter('name')">
                        {{ text('name') }}
                        <X :size="13" />
                    </button>
                    <button
                        v-if="text('artistName')"
                        class="keyword-chip"
                        @click="filter('artistName')"
                    >
                        艺人：{{ text('artistName') }}
                        <X :size="13" />
                    </button>
                </div>
            </div>
        </section>
        <div class="results-heading">
            <h2>
                演出列表
                <span v-if="!loading && !error">{{ result?.total || 0 }} 场</span>
            </h2>
            <span class="muted">按演出时间排列</span>
        </div>
        <LoadState
            :loading="loading"
            :error="error"
            :empty="!result?.records.length"
            @retry="retry++"
        />
        <template v-if="!loading && !error && result?.records.length">
            <div class="event-grid listing-grid">
                <EventCard v-for="event in result.records" :key="event.id" :event="event" />
            </div>
            <nav v-if="result.totalPages > 1" class="pagination" aria-label="演出列表分页">
                <button :disabled="result.page <= 1" @click="changePage(result.page - 1)">
                    <ArrowLeft :size="16" />
                    上一页
                </button>
                <span>第 {{ result.page }} / {{ result.totalPages }} 页</span>
                <button
                    :disabled="result.page >= result.totalPages"
                    @click="changePage(result.page + 1)"
                >
                    下一页
                    <ArrowRight :size="16" />
                </button>
            </nav>
        </template>
    </div>
</template>
