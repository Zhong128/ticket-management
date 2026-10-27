<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowUp, Bot, CircleHelp, LoaderCircle, Trash2, UserRound, Wrench } from 'lucide-vue-next'
import { account } from '../api/account'
import AccountNav from '../components/AccountNav.vue'
interface Message {
    id: number
    role: 'user' | 'assistant'
    text: string
    failed?: boolean
}
const messages = ref<Message[]>([]),
    draft = ref(''),
    busy = ref(false),
    clearing = ref(false),
    error = ref(''),
    notice = ref('')
const history = ref<HTMLDivElement>(),
    clearDialog = ref<HTMLDialogElement>()
const suggestions = ['如何查看我的订单？', '付款后订单还没更新怎么办？', '帮我找北京近期的音乐会']
const featureEnabled = ref<boolean | null>(null)
const maintenance = computed(() => featureEnabled.value === false)
const statusController = new AbortController()
onMounted(() => {
    account.status(statusController.signal).then(status => {
        if (!statusController.signal.aborted) featureEnabled.value = status?.customerServiceEnabled !== false
    }).catch(() => {
        if (!statusController.signal.aborted) featureEnabled.value = true
    })
})
let nextId = 0,
    disposed = false,
    controller: AbortController | undefined
onUnmounted(() => {
    disposed = true
    controller?.abort()
    statusController.abort()
})
const canSend = computed(
    () =>
        featureEnabled.value === true && !!draft.value.trim() && draft.value.trim().length <= 2000 && !busy.value && !clearing.value,
)
async function scroll() {
    await nextTick()
    history.value?.scrollTo({ top: history.value.scrollHeight, behavior: 'smooth' })
}
function choose(text: string) {
    if (!busy.value && !clearing.value) draft.value = text
}
async function send() {
    if (!canSend.value) return
    const text = draft.value.trim(),
        message: Message = { id: nextId++, role: 'user', text }
    messages.value.push(message)
    draft.value = ''
    error.value = ''
    notice.value = ''
    busy.value = true
    controller = new AbortController()
    const signal = controller.signal
    void scroll()
    try {
        const reply = await account.chat(text, signal)
        if (disposed || signal.aborted) return
        if (!reply?.trim()) throw new Error('暂未收到回复，请稍后重新提问')
        messages.value.push({ id: nextId++, role: 'assistant', text: reply })
        void scroll()
    } catch (cause) {
        if (!disposed && !signal.aborted) {
            const row = messages.value.find((m) => m.id === message.id)
            if (row) row.failed = true
            error.value = cause instanceof Error ? cause.message : '暂时无法回复，请稍后重试'
        }
    } finally {
        if (!disposed) busy.value = false
    }
}
function retry(message: Message) {
    if (!busy.value && !clearing.value) {
        draft.value = message.text
        error.value = ''
    }
}
async function clear() {
    if (busy.value || clearing.value) return
    clearing.value = true
    error.value = ''
    controller = new AbortController()
    const signal = controller.signal
    try {
        await account.clearChat(signal)
        if (!disposed && !signal.aborted) {
            messages.value = []
            draft.value = ''
            notice.value = '本次会话已清空'
            clearDialog.value?.close()
        }
    } catch (cause) {
        if (!disposed && !signal.aborted) {
            error.value = cause instanceof Error ? cause.message : '清空失败，请重试'
            clearDialog.value?.close()
        }
    } finally {
        if (!disposed) clearing.value = false
    }
}
function keydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault()
        void send()
    }
}
</script>
<template>
    <div class="container account-page support-page">
        <AccountNav />
        <div class="account-content">
            <div class="transaction-heading account-heading">
                <div>
                    <h1>有戏智能客服</h1>
                    <p>演出怎么选，订单怎么看，都可以问。</p>
                </div>
                <button
                    v-if="!maintenance"
                    class="text-button"
                    :disabled="busy || clearing"
                    @click="clearDialog?.showModal()"
                >
                    <Trash2 :size="15" />
                    清空会话
                </button>
            </div>
            <section v-if="maintenance" class="account-panel account-empty">
                <Wrench :size="40" />
                <h2>功能维护中</h2>
                <p>智能客服暂时无法提供服务，恢复后欢迎再来。<br />你仍可正常浏览演出和管理订单。</p>
                <div class="result-actions"><RouterLink to="/account/orders" class="button primary">查看我的订单</RouterLink><RouterLink to="/events" class="button secondary">继续找演出</RouterLink></div>
            </section>
            <section v-else class="chat-panel">
                <div class="chat-service-heading">
                    <span class="chat-bot"><Bot :size="23" /></span>
                    <div>
                        <strong>现场小助手</strong>
                        <p>演出查询 · 订单解答 · 购票指引</p>
                    </div>
                    <RouterLink to="/account/orders" class="text-button">我的订单</RouterLink>
                </div>
                <div
                    ref="history"
                    class="chat-history"
                    aria-label="客服对话"
                    role="log"
                    aria-live="polite"
                >
                    <div v-if="!messages.length" class="chat-welcome">
                        <Bot :size="38" />
                        <h2>你好，下一场想去哪里？</h2>
                        <p>可以告诉我城市、艺人，或遇到的购票问题。</p>
                        <div class="chat-suggestions">
                            <button
                                v-for="text in suggestions"
                                :key="text"
                                :disabled="busy || clearing"
                                @click="choose(text)"
                            >
                                <CircleHelp :size="15" />
                                {{ text }}
                            </button>
                        </div>
                        <small>
                            刷新页面不显示先前消息，服务端仍可能保留上下文；可点击清空会话重新开始。
                        </small>
                    </div>
                    <div
                        v-for="message in messages"
                        :key="message.id"
                        class="chat-message"
                        :class="message.role"
                    >
                        <span class="message-avatar">
                            <Bot v-if="message.role === 'assistant'" :size="18" />
                            <UserRound v-else :size="18" />
                        </span>
                        <div>
                            <div class="message-bubble">{{ message.text }}</div>
                            <button
                                v-if="message.failed"
                                class="text-button"
                                :disabled="busy || clearing"
                                @click="retry(message)"
                            >
                                未收到回复，重新编辑
                            </button>
                        </div>
                    </div>
                    <div v-if="busy" class="chat-message assistant">
                        <span class="message-avatar"><Bot :size="18" /></span>
                        <div class="message-bubble chat-thinking">
                            <LoaderCircle :size="16" />
                            正在查询，请稍候…
                        </div>
                    </div>
                </div>
                <div class="chat-composer">
                    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                    <p v-if="notice" class="save-success" role="status">{{ notice }}</p>
                    <form @submit.prevent="send">
                        <textarea
                            v-model="draft"
                            aria-label="咨询内容"
                            maxlength="2000"
                            rows="2"
                            placeholder="输入你的问题，Enter发送，Shift+Enter换行"
                            :disabled="clearing"
                            @keydown="keydown"
                        />
                        <button
                            class="button primary"
                            type="submit"
                            aria-label="发送消息"
                            :disabled="!canSend"
                        >
                            <ArrowUp :size="20" />
                        </button>
                    </form>
                    <div class="chat-footnote">
                        <span>AI回复仅供参考，订单与退款请以页面状态为准。</span>
                        <span>{{ draft.length }} / 2000</span>
                    </div>
                </div>
            </section>
        </div>
        <dialog
            ref="clearDialog"
            class="confirm-dialog"
            @cancel="clearing && $event.preventDefault()"
        >
            <h2>清空当前会话？</h2>
            <p>将清除当前用户的客服上下文。清空后可重新开始咨询。</p>
            <div class="result-actions">
                <button class="button secondary" :disabled="clearing" @click="clearDialog?.close()">
                    保留会话
                </button>
                <button class="button primary" :disabled="clearing" @click="clear">
                    {{ clearing ? '正在清空…' : '确认清空' }}
                </button>
            </div>
        </dialog>
    </div>
</template>
