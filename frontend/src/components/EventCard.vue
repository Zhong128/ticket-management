<script setup lang="ts">
import type { Event } from '../api/types'
import PosterImage from './PosterImage.vue'
import { dateLabel, money, saleState } from '../utils/format'
defineProps<{ event: Event }>()
</script>
<template>
    <RouterLink :to="`/events/${event.id}`" class="event-card">
        <div class="card-poster">
            <PosterImage :src="event.coverImage" :alt="event.name" />
            <span class="city-badge">{{ event.cityName || '演出' }}</span>
        </div>
        <h3>{{ event.name }}</h3>
        <p class="event-meta">{{ dateLabel(event.eventStartTime) }}</p>
        <p class="event-meta venue-line">{{ event.venue }}</p>
        <div class="card-bottom">
            <span v-if="event.minPrice != null" class="price">
                <small>¥</small>
                {{ money(event.minPrice) }}
                <em>起</em>
            </span>
            <span v-else class="muted">暂无可售票档</span>
            <span class="sale-label">{{ saleState(event) }}</span>
        </div>
    </RouterLink>
</template>
