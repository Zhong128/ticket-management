<script setup lang="ts">
import { orderLabel } from '../utils/purchase'
defineProps<{ status: number }>()
</script>
<template>
    <span class="order-state" :class="`order-state-${status}`">{{ orderLabel(status) }}</span>
</template>
