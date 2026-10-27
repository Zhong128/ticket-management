<script setup lang="ts">
import { computed, ref } from 'vue'
import { ReceiptText, RotateCw, ArrowUpRight } from 'lucide-vue-next'
import { account } from '../api/account'
import type { Refund } from '../api/accountTypes'
import { refundLabel, displayTime } from '../utils/account'
import { money } from '../utils/format'
import { usePoll } from '../composables/usePoll'
import AccountNav from '../components/AccountNav.vue'
import LoadState from '../components/LoadState.vue'
const records = ref<Refund[]>([]),
    loading = ref(true),
    error = ref(''),
    filter = ref('all')
const shown = computed(() =>
    records.value.filter(
        (r) =>
            filter.value === 'all' ||
            (filter.value === 'success' ? r.status === 'SUCCESS' : r.status !== 'SUCCESS'),
    ),
)
const poll = usePoll(async (signal) => {
    try {
        const data = await account.refunds(signal)
        if (signal.aborted) return false
        records.value = data
        error.value = ''
        return data.some((r) =>
            ['REQUESTED', 'PROCESSING', 'UNKNOWN', 'RETRYABLE_FAILED'].includes(r.status),
        )
    } catch (cause) {
        if (!signal.aborted)
            error.value = cause instanceof Error ? cause.message : '退款记录加载失败'
        return false
    } finally {
        if (!signal.aborted) loading.value = false
    }
})
</script>
<template>
    <div class="container account-page">
        <AccountNav />
        <div class="account-content">
            <div class="transaction-heading account-heading">
                <div>
                    <h1>退款记录</h1>
                    <p>查看退款进度，关注每一笔去向。</p>
                </div>
                <button class="text-button" :disabled="poll.running.value" @click="poll.restart">
                    <RotateCw :size="15" />
                    刷新记录
                </button>
            </div>
            <p class="inline-notice">
                这里展示系统因迟到或重复支付生成的退款记录，退款结果以服务端确认为准。
            </p>
            <nav class="order-tabs" aria-label="退款筛选">
                <button :class="{ active: filter === 'all' }" @click="filter = 'all'">
                    全部记录
                </button>
                <button :class="{ active: filter === 'pending' }" @click="filter = 'pending'">
                    处理中
                </button>
                <button :class="{ active: filter === 'success' }" @click="filter = 'success'">
                    退款成功
                </button>
            </nav>
            <LoadState :loading="loading" :error="error" @retry="poll.restart" />
            <template v-if="!loading && !error">
                <div v-if="!shown.length" class="account-panel account-empty">
                    <ReceiptText :size="38" />
                    <h2>{{ records.length ? '该分类暂无记录' : '暂无退款记录' }}</h2>
                    <p>如有退款处理，进度会显示在这里。</p>
                    <RouterLink to="/account/orders" class="button secondary">
                        查看我的订单
                    </RouterLink>
                </div>
                <article
                    v-for="refund in shown"
                    :key="refund.refund_no"
                    class="refund-card account-panel"
                >
                    <div class="refund-card-heading">
                        <span>退款编号 {{ refund.refund_no }}</span>
                        <span
                            class="refund-state"
                            :class="{ success: refund.status === 'SUCCESS' }"
                        >
                            {{ refundLabel(refund.status) }}
                        </span>
                    </div>
                    <div class="refund-amount">
                        <div>
                            <p>
                                {{
                                    refund.reason === 'LATE_OR_DUPLICATE_PAYMENT'
                                        ? '迟到或重复支付退款'
                                        : refund.reason || '支付退款'
                                }}
                            </p>
                            <small>申请时间 {{ displayTime(refund.created_at) }}</small>
                        </div>
                        <strong>¥{{ money(refund.amount) }}</strong>
                    </div>
                    <div class="refund-card-footer">
                        <span>更新于 {{ displayTime(refund.updated_at) }}</span>
                        <RouterLink :to="`/account/orders/${refund.order_id}`" class="text-button">
                            查看关联订单
                            <ArrowUpRight :size="14" />
                        </RouterLink>
                    </div>
                    <p v-if="refund.status === 'MANUAL_REQUIRED'" class="form-hint">
                        当前退款需要人工核实，请保留退款编号联系平台处理。
                    </p>
                </article>
                <p class="form-hint">
                    显示最近100条记录。
                    <template v-if="poll.exhausted.value">
                        自动查询已暂停，可手动刷新继续。
                    </template>
                </p>
            </template>
        </div>
    </div>
</template>
