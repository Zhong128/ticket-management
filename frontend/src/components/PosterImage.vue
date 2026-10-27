<script setup lang="ts">
import { ref, watch } from 'vue'
import { Ticket } from 'lucide-vue-next'
const props = defineProps<{ src?: string; alt: string; eager?: boolean }>()
const broken = ref(false)
watch(
    () => props.src,
    () => {
        broken.value = false
    },
)
</script>
<template>
    <img
        v-if="src && !broken"
        :src="src"
        :alt="alt"
        :loading="eager ? 'eager' : 'lazy'"
        referrerpolicy="no-referrer"
        @error="broken = true"
    />
    <div v-else class="poster-fallback" role="img" :aria-label="alt + '，海报暂不可用'">
        <Ticket :size="32" />
        <span>{{ alt }}</span>
    </div>
</template>
