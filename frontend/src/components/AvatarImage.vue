<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { UserRound } from 'lucide-vue-next'
const props = defineProps<{ src?: string | null; alt: string; iconSize?: number }>()
const broken = ref(false)
const source = computed(() => {
    const value = props.src || ''
    return /^(https?:\/\/|blob:|\/media\/avatars\/)/i.test(value) ? value : ''
})
watch(() => props.src, () => {
    broken.value = false
})
</script>
<template>
    <img
        v-if="source && !broken"
        class="user-avatar-image"
        :src="source"
        :alt="alt"
        referrerpolicy="no-referrer"
        @error="broken = true"
    />
    <span v-else class="user-avatar-image" role="img" :aria-label="alt + '，默认头像'">
        <UserRound :size="iconSize || 24" />
    </span>
</template>
<style scoped>
.user-avatar-image {
    width: 100%;
    height: 100%;
    display: grid;
    place-items: center;
    border-radius: inherit;
    object-fit: cover;
    overflow: hidden;
}
img.user-avatar-image {
    display: block;
}
</style>
