<script setup lang="ts">
import { computed } from 'vue'
import {
    Baby,
    Clapperboard,
    Drum,
    Footprints,
    Laugh,
    MicVocal,
    Music2,
    Piano,
    Theater,
    Ticket,
    Trophy,
} from 'lucide-vue-next'

const props = defineProps<{ name: string; size?: number }>()
const icons: Record<string, typeof Ticket> = {
    演唱会: MicVocal,
    音乐节: Drum,
    音乐剧: Music2,
    歌剧话剧: Theater,
    话剧歌剧: Theater,
    儿童亲子: Baby,
    亲子儿童: Baby,
    脱口秀: Laugh,
    音乐会: Piano,
    舞蹈芭蕾: Footprints,
    体育: Trophy,
    电影: Clapperboard,
}
const icon = computed(() => icons[props.name] || Ticket)
</script>

<template>
    <component :is="icon" :size="size || 32" :stroke-width="1.5" aria-hidden="true" />
</template>
