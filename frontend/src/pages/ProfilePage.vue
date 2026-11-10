<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { Camera, Check } from 'lucide-vue-next'
import { account } from '../api/account'
import type { Profile } from '../api/accountTypes'
import { profilePayload } from '../utils/account'
import { useAuthStore } from '../stores/auth'
import AccountNav from '../components/AccountNav.vue'
import LoadState from '../components/LoadState.vue'
import AvatarImage from '../components/AvatarImage.vue'
const auth = useAuthStore()
const form = reactive<Profile>({
    username: '',
    email: '',
    nickname: '',
    phone: '',
    realName: '',
    gender: 0,
    birthday: '',
    avatar: '',
})
const loading = ref(true),
    error = ref(''),
    saveError = ref(''),
    saved = ref(false),
    busy = ref(false)
let disposed = false
let controller: AbortController | undefined
let avatarController: AbortController | undefined
const avatarFile = ref<File>()
const avatarPreview = ref('')
const avatarError = ref('')
const avatarMessage = ref('')
const avatarBusy = ref(false)
const avatarInput = ref<HTMLInputElement>()
function cancelAvatar() {
    if (avatarPreview.value) {
        URL.revokeObjectURL(avatarPreview.value)
    }
    avatarPreview.value = ''
    avatarFile.value = undefined
    if (avatarInput.value) {
        avatarInput.value.value = ''
    }
}
function selectAvatar(event: Event) {
    avatarError.value = ''
    avatarMessage.value = ''
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) {
        return
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
        avatarError.value = '请选择 JPG 或 PNG 图片'
        if (avatarInput.value) {
            avatarInput.value.value = ''
        }
        return
    }
    if (file.size > 2 * 1024 * 1024 || !file.size) {
        avatarError.value = '图片大小需在 2MB 以内，且不能为空'
        if (avatarInput.value) {
            avatarInput.value.value = ''
        }
        return
    }
    cancelAvatar()
    avatarFile.value = file
    avatarPreview.value = URL.createObjectURL(file)
}
async function saveAvatar(reset = false) {
    if (busy.value || avatarBusy.value || (!reset && !avatarFile.value)) {
        return
    }
    const current = auth.user
    avatarBusy.value = true
    avatarError.value = ''
    avatarMessage.value = ''
    avatarController = new AbortController()
    const signal = avatarController.signal
    try {
        const result = reset
            ? await account.resetAvatar(signal)
            : await account.uploadAvatar(avatarFile.value!, signal)
        if (disposed || signal.aborted || auth.user?.token !== current?.token) {
            return
        }
        form.avatar = result.avatar || ''
        if (current) {
            auth.accept({ ...current, avatar: result.avatar })
        }
        cancelAvatar()
        avatarMessage.value = reset ? '已恢复默认头像' : '头像已保存'
    } catch (cause) {
        if (!disposed && !signal.aborted) {
            avatarError.value = cause instanceof Error ? cause.message : '头像保存失败，请重试'
        }
    } finally {
        if (!disposed) {
            avatarBusy.value = false
        }
    }
}
onUnmounted(() => {
    disposed = true
    controller?.abort()
    avatarController?.abort()
    cancelAvatar()
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
                { nickname: '', phone: '', realName: '', gender: 0, birthday: '', avatar: '' },
                data,
            )
    } catch (cause) {
        if (!signal.aborted) error.value = cause instanceof Error ? cause.message : '资料加载失败'
    } finally {
        if (!signal.aborted) loading.value = false
    }
}
async function save() {
    if (busy.value || avatarBusy.value) return
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
                    <div class="profile-avatar"><AvatarImage :src="avatarPreview || form.avatar" :alt="avatarPreview ? '头像预览' : '我的头像'" :icon-size="32" /></div>
                    <div>
                        <h2>{{ form.nickname || form.username }}</h2>
                        <p>{{ form.email }}</p>
                    </div>
                </div>
                <div class="profile-avatar-editor" aria-label="设置头像">
                    <input ref="avatarInput" class="sr-only" type="file" accept="image/jpeg,image/png" aria-label="选择头像文件" :disabled="busy || avatarBusy" @change="selectAvatar" />
                    <div class="profile-avatar-actions">
                        <button class="button secondary" type="button" :disabled="busy || avatarBusy" @click="avatarInput?.click()"><Camera :size="16" />选择图片</button>
                        <template v-if="avatarFile">
                            <button class="button primary" type="button" :disabled="busy || avatarBusy" @click="saveAvatar()">{{ avatarBusy ? '正在保存…' : '保存头像' }}</button>
                            <button class="text-button" type="button" :disabled="avatarBusy" @click="cancelAvatar">取消选择</button>
                        </template>
                        <button v-else-if="form.avatar" class="text-button" type="button" :disabled="busy || avatarBusy" @click="saveAvatar(true)">恢复默认头像</button>
                    </div>
                    <p class="form-hint">支持 JPG、PNG，最大2MB。头像将居中裁成正方形；保存头像不影响下方资料。</p>
                    <p v-if="avatarError" class="form-error" role="alert">{{ avatarError }}</p>
                    <p v-if="avatarMessage" class="save-success" role="status"><Check :size="16" />{{ avatarMessage }}</p>
                </div>
                <form class="account-form" @submit.prevent="save" @input="saved = false">
                    <fieldset :disabled="busy || avatarBusy">
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
<style scoped>
.profile-avatar { flex-shrink:0;overflow:hidden; }
.profile-avatar-editor { margin:-8px 0 28px;padding-bottom:22px;border-bottom:1px solid #eeeef4; }
.profile-avatar-actions { display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:10px; }
.profile-avatar-actions .button { min-height:36px;padding:8px 14px;font-size:13px;display:inline-flex;align-items:center;gap:7px; }
.profile-avatar-actions .text-button { font-size:13px; }
</style>
