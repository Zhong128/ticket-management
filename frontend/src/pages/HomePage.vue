<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowUpRight, Sparkles } from 'lucide-vue-next'
import { browse } from '../api/browse'
import type { Event, Home } from '../api/types'
import { useCatalogStore } from '../stores/catalog'
import EventCard from '../components/EventCard.vue'
import PosterImage from '../components/PosterImage.vue'
import LoadState from '../components/LoadState.vue'
import CategoryShortcuts from '../components/CategoryShortcuts.vue'
import CategoryFloor from '../components/CategoryFloor.vue'
import { dateLabel, isUpcomingEvent, money } from '../utils/format'

const catalog = useCatalogStore()
const loading = ref(true)
const error = ref('')
const events = ref<Event[]>([])
const home = ref<Home | null>(null)
const retry = ref(0)
const categoryGroups = computed(() => {
    const groups =
        catalog.cityId && home.value
            ? home.value.categories
            : catalog.categories.map((category) => ({
                  category,
                  events: events.value
                      .filter((event) => event.categoryId === category.id)
                      .slice(0, 5),
              }))
    const priority = [
        '演唱会',
        '歌剧话剧',
        '音乐剧',
        '儿童亲子',
        '音乐会',
        '脱口秀',
        '体育',
        '音乐节',
        '舞蹈芭蕾',
    ]
    return groups
        .filter((group) => group.events.length)
        .sort((a, b) => {
            const rank = (name: string) =>
                priority.includes(name) ? priority.indexOf(name) : priority.length
            return rank(a.category.name) - rank(b.category.name)
        })
        .slice(0, 8)
})
const featured = computed(
    () => events.value.find((event) => event.categoryName === '演唱会') || events.value[0],
)
const sideEvents = computed(() => {
    const candidates = events.value.filter((event) => event.id !== featured.value?.id)
    const first =
        candidates.find((event) => event.categoryId !== featured.value?.categoryId) || candidates[0]
    const second =
        candidates.find(
            (event) => event.id !== first?.id && event.categoryId !== first?.categoryId,
        ) || candidates.find((event) => event.id !== first?.id)
    return [first, second].filter((event): event is Event => !!event)
})
const discoveryEvents = computed(() => {
    const seen = new Set<string>()
    const diverse = events.value.filter((event) => {
        if (seen.has(event.categoryId)) return false
        seen.add(event.categoryId)
        return true
    })
    return [...diverse, ...events.value.filter((event) => !diverse.includes(event))].slice(0, 6)
})
watch(
    [() => catalog.cityId, retry],
    async ([cityId], _, onCleanup) => {
        const controller = new AbortController()
        onCleanup(() => controller.abort())
        loading.value = true
        error.value = ''
        try {
            const [page, recommendation] = await Promise.all([
                browse.events({ cityId: cityId || undefined, size: 100 }, controller.signal),
                cityId
                    ? browse.home(cityId, controller.signal).catch(() => null)
                    : Promise.resolve(null),
            ])
            if (controller.signal.aborted) return
            events.value = page.records.filter((event) => isUpcomingEvent(event))
            home.value = recommendation
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
    <div class="container home-page">
        <div class="page-intro">
            <span>
                <Sparkles :size="16" />
                好现场，就在身边
            </span>
            <span>{{ catalog.city?.name || '全国' }}演出指南</span>
        </div>
        <LoadState :loading="loading" :error="error" :empty="!events.length" @retry="retry++" />
        <template v-if="!loading && !error && featured">
            <section class="hero-grid" aria-label="精选演出">
                <RouterLink :to="`/events/${featured.id}`" class="hero-main">
                    <div class="hero-copy">
                        <span class="hero-tag">这一场，值得奔赴</span>
                        <h1>{{ featured.name }}</h1>
                        <p>{{ featured.cityName }} / {{ featured.venue }}</p>
                        <p>{{ dateLabel(featured.eventStartTime) }}</p>
                        <span class="hero-action">
                            查看演出
                            <ArrowUpRight :size="19" />
                        </span>
                    </div>
                    <div class="hero-poster">
                        <PosterImage :src="featured.coverImage" :alt="featured.name" eager />
                    </div>
                </RouterLink>
                <div class="hero-side">
                    <RouterLink
                        v-for="(event, index) in sideEvents"
                        :key="event.id"
                        :to="`/events/${event.id}`"
                        class="hero-small"
                        :class="{ lavender: index === 1 }"
                    >
                        <div>
                            <span class="small-feature-label">
                                {{ index === 0 ? '周末的好去处' : '换一种心情' }}
                            </span>
                            <h2>{{ event.name }}</h2>
                            <p>{{ event.cityName }} · {{ dateLabel(event.eventStartTime) }}</p>
                            <span v-if="event.minPrice != null" class="small-feature-price">
                                ¥{{ money(event.minPrice) }} 起
                            </span>
                        </div>
                        <div class="small-poster">
                            <PosterImage :src="event.coverImage" :alt="event.name" eager />
                        </div>
                    </RouterLink>
                </div>
            </section>
            <CategoryShortcuts :categories="catalog.categories" :city-id="catalog.cityId" />
            <section class="show-section">
                <div class="section-heading">
                    <div>
                        <h2>现在，去现场</h2>
                        <p>把屏幕里的喜欢，变成一次亲身经历。</p>
                    </div>
                    <RouterLink
                        :to="{ path: '/events', query: { cityId: catalog.cityId || undefined } }"
                    >
                        全部演出
                        <ArrowUpRight :size="16" />
                    </RouterLink>
                </div>
                <div class="event-grid">
                    <EventCard v-for="event in discoveryEvents" :key="event.id" :event="event" />
                </div>
            </section>
            <div class="discovery-strip">
                <div>
                    <span class="strip-mark">现场见</span>
                    <h2>总有一场，刚好是你的热爱。</h2>
                    <p>演唱会的合唱，剧场里的共鸣，和散场后仍在回响的瞬间。</p>
                </div>
                <RouterLink
                    :to="{ path: '/events', query: { cityId: catalog.cityId || undefined } }"
                    class="button primary"
                >
                    发现更多演出
                    <ArrowUpRight :size="17" />
                </RouterLink>
            </div>
            <CategoryFloor
                v-for="group in categoryGroups"
                :key="group.category.id"
                :category="group.category"
                :events="group.events"
                :city-id="catalog.cityId"
                :city-name="catalog.city?.name"
            />
        </template>
    </div>
</template>
