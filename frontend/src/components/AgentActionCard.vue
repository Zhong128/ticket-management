<script setup lang="ts">
import { computed, ref } from 'vue'
import { actionOrderPath, canConfirmAction, type AgentAction } from '../api/support'

const props = defineProps<{
    action: AgentAction
    runSucceeded: boolean
    runPending: boolean
    available: boolean
    processing: boolean
    now: number
}>()
const emit = defineEmits<{ confirm: []; dismiss: []; refresh: [] }>()
const dialog = ref<HTMLDialogElement>()
const allowed = computed(() => canConfirmAction(props.action, props.runSucceeded, props.available, props.now) && !props.processing)
const expired = computed(() => props.action.status === 'PENDING' && props.now >= props.action.expiresEpochMillis)
const labels = {
    PENDING: '待你确认', EXECUTING: '已确认，处理中', SUCCEEDED: '已核验结果', REJECTED: '未能执行',
    UNKNOWN: '结果待核实', DISMISSED: '已放弃', EXPIRED: '草稿已失效',
}
const money = (value: number) => `¥${value.toFixed(2)}`
function confirm() {
    if (!allowed.value) return
    dialog.value?.close()
    emit('confirm')
}
</script>

<template>
    <section class="agent-action-card" :aria-label="action.type === 'PURCHASE' ? '购票确认卡片' : '取消确认卡片'" :data-action-id="action.id">
        <div class="action-heading"><strong>{{ action.title }}</strong><span>{{ expired ? '草稿已到期' : labels[action.status] }}</span></div>
        <dl>
            <div><dt>演出</dt><dd>{{ action.eventName }}</dd></div>
            <div><dt>场次</dt><dd>{{ action.sessionName }}<template v-if="action.sessionTime"> · {{ action.sessionTime.replace('T', ' ') }}</template></dd></div>
            <div><dt>票档</dt><dd>{{ action.tierName }}</dd></div>
            <div><dt>数量</dt><dd>{{ action.quantity }} 张</dd></div>
            <div><dt>{{ action.type === 'PURCHASE' ? '确认金额' : '订单金额' }}</dt><dd class="action-amount">{{ money(action.totalAmount) }}</dd></div>
        </dl>
        <p v-if="action.status === 'PENDING'" class="action-hint">{{ action.type === 'PURCHASE' ? '确认后提交购票申请，不会自动付款；库存和资格以受理结果为准。' : '仅取消这笔本人未支付订单，不发起退款。' }}</p>
        <p v-if="action.status === 'PENDING'" class="action-hint">有效至 {{ action.expiresAt.replace('T', ' ') }}。价格或场次变化需重新确认。</p>
        <p v-if="action.status === 'PENDING' && !runSucceeded" class="action-hint">{{ runPending ? '正在生成回复，完成后才能确认。' : '来源回复尚未核实成功，此草稿不可确认。' }}</p>
        <p v-if="!available" class="action-hint">当前功能维护中，暂不能确认交易。</p>
        <p v-if="action.message" class="action-result" role="status">{{ action.message }}</p>
        <p v-if="action.status === 'UNKNOWN' || action.status === 'EXECUTING'" class="action-hint">后台结果仍需核实。请查询原结果，不要重复申请；离开页面不会取消已确认的操作。</p>
        <div class="action-buttons">
            <template v-if="action.status === 'PENDING'">
                <button class="button primary" :disabled="!allowed" @click="dialog?.showModal()">{{ action.type === 'PURCHASE' ? '核对购票申请' : '核对取消订单' }}</button>
                <button class="button secondary" :disabled="processing" @click="emit('dismiss')">放弃草稿</button>
            </template>
            <button v-if="action.status === 'UNKNOWN' || action.status === 'EXECUTING' || expired" class="button secondary" :disabled="processing" @click="emit('refresh')">查询原结果</button>
            <RouterLink v-if="actionOrderPath(action)" :to="actionOrderPath(action)!" class="text-button">查看这笔订单</RouterLink>
        </div>
        <dialog ref="dialog" class="confirm-dialog">
            <h2>{{ action.type === 'PURCHASE' ? '确认提交购票申请？' : '确认取消这笔订单？' }}</h2>
            <p>{{ action.eventName }} · {{ action.sessionName }} · {{ action.tierName }}</p>
            <p>{{ action.quantity }} 张，{{ action.type === 'PURCHASE' ? '申请金额' : '订单金额' }} {{ money(action.totalAmount) }}</p>
            <p>{{ action.type === 'PURCHASE' ? '只提交本卡片中的申请，成功生成订单后仍需你前往支付。' : '取消后不能恢复这笔未支付订单，请核对目标。' }}</p>
            <div class="result-actions">
                <button class="button secondary" @click="dialog?.close()">返回核对</button>
                <button class="button primary" :disabled="!allowed" @click="confirm">{{ action.type === 'PURCHASE' ? '确认提交购票申请' : '确认取消此订单' }}</button>
            </div>
        </dialog>
    </section>
</template>

<style scoped>
.agent-action-card { margin-top: 12px; border: 1px solid #e5d2da; border-radius: 10px; padding: 16px; background: #fff; font-size: 14px; overflow-wrap: anywhere; }
.action-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; }
.action-heading span { font-size: 12px; color: #a52152; }
dl { margin: 14px 0; }
dl > div { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: 8px; margin: 7px 0; }
dt { color: #72727e; }
dd { margin: 0; }
.action-amount { color: #c41c5e; font-weight: 600; }
.action-hint { color: #6e6e7c; line-height: 1.7; font-size: 12px; margin: 8px 0; }
.action-result { line-height: 1.7; }
.action-buttons { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; align-items: center; }
.action-buttons .button { padding: 8px 12px; font-size: 13px; }
.confirm-dialog { max-width: min(480px, calc(100vw - 32px)); }
</style>
