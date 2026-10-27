<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props = defineProps<{ open: boolean; title: string; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
let returnFocus: HTMLElement | null = null
watch(
    () => props.open,
    async (open) => {
        await nextTick()
        if (open && !dialog.value?.open) {
            returnFocus =
                document.activeElement instanceof HTMLElement ? document.activeElement : null
            dialog.value?.showModal()
        } else if (!open && dialog.value?.open) {
            dialog.value.close()
            returnFocus?.focus()
        }
    },
    { immediate: true },
)
onUnmounted(() => {
    dialog.value?.close()
    returnFocus?.focus()
})
</script>
<template>
    <dialog
        ref="dialog"
        class="admin-modal"
        :aria-label="title"
        @cancel.prevent="!busy && emit('close')"
    >
        <div class="admin-modal-heading">
            <h2>{{ title }}</h2>
            <button
                class="admin-icon-btn"
                aria-label="关闭弹窗"
                :disabled="busy"
                @click="emit('close')"
            >
                <X :size="20" />
            </button>
        </div>
        <slot />
    </dialog>
</template>
