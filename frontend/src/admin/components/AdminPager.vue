<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ page: number; size: number; total: number }>()
defineEmits<{ change: [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template>
    <nav class="admin-pager" aria-label="列表分页">
        <span>共 {{ total }} 条</span>
        <div class="admin-actions">
            <button
                class="admin-btn small"
                :disabled="page <= 1"
                @click="$emit('change', page - 1)"
            >
                上一页
            </button>
            <span>{{ page }} / {{ pages }}</span>
            <button
                class="admin-btn small"
                :disabled="page >= pages"
                @click="$emit('change', page + 1)"
            >
                下一页
            </button>
        </div>
    </nav>
</template>
