<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import { trade } from '../api/trade'
const props = defineProps<{ orderId: string }>()
const emit = defineEmits<{ changed: [] }>()
const dialog = ref<HTMLDialogElement>()
const busy = ref(false)
const error = ref('')
function open() {
    error.value = ''
    dialog.value?.showModal()
}
async function confirm() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
        await trade.cancel(props.orderId)
        dialog.value?.close()
        emit('changed')
    } catch (cause) {
        error.value = cause instanceof Error ? cause.message : '取消暂未完成，请查询订单状态'
    } finally {
        busy.value = false
    }
}
</script>
<template>
    <button class="button secondary" @click="open">取消订单</button>
    <dialog ref="dialog" class="confirm-dialog">
        <div class="dialog-heading">
            <h2>确认取消这笔订单？</h2>
            <button
                class="icon-button"
                aria-label="关闭取消确认"
                :disabled="busy"
                @click="dialog?.close()"
            >
                <X :size="20" />
            </button>
        </div>
        <p>取消后将无法继续支付，库存释放需要等待渠道确认。请以订单的最新状态为准。</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <div class="result-actions">
            <button class="button secondary" :disabled="busy" @click="dialog?.close()">
                保留订单
            </button>
            <button class="button primary" :disabled="busy" @click="confirm">
                {{ busy ? '正在取消…' : '确认取消' }}
            </button>
        </div>
    </dialog>
</template>
