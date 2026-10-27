<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { UserRound, Check } from 'lucide-vue-next'
import { account } from '../api/account'
import type { Profile } from '../api/accountTypes'
import { profilePayload } from '../utils/account'
import { useAuthStore } from '../stores/auth'
import AccountNav from '../components/AccountNav.vue'
import LoadState from '../components/LoadState.vue'
const auth = useAuthStore()
const form = reactive<Profile>({
    username: '',
    email: '',
    nickname: '',
    phone: '',
    realName: '',
    gender: 0,
    birthday: '',
})
const loading = ref(true),
    error = ref(''),
    saveError = ref(''),
    saved = ref(false),
    busy = ref(false)
let disposed = false
let controller: AbortController | undefined
onUnmounted(() => {
    disposed = true
    controller?.abort()
})
async function load() {
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    error.value = ''
    try {
        const data = await account.profile(signal)
        if (!signal.aborted)
            Object.assign(
                form,
                { nickname: '', phone: '', realName: '', gender: 0, birthday: '' },
                data,
            )
    } catch (cause) {
        if (!signal.aborted) error.value = cause instanceof Error ? cause.message : '资料加载失败'
    } finally {
        if (!signal.aborted) loading.value = false
    }
}
async function save() {
    if (busy.value) return
    const current = auth.user
    busy.value = true
    saveError.value = ''
    saved.value = false
    try {
        await account.saveProfile(profilePayload(form))
        if (disposed || auth.user?.token !== current?.token) return
        saved.value = true
        if (current) auth.accept({ ...current, username: form.username.trim() })
    } catch (cause) {
        if (!disposed) saveError.value = cause instanceof Error ? cause.message : '保存失败，请重试'
    } finally {
        if (!disposed) busy.value = false
    }
}
onMounted(load)
</script>
<template>
    <div class="container account-page">
        <AccountNav />
        <div class="account-content">
            <div class="transaction-heading">
                <h1>个人资料</h1>
                <p>完善你的资料，让每一次相约更方便。</p>
            </div>
            <LoadState :loading="loading" :error="error" @retry="load" />
            <section v-if="!loading && !error" class="account-panel">
                <div class="profile-intro">
                    <div class="profile-avatar"><UserRound :size="32" /></div>
                    <div>
                        <h2>{{ form.nickname || form.username }}</h2>
                        <p>{{ form.email }}</p>
                    </div>
                </div>
                <form class="account-form" @submit.prevent="save" @input="saved = false">
                    <fieldset :disabled="busy">
                        <div class="form-grid">
                            <label>
                                用户名
                                <input
                                    v-model="form.username"
                                    autocomplete="username"
                                    required
                                    minlength="3"
                                    maxlength="20"
                                />
                            </label>
                            <label>
                                昵称
                                <input
                                    v-model="form.nickname"
                                    maxlength="20"
                                    placeholder="现场朋友怎么称呼你"
                                />
                            </label>
                            <label>
                                登录邮箱
                                <input
                                    :value="form.email"
                                    type="email"
                                    readonly
                                    class="readonly-input"
                                />
                            </label>
                            <label>
                                手机号
                                <input
                                    v-model="form.phone"
                                    type="tel"
                                    autocomplete="tel"
                                    pattern="1[3-9][0-9]{9}"
                                    maxlength="11"
                                    placeholder="选填，11位手机号"
                                />
                            </label>
                            <label>
                                真实姓名
                                <input
                                    v-model="form.realName"
                                    autocomplete="name"
                                    maxlength="10"
                                    placeholder="选填"
                                />
                            </label>
                            <label>
                                生日
                                <input v-model="form.birthday" type="date" />
                            </label>
                            <label>
                                性别
                                <select v-model.number="form.gender">
                                    <option :value="0">保密</option>
                                    <option :value="1">男</option>
                                    <option :value="2">女</option>
                                </select>
                            </label>
                        </div>
                        <p class="form-hint">手机号和生日留空将保留原值。资料仅用于账户信息，不代表演出已完成实名登记。</p>
                        <p v-if="saveError" class="form-error" role="alert">{{ saveError }}</p>
                        <p v-if="saved" class="save-success" role="status">
                            <Check :size="16" />
                            资料已保存
                        </p>
                        <button class="button primary" type="submit">
                            {{ busy ? '正在保存…' : '保存资料' }}
                        </button>
                    </fieldset>
                </form>
            </section>
        </div>
    </div>
</template>
