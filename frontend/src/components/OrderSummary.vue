<script setup lang="ts">
import PosterImage from './PosterImage.vue'
import { dateLabel, money } from '../utils/format'
defineProps<{
    title: string
    cover?: string
    venue: string
    time: string
    tier: string
    quantity: number
    amount: number
}>()
</script>
<template>
    <div class="order-summary">
        <div class="summary-poster"><PosterImage :src="cover" :alt="title" /></div>
        <div class="summary-content">
            <h2>{{ title }}</h2>
            <p>{{ dateLabel(time) }} {{ time?.slice(11, 16) }}</p>
            <p>{{ venue }}</p>
            <p class="summary-tier">
                {{ tier }}
                <span>× {{ quantity }} 张</span>
            </p>
        </div>
        <div class="summary-amount">
            <small>订单金额</small>
            <strong>¥{{ money(amount) }}</strong>
        </div>
    </div>
</template>
