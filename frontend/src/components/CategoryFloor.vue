<script setup lang="ts">
import { ArrowUpRight } from 'lucide-vue-next'
import type { Category, Event } from '../api/types'
import CategoryIcon from './CategoryIcon.vue'
import EventCard from './EventCard.vue'
import PosterImage from './PosterImage.vue'
import { dateLabel, money } from '../utils/format'

defineProps<{ category: Category; events: Event[]; cityId?: string; cityName?: string }>()
</script>

<template>
    <section v-if="events.length" class="category-floor" :aria-label="category.name + '推荐'">
        <div class="section-heading">
            <div class="floor-heading">
                <CategoryIcon :name="category.name" :size="24" />
                <h2>{{ category.name }}</h2>
                <span>{{ cityName || '全国' }}精选</span>
            </div>
            <RouterLink
                :to="{
                    path: '/events',
                    query: { categoryId: category.id, cityId: cityId || undefined },
                }"
            >
                查看全部
                <ArrowUpRight :size="16" />
            </RouterLink>
        </div>
        <div class="floor-content">
            <RouterLink :to="`/events/${events[0].id}`" class="floor-feature">
                <div class="floor-feature-poster">
                    <PosterImage :src="events[0].coverImage" :alt="events[0].name" />
                </div>
                <div class="floor-feature-copy">
                    <span class="floor-feature-city">
                        {{ events[0].cityName }} · {{ dateLabel(events[0].eventStartTime) }}
                    </span>
                    <h3>{{ events[0].name }}</h3>
                    <span v-if="events[0].minPrice != null" class="price">
                        <small>¥</small>
                        {{ money(events[0].minPrice) }}
                        <em>起</em>
                    </span>
                    <span v-else class="muted">暂无可售票档</span>
                </div>
            </RouterLink>
            <div v-if="events.length > 1" class="category-floor-list">
                <EventCard v-for="event in events.slice(1, 5)" :key="event.id" :event="event" />
            </div>
            <div v-else class="floor-invitation">
                <CategoryIcon :name="category.name" :size="46" />
                <h3>给生活，留一点现场的时间</h3>
                <p>从这一场开始，遇见不一样的热爱。</p>
                <RouterLink :to="`/events/${events[0].id}`">
                    查看这场演出
                    <ArrowUpRight :size="16" />
                </RouterLink>
            </div>
        </div>
    </section>
</template>
