<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Check, Clock3, RotateCw, Ticket } from 'lucide-vue-next'
import { trade } from '../api/trade'
import type { Order, Payment } from '../api/tradeTypes'
import { useAuthStore } from '../stores/auth'
import { usePoll } from '../composables/usePoll'
const route=useRoute(),auth=useAuthStore()
const paymentNo=typeof route.query.out_trade_no==='string'?route.query.out_trade_no:''
const payment=ref<Payment|null>(null),order=ref<Order|null>(null),error=ref('')
const paid=computed(()=>!!order.value && [1,3].includes(order.value.orderStatus))
const title=computed(()=>payment.value?.payStatus===5?'该笔支付已退款':paid.value?'支付成功':order.value?.orderStatus===2?'订单已取消':'付款结果待确认')
const poll=usePoll(async signal=>{
    if(!auth.loggedIn)return false
    if(!/^[A-Za-z0-9_-]{1,64}$/.test(paymentNo)){error.value='缺少有效的支付流水，请到我的订单查询';return false}
    try{
        const result=await trade.payment(paymentNo,signal)
        if(signal.aborted)return false
        payment.value=result
        if(result.orderId){
            const current=await trade.order(result.orderId,signal)
            if(signal.aborted)return false
            order.value=current
        }
        error.value=''
        return !paid.value && order.value?.orderStatus!==2 && result.payStatus!==5
    }catch(cause){if(!signal.aborted)error.value=cause instanceof Error?cause.message:'暂时无法查询付款结果';return true}
})
</script>
<template><div class="container transaction-page"><section class="transaction-panel result-panel"><div class="result-symbol" :class="{paid}"><Check v-if="paid" :size="38"/><Clock3 v-else :size="38"/></div><h1>{{title}}</h1><p>付款结果由服务端查询确认，浏览器回跳不会直接改变订单状态。</p><p v-if="order?.orderNo" class="request-reference">订单号：{{order.orderNo}}</p><p v-if="error" class="form-error" role="alert">{{error}}</p><p v-if="poll.exhausted.value" class="inline-notice">自动查询已暂停，可手动查询或到订单页确认。</p><div class="result-actions"><RouterLink v-if="!auth.loggedIn" :to="{path:'/login',query:{redirect:route.fullPath}}" class="button primary">登录后查询付款结果</RouterLink><template v-else><button class="button secondary" :disabled="poll.running.value" @click="poll.restart"><RotateCw :size="15"/>查询最新结果</button><RouterLink v-if="order" :to="`/account/orders/${order.id}`" class="button primary">查看订单详情</RouterLink></template><RouterLink to="/account/orders" class="text-button"><Ticket :size="16"/>我的订单</RouterLink></div></section></div></template>
