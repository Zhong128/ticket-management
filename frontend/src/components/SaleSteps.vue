<script setup lang="ts">
import { Check } from 'lucide-vue-next'
defineProps<{ step: number }>()
</script>
<template>
    <ol class="sale-steps" aria-label="购票进度">
        <li
            v-for="(label, index) in ['确认购票信息', '等待订单创建', '支付订单']"
            :key="label"
            :class="{ current: step === index + 1, done: step > index + 1 }"
            :aria-current="step === index + 1 ? 'step' : undefined"
        >
            <span>
                <Check v-if="step > index + 1" :size="15" />
                <template v-else>{{ index + 1 }}</template>
            </span>
            {{ label }}
        </li>
    </ol>
</template>
