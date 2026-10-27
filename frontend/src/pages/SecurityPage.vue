<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { LockKeyhole, Check } from 'lucide-vue-next'
import { account } from '../api/account'
import { validatePassword } from '../utils/account'
import AccountNav from '../components/AccountNav.vue'
const oldPassword = ref(''),
    next = ref(''),
    confirmation = ref(''),
    error = ref(''),
    busy = ref(false),
    saved = ref(false)
let disposed = false
onUnmounted(() => {
    disposed = true
    oldPassword.value = ''
    next.value = ''
    confirmation.value = ''
})
async function save() {
    if (busy.value) return
    saved.value = false
    error.value = validatePassword(oldPassword.value, next.value, confirmation.value)
    if (error.value) return
    busy.value = true
    try {
        await account.password(oldPassword.value, next.value)
        if (!disposed) {
            oldPassword.value = ''
            next.value = ''
            confirmation.value = ''
            saved.value = true
        }
    } catch (cause) {
        if (!disposed) error.value = cause instanceof Error ? cause.message : '修改失败，请重试'
    } finally {
        if (!disposed) busy.value = false
    }
}
</script>
<template>
    <div class="container account-page">
        <AccountNav />
        <div class="account-content">
            <div class="transaction-heading">
                <h1>账户安全</h1>
                <p>为你的账户换一把安心的钥匙。</p>
            </div>
            <section class="account-panel">
                <div class="section-title">
                    <LockKeyhole :size="23" />
                    <div>
                        <h2>修改登录密码</h2>
                        <p>使用当前密码验证身份，再设置6–20位新密码。</p>
                    </div>
                </div>
                <form class="account-form password-form" @submit.prevent="save">
                    <fieldset :disabled="busy">
                        <label>
                            当前密码
                            <input
                                v-model="oldPassword"
                                type="password"
                                autocomplete="current-password"
                                required
                                maxlength="100"
                            />
                        </label>
                        <label>
                            新密码
                            <input
                                v-model="next"
                                type="password"
                                autocomplete="new-password"
                                required
                                minlength="6"
                                maxlength="20"
                            />
                        </label>
                        <label>
                            确认新密码
                            <input
                                v-model="confirmation"
                                type="password"
                                autocomplete="new-password"
                                required
                                minlength="6"
                                maxlength="20"
                            />
                        </label>
                        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                        <p v-if="saved" class="save-success" role="status">
                            <Check :size="16" />
                            密码已修改，下次登录请使用新密码
                        </p>
                        <button class="button primary">
                            {{ busy ? '正在修改…' : '修改密码' }}
                        </button>
                    </fieldset>
                </form>
            </section>
        </div>
    </div>
</template>
