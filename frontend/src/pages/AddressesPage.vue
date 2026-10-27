<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { MapPin, Plus, Pencil, X, Star, Trash2 } from 'lucide-vue-next'
import { account } from '../api/account'
import type { Address, AddressInput } from '../api/accountTypes'
import { addressPayload } from '../utils/account'
import AccountNav from '../components/AccountNav.vue'
import LoadState from '../components/LoadState.vue'
const addresses = ref<Address[]>([]),
    loading = ref(true),
    error = ref(''),
    actionError = ref(''),
    notice = ref(''),
    busy = ref(false)
const editor = ref<HTMLDialogElement>(),
    deleteDialog = ref<HTMLDialogElement>(),
    editing = ref<string | null>(null),
    deleting = ref<Address | null>(null),
    formError = ref('')
const blank = () => ({
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    postalCode: '',
})
const form = reactive<AddressInput>(blank())
let controller: AbortController | undefined,
    disposed = false
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
        const data = await account.addresses(signal)
        if (!signal.aborted) addresses.value = data
    } catch (cause) {
        if (!signal.aborted) error.value = cause instanceof Error ? cause.message : '地址加载失败'
    } finally {
        if (!signal.aborted) loading.value = false
    }
}
function open(address?: Address) {
    if (busy.value) return
    editing.value = address?.id || null
    Object.assign(form, blank(), address ? addressPayload(address) : {})
    formError.value = ''
    editor.value?.showModal()
}
async function save() {
    if (busy.value) return
    const payload = addressPayload(form)
    if (!payload.receiverName || !payload.province || !payload.city || !payload.detailAddress) {
        formError.value = '请填写收件人、省份、城市和详细地址'
        return
    }
    busy.value = true
    formError.value = ''
    notice.value = ''
    try {
        if (editing.value) await account.saveAddress(editing.value, payload)
        else await account.addAddress(payload)
        if (disposed) return
        editor.value?.close()
        notice.value = editing.value ? '地址已修改' : '地址已添加'
        await load()
    } catch (cause) {
        if (!disposed) formError.value = cause instanceof Error ? cause.message : '保存失败，请重试'
    } finally {
        if (!disposed) busy.value = false
    }
}
async function setDefault(address: Address) {
    if (busy.value || address.isDefault === 1) return
    busy.value = true
    actionError.value = ''
    notice.value = ''
    try {
        await account.defaultAddress(address.id)
        if (!disposed) {
            notice.value = '默认地址已更新'
            await load()
        }
    } catch (cause) {
        if (!disposed)
            actionError.value = cause instanceof Error ? cause.message : '设置失败，请重试'
    } finally {
        if (!disposed) busy.value = false
    }
}
function askDelete(address: Address) {
    if (busy.value || address.isDefault === 1) return
    deleting.value = address
    actionError.value = ''
    deleteDialog.value?.showModal()
}
async function remove() {
    if (!deleting.value || busy.value) return
    busy.value = true
    actionError.value = ''
    notice.value = ''
    try {
        await account.deleteAddress(deleting.value.id)
        if (!disposed) {
            deleteDialog.value?.close()
            notice.value = '地址已删除'
            await load()
        }
    } catch (cause) {
        if (!disposed)
            actionError.value = cause instanceof Error ? cause.message : '删除失败，请重试'
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
            <div class="transaction-heading account-heading">
                <div>
                    <h1>收货地址</h1>
                    <p>常用地址，提前准备好。</p>
                </div>
                <button class="button primary" :disabled="busy" @click="open()">
                    <Plus :size="17" />
                    新增地址
                </button>
            </div>
            <LoadState :loading="loading" :error="error" @retry="load" />
            <p v-if="notice" class="save-success" role="status">{{ notice }}</p>
            <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
            <template v-if="!loading && !error">
                <div v-if="!addresses.length" class="account-panel account-empty">
                    <MapPin :size="38" />
                    <h2>还没有收货地址</h2>
                    <p>添加一个常用地址，方便日后使用。</p>
                    <button class="button primary" @click="open()">添加第一个地址</button>
                </div>
                <section
                    v-for="address in addresses"
                    :key="address.id"
                    class="address-card"
                    :class="{ 'default-address': address.isDefault === 1 }"
                >
                    <div class="address-card-heading">
                        <h2>
                            {{ address.receiverName }}
                            <span>{{ address.receiverPhone }}</span>
                        </h2>
                        <span v-if="address.isDefault === 1" class="default-badge">
                            <Star :size="12" />
                            默认地址
                        </span>
                    </div>
                    <p>
                        {{
                            address.fullAddress ||
                            `${address.province}${address.city}${address.district || ''}${address.detailAddress}`
                        }}
                    </p>
                    <small v-if="address.postalCode">邮政编码 {{ address.postalCode }}</small>
                    <div class="address-actions">
                        <span v-if="address.isDefault === 1" class="form-hint">
                            默认地址不可删除，请先设置其他默认地址。
                        </span>
                        <button
                            v-else
                            class="text-button"
                            :disabled="busy"
                            @click="setDefault(address)"
                        >
                            设为默认
                        </button>
                        <div>
                            <button class="text-button" :disabled="busy" @click="open(address)">
                                <Pencil :size="14" />
                                编辑
                            </button>
                            <button
                                class="text-button"
                                :disabled="busy || address.isDefault === 1"
                                @click="askDelete(address)"
                            >
                                <Trash2 :size="14" />
                                删除
                            </button>
                        </div>
                    </div>
                </section>
                <p class="form-hint">
                    地址保存与订单配送规则独立，当前购票流程不要求选择配送地址。
                </p>
            </template>
        </div>
        <dialog ref="editor" class="address-dialog" @cancel="busy && $event.preventDefault()">
            <div class="dialog-heading">
                <h2>{{ editing ? '编辑地址' : '新增地址' }}</h2>
                <button
                    class="icon-button"
                    aria-label="关闭地址编辑"
                    :disabled="busy"
                    @click="editor?.close()"
                >
                    <X :size="21" />
                </button>
            </div>
            <form class="account-form" @submit.prevent="save">
                <fieldset :disabled="busy">
                    <div class="form-grid">
                        <label>
                            收件人
                            <input
                                v-model="form.receiverName"
                                autocomplete="name"
                                required
                                maxlength="10"
                            />
                        </label>
                        <label>
                            联系电话
                            <input
                                v-model="form.receiverPhone"
                                type="tel"
                                autocomplete="tel"
                                required
                                pattern="1[3-9][0-9]{9}"
                                maxlength="11"
                            />
                        </label>
                        <label>
                            省份
                            <input
                                v-model="form.province"
                                autocomplete="address-level1"
                                required
                                maxlength="30"
                                placeholder="如：北京市"
                            />
                        </label>
                        <label>
                            城市
                            <input
                                v-model="form.city"
                                autocomplete="address-level2"
                                required
                                maxlength="30"
                                placeholder="如：北京市"
                            />
                        </label>
                        <label>
                            区县
                            <input
                                v-model="form.district"
                                autocomplete="address-level3"
                                maxlength="30"
                                placeholder="选填"
                            />
                        </label>
                        <label>
                            邮政编码
                            <input
                                v-model="form.postalCode"
                                inputmode="numeric"
                                autocomplete="postal-code"
                                pattern="[0-9]{6}"
                                maxlength="6"
                                placeholder="选填，6位数字"
                            />
                        </label>
                        <label class="wide-field">
                            详细地址
                            <textarea
                                v-model="form.detailAddress"
                                autocomplete="street-address"
                                required
                                maxlength="200"
                                rows="3"
                                placeholder="街道、楼栋及门牌号"
                            />
                        </label>
                    </div>
                    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
                    <div class="dialog-actions">
                        <button class="button secondary" type="button" @click="editor?.close()">
                            取消
                        </button>
                        <button class="button primary">
                            {{ busy ? '正在保存…' : '保存地址' }}
                        </button>
                    </div>
                </fieldset>
            </form>
        </dialog>
        <dialog ref="deleteDialog" class="confirm-dialog" @cancel="busy && $event.preventDefault()">
            <h2>删除这个地址？</h2>
            <p>{{ deleting?.receiverName }} · {{ deleting?.receiverPhone }}</p>
            <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
            <div class="result-actions">
                <button class="button secondary" :disabled="busy" @click="deleteDialog?.close()">
                    保留地址
                </button>
                <button class="button primary" :disabled="busy" @click="remove">
                    {{ busy ? '正在删除…' : '确认删除' }}
                </button>
            </div>
        </dialog>
    </div>
</template>
