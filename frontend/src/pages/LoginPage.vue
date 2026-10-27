<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, LockKeyhole, Mail, Ticket } from 'lucide-vue-next'
import { trade } from '../api/trade'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { safeRedirect } from '../utils/purchase'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const code = ref('')
const verifying = ref(false)
const busy = ref(false)
const error = ref('')
const sentUntil = ref(0)
const now = ref(Date.now())
const cooldown = computed(() => Math.max(0, Math.ceil((sentUntil.value - now.value) / 1000)))
const timer = setInterval(() => {
    now.value = Date.now()
}, 1000)
let disposed = false
onUnmounted(() => {
    disposed = true
    clearInterval(timer)
    password.value = ''
    code.value = ''
})
async function submit() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    const initialToken = auth.user?.token
    const redirect = safeRedirect(route.query.redirect)
    try {
        const info = verifying.value
            ? await trade.verify(email.value.trim(), code.value.trim(), password.value)
            : await trade.login(email.value.trim(), password.value)
        if (disposed || auth.user?.token !== initialToken) return
        auth.accept(info)
        password.value = ''
        await router.replace(redirect)
    } catch (cause) {
        if (disposed) return
        if (cause instanceof ApiError && cause.code === 200) {
            verifying.value = true
            sentUntil.value = Date.now() + 60000
        } else error.value = cause instanceof Error ? cause.message : '登录失败，请稍后重试'
    } finally {
        busy.value = false
    }
}
function changeEmail() {
    verifying.value = false
    error.value = ''
    code.value = ''
}
async function resend() {
    if (busy.value || cooldown.value) return
    busy.value = true
    error.value = ''
    try {
        await trade.resend(email.value.trim())
        sentUntil.value = Date.now() + 60000
    } catch (cause) {
        error.value = cause instanceof Error ? cause.message : '发送失败，请稍后重试'
    } finally {
        busy.value = false
    }
}
</script>
<template>
    <div class="container login-page">
        <section class="login-story">
            <Ticket :size="50" />
            <h1>
                下一次心动，
                <br />
                在现场见。
            </h1>
            <p>一张门票，连接你和期待已久的那一刻。</p>
            <div class="login-ticket">
                <span>有戏</span>
                <p>把喜欢，留给现场。</p>
                <span class="ticket-stub">与你相约</span>
            </div>
        </section>
        <section class="login-form-panel">
            <span class="detail-category">欢迎来到有戏</span>
            <h2>{{ verifying ? '验证你的邮箱' : '登录 / 注册' }}</h2>
            <p class="login-description">
                {{
                    verifying
                        ? `验证码已发送至 ${email}，验证后完成注册。`
                        : '使用邮箱和密码登录，未注册邮箱将进入验证流程。'
                }}
            </p>
            <form @submit.prevent="submit">
                <label v-if="!verifying" class="field-label" for="email">邮箱</label>
                <div v-if="!verifying" class="field-input">
                    <Mail :size="18" />
                    <input
                        id="email"
                        :disabled="busy"
                        v-model="email"
                        type="email"
                        autocomplete="email"
                        placeholder="请输入邮箱"
                        required
                        maxlength="100"
                    />
                </div>
                <label class="field-label" for="password">
                    {{ verifying ? '账户密码' : '密码' }}
                </label>
                <div class="field-input">
                    <LockKeyhole :size="18" />
                    <input
                        id="password"
                        :disabled="busy"
                        v-model="password"
                        type="password"
                        :autocomplete="verifying ? 'new-password' : 'current-password'"
                        placeholder="6–20 位密码"
                        minlength="6"
                        maxlength="20"
                        required
                    />
                </div>
                <template v-if="verifying">
                    <label class="field-label" for="verification-code">邮箱验证码</label>
                    <div class="verification-field">
                        <input
                            id="verification-code"
                            :disabled="busy"
                            v-model="code"
                            inputmode="numeric"
                            autocomplete="one-time-code"
                            pattern="[0-9]{4,6}"
                            maxlength="6"
                            placeholder="4–6 位验证码"
                            required
                        />
                        <button type="button" :disabled="busy || cooldown > 0" @click="resend">
                            {{ cooldown ? `${cooldown} 秒后重发` : '重新发送' }}
                        </button>
                    </div>
                </template>
                <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                <button class="button primary full-width" :disabled="busy">
                    {{ busy ? '正在处理…' : verifying ? '验证并登录' : '登录 / 注册' }}
                </button>
                <button
                    v-if="verifying"
                    type="button"
                    class="text-button"
                    :disabled="busy"
                    @click="changeEmail"
                >
                    <ArrowLeft :size="14" />
                    更换邮箱
                </button>
            </form>
            <p class="login-footnote">登录后可在“我的订单”查看购票申请和付款进度。</p>
        </section>
    </div>
</template>
