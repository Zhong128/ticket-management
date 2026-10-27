<script setup lang="ts">
import { SearchX, WifiOff } from 'lucide-vue-next'
defineProps<{ loading?: boolean; error?: string; empty?: boolean; message?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
    <div v-if="loading" class="loading-grid" role="status" aria-label="正在加载内容">
        <div v-for="i in 6" :key="i" class="skeleton">
            <div></div>
            <p></p>
            <p></p>
        </div>
        <span class="sr-only">正在加载内容</span>
    </div>
    <div v-else-if="error || empty" class="state-panel" role="status">
        <WifiOff v-if="error" :size="36" />
        <SearchX v-else :size="36" />
        <h2>{{ error ? '暂时没能加载' : '还没有找到合适的演出' }}</h2>
        <p>{{ error || message || '试试切换城市、分类，或换一个关键词。' }}</p>
        <button v-if="error" class="button secondary" @click="$emit('retry')">重新加载</button>
        <RouterLink v-else to="/events" class="button secondary">查看全部演出</RouterLink>
    </div>
</template>
