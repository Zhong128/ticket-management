<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Ticket, CalendarDays, Armchair } from 'lucide-vue-next'
import { request } from '../../api/client'
import type { LoginInfo } from '../../api/tradeTypes'
import { useAdminAuth, isAdminRole } from '../auth'
const username = ref(''),
    password = ref(''),
    busy = ref(false),
    error = ref('')
const route = useRoute(),
    router = useRouter(),
    auth = useAdminAuth()
let controller: AbortController | undefined,
    disposed = false
onUnmounted(() => {
    disposed = true
    controller?.abort()
})
async function login() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    controller = new AbortController()
    const signal = controller.signal
    try {
        const info = await request<LoginInfo>(
            'post',
            '/auth/admin/login',
            { username: username.value.trim(), password: password.value },
            undefined,
            signal,
            12000,
            'admin',
        )
        if (disposed || signal.aborted) return
        const who = await request<{ role: string }>(
            'get',
            '/auth/userinfo',
            undefined,
            undefined,
            signal,
            12000,
            'admin',
            info.token,
        )
        if (disposed || signal.aborted) return
        if (!isAdminRole(who.role)) throw new Error('该账号没有管理权限，请使用管理员账号')
        auth.accept({ ...info, role: who.role })
        password.value = ''
        const target = route.query.redirect
        const safe =
            typeof target === 'string' &&
            /^\/admin\/(?!login(?:[/?#]|$))/.test(target) &&
            !/[\\\r\n]/.test(target)
        await router.replace(safe ? target : '/admin/dashboard')
    } catch (cause) {
        if (!disposed && !signal.aborted)
            error.value = cause instanceof Error ? cause.message : '登录失败，请重试'
    } finally {
        if (!disposed) busy.value = false
    }
}
</script>
<template>
    <div class="admin-app admin-login">
        <section class="admin-login-story">
            <RouterLink to="/" class="admin-brand">
                <Ticket :size="32" />
                <span>
                    有戏
                    <small>演出运营</small>
                </span>
            </RouterLink>
            <div>
                <h1>
                    把每一场
                    <br />
                    安排妥当。
                </h1>
                <p>
                    从第一张海报，到最后一张票。
                    <br />
                    让值得奔赴的现场，顺利发生。
                </p>
                <div class="admin-login-tickets">
                    <span>
                        <Ticket :size="22" />
                        演出上架
                    </span>
                    <span>
                        <CalendarDays :size="22" />
                        场次排期
                    </span>
                    <span>
                        <Armchair :size="22" />
                        票档管理
                    </span>
                </div>
            </div>
            <small>有戏演出运营管理</small>
        </section>
        <section class="admin-login-form">
            <div>
                <span class="admin-login-mark"><Ticket :size="25" /></span>
                <h2>欢迎回来</h2>
                <p class="admin-muted">使用管理员账号进入工作台</p>
                <form class="admin-form" @submit.prevent="login">
                    <label class="admin-field">
                        用户名
                        <input
                            v-model="username"
                            name="username"
                            autocomplete="username"
                            required
                            minlength="3"
                            maxlength="20"
                            :disabled="busy"
                        />
                    </label>
                    <label class="admin-field">
                        密码
                        <input
                            v-model="password"
                            type="password"
                            name="password"
                            autocomplete="current-password"
                            required
                            minlength="6"
                            maxlength="20"
                            :disabled="busy"
                        />
                    </label>
                    <p v-if="error" role="alert" class="admin-message error">{{ error }}</p>
                    <button class="admin-btn primary" :disabled="busy">
                        {{ busy ? '正在验证…' : '登录管理端' }}
                        <ArrowRight :size="17" />
                    </button>
                </form>
                <RouterLink to="/" class="admin-login-back">返回用户端</RouterLink>
            </div>
        </section>
    </div>
</template>
