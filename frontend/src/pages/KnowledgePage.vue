<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { support, type KnowledgeEntry } from '../api/support'
const route = useRoute()
const entry = ref<KnowledgeEntry>(), error = ref(''), loading = ref(false)
let generation = 0, controller = new AbortController()
watch(() => [route.params.entryId, route.query.version], async () => {
    const current = ++generation
    controller.abort()
    controller = new AbortController()
    entry.value = undefined
    error.value = ''
    loading.value = true
    const id = String(route.params.entryId || '')
    const rawVersion = route.query.version
    const version = rawVersion === undefined ? undefined : Number(rawVersion)
    if (!/^KB-[A-Z]+-\d{3}$/.test(id) || (rawVersion !== undefined &&
        (typeof rawVersion !== 'string' || !/^[1-9]\d*$/.test(rawVersion) || !Number.isSafeInteger(version)))) {
        error.value = '规则不存在或版本无效'
        loading.value = false
        return
    }
    try {
        const result = await support.knowledge(id, version, controller.signal)
        if (current === generation) entry.value = result
    } catch (cause) {
        if (current === generation) error.value = cause instanceof Error ? cause.message : '规则暂不可用'
    } finally {
        if (current === generation) loading.value = false
    }
}, { immediate: true })
onUnmounted(() => { generation++; controller.abort() })
</script>
<template>
    <div class="container knowledge-page">
        <RouterLink to="/support" class="text-button">返回客服</RouterLink>
        <p v-if="loading" role="status">正在加载规则…</p>
        <p v-else-if="error" class="form-error" role="alert">{{ error }}</p>
        <article v-else-if="entry" class="knowledge-card">
            <p>{{ entry.documentTitle }}</p>
            <h1>{{ entry.question }}</h1>
            <p>版本 {{ entry.version }} · 核对日期 {{ entry.reviewedAt }}</p>
            <p v-if="!entry.current" class="historical" role="status">历史版本，仅供追溯，不可作为现行规则。</p>
            <h2>规则正文</h2>
            <p class="knowledge-text">{{ entry.answer }}</p>
            <h2>需实时核验</h2>
            <p class="knowledge-text">{{ entry.liveCheck }}</p>
        </article>
    </div>
</template>
<style scoped>
.knowledge-page { max-width: 900px; padding-top: 32px; padding-bottom: 48px; }
.knowledge-card { background: white; border: 1px solid #e7e7eb; border-radius: 12px; padding: 28px; margin-top: 20px; overflow-wrap: anywhere; }
.knowledge-card h1 { font-size: 26px; line-height: 1.5; }
.knowledge-card h2 { font-size: 18px; margin-top: 28px; }
.knowledge-text { white-space: pre-wrap; line-height: 1.9; }
.historical { padding: 12px; background: #fff3df; color: #805715; border-radius: 8px; }
@media (max-width: 760px) { .knowledge-card { padding: 18px; } .knowledge-card h1 { font-size: 22px; } }
</style>
