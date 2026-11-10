<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowUp, Bot, UserRound, Wrench } from 'lucide-vue-next'
import { account } from '../api/account'
import {
    support,
    normalizeCitations,
    citationPath,
    streamMessage,
    runPollingDeadline,
    type Conversation,
    type ChatMessage,
    type Run,
    normalizeActions,
    canConfirmAction,
    type AgentAction,
} from '../api/support'
import AccountNav from '../components/AccountNav.vue'
import AgentActionCard from '../components/AgentActionCard.vue'
const messages = ref<ChatMessage[]>([]),
    conversations = ref<Conversation[]>([])
const selected = ref<Conversation>(),
    draft = ref(''),
    title = ref(''),
    busy = ref(false),
    loading = ref(false),
    clearing = ref(false)
const error = ref(''),
    notice = ref(''),
    featureEnabled = ref<boolean | null>(null)
const tradingEnabled = ref(false)
const history = ref<HTMLDivElement>(),
    clearDialog = ref<HTMLDialogElement>()
const listPage = ref(1),
    messagePage = ref(1),
    moreConversations = ref(false),
    moreMessages = ref(false)
const pending = ref<{ message: string; clientMessageId: string; runId?: string }>()
const actions = ref<AgentAction[]>([]),
    actionBusy = ref<Set<string>>(new Set()),
    successfulRuns = ref<Set<string>>(new Set()),
    actionError = ref(''),
    actionNow = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | undefined
let actionRevision = 0
let generation = 0,
    controller = new AbortController(),
    disposed = false
const canSend = computed(
    () =>
        featureEnabled.value === true &&
        !!selected.value &&
        !!draft.value.trim() &&
        draft.value.trim().length <= 2000 &&
        !busy.value &&
        !loading.value &&
        !clearing.value &&
        !pending.value,
)
const suggestions = ['如何查看我的订单？', '付款后订单还没更新怎么办？', '帮我找北京近期的音乐会']
const queryLabels: Record<string, string> = {
    queryEventsByCity: '查询城市演出',
    queryEventsByArtist: '查询艺人演出',
    getEventDetail: '查询演出详情',
    getEventSessions: '查询演出场次',
    getSessionDetail: '查询场次信息',
    getSessionTiers: '查询票档价格',
    getRealTimeStock: '检查实时库存',
    getUserProfile: '查询个人资料',
    getUserAddresses: '查询收货地址',
    getUserOrders: '查询我的订单',
    getOrderDetail: '查询订单详情',
    getReservationStatus: '查询购票申请进度',
    checkSeckillQualification: '检查购票资格',
    proposePurchase: '准备购票确认卡片',
    proposeCancellation: '准备取消确认卡片',
}
function mergeAction(value: unknown) {
    const row = normalizeActions([value])[0]
    if (!row || row.conversationId !== selected.value?.id) return
    const index = actions.value.findIndex((item) => item.id === row.id)
    if (index >= 0) actions.value[index] = row
    else actions.value.push(row)
}
async function loadActions(current = generation) {
    const id = selected.value?.id
    const revision = actionRevision
    if (!id || actionBusy.value.size) return
    try {
        const result = await support.actions(id, controller.signal)
        if (current !== generation || revision !== actionRevision || disposed) return
        actions.value = normalizeActions(result).filter((row) => row.conversationId === id)
        actionError.value = ''
    } catch {
        if (current === generation && !controller.signal.aborted)
            actionError.value = '交易卡片暂不可用，请刷新卡片。聊天仍可继续。'
    }
}
async function actionOperation(row: AgentAction, operation: 'confirm' | 'dismiss' | 'refresh') {
    if (actionBusy.value.has(row.id) || row.conversationId !== selected.value?.id) return
    if (operation === 'confirm' && !canConfirmAction(row, successfulRuns.value.has(row.runId), featureEnabled.value === true && tradingEnabled.value && !busy.value && !loading.value && !clearing.value, Date.now())) return
    const current = generation,
        signal = controller.signal
    actionRevision++
    actionBusy.value.add(row.id)
    actionError.value = ''
    try {
        const result = operation === 'confirm'
            ? await support.confirmAction(row.id, signal)
            : operation === 'dismiss'
                ? await support.dismissAction(row.id, signal)
                : await support.action(row.id, signal)
        if (current !== generation || signal.aborted) return
        actionRevision++
        mergeAction(result)
    } catch (cause) {
        if (current !== generation || signal.aborted) return
        actionRevision++
        if (operation === 'confirm') {
            // 确认响应丢失不代表未执行，后续仅允许查询同一卡片。
            row.status = 'UNKNOWN'
            row.message = '确认响应未收到，结果待核实。'
        }
        actionError.value = cause instanceof Error ? cause.message : '操作暂不可用，请查询原结果'
    } finally {
        if (current === generation) actionBusy.value.delete(row.id)
    }
}
function queryNotice(toolName: string, status: string) {
    const label = queryLabels[toolName] || '处理咨询'
    if (status === 'SUCCESS') return `${label}已完成`
    if (status === 'EMPTY') return `${label}暂无结果`
    if (status === 'REJECTED') return `${label}未能受理，正在整理说明`
    if (status === 'ERROR') return `${label}暂时失败，正在整理说明`
    return `正在${label}…`
}
function fail(cause: unknown) {
    error.value = cause instanceof Error ? cause.message : '请求失败，请重试'
}
async function scroll() {
    await nextTick()
    history.value?.scrollTo({ top: history.value.scrollHeight })
}
function cancel() {
    // 只取消浏览器请求，后台运行继续；代次隔离迟到响应。
    generation++
    controller.abort()
    controller = new AbortController()
    busy.value = false
    loading.value = false
    clearing.value = false
    actionBusy.value = new Set()
}
async function list(more = false) {
    const current = generation
    const page = more ? listPage.value + 1 : 1
    const result = await support.conversations(page, controller.signal)
    if (disposed || current !== generation) return
    conversations.value = more ? [...conversations.value, ...result.records] : result.records
    listPage.value = page
    moreConversations.value = result.hasNext
}
async function select(row: Conversation) {
    cancel()
    const current = generation
    selected.value = row
    title.value = row.title
    messages.value = []
    actions.value = []
    successfulRuns.value = new Set()
    actionError.value = ''
    pending.value = undefined
    error.value = ''
    notice.value = ''
    draft.value = ''
    loading.value = true
    try {
        // 先刷新运行元数据，再读历史，避免运行刚完成时漏掉最终回复。
        const latest = await support.conversation(row.id, controller.signal)
        if (current !== generation) return
        selected.value = latest
        title.value = latest.title
        const cached = conversations.value.find((conversation) => conversation.id === latest.id)
        if (cached) Object.assign(cached, latest)
        const result = await support.messages(latest.id, 1, controller.signal)
        if (current !== generation) return
        messages.value = result.records.map((message) => ({ ...message, citations: normalizeCitations(message.citations) }))
        successfulRuns.value = new Set(result.records.filter((message) => message.role === 'assistant' && message.runId).map((message) => message.runId!))
        await loadActions(current)
        if (current !== generation) return
        messagePage.value = 1
        moreMessages.value = result.hasNext
        if (latest.activeRunId) {
            pending.value = { message: '', clientMessageId: '', runId: latest.activeRunId }
            void recover()
        }
        void scroll()
    } catch (cause) {
        if (current === generation) fail(cause)
    } finally {
        if (current === generation) loading.value = false
    }
}
async function earlier() {
    if (!selected.value || loading.value) return
    const current = generation
    loading.value = true
    try {
        const result = await support.messages(
            selected.value.id,
            messagePage.value + 1,
            controller.signal,
        )
        if (current !== generation) return
        const ids = new Set(messages.value.map((m) => m.id))
        messages.value = [...result.records.filter((m) => !ids.has(m.id)).map((message) => ({ ...message, citations: normalizeCitations(message.citations) })), ...messages.value]
        for (const message of result.records) {
            if (message.role === 'assistant' && message.runId) successfulRuns.value.add(message.runId)
        }
        messagePage.value++
        moreMessages.value = result.hasNext
    } catch (cause) {
        if (current === generation) fail(cause)
    } finally {
        if (current === generation) loading.value = false
    }
}
async function initialize() {
    error.value = ''
    featureEnabled.value = null
    try {
        const status = await account.status(controller.signal) as { customerServiceEnabled?: boolean; tradingEnabled?: boolean }
        if (disposed) return
        featureEnabled.value = status.customerServiceEnabled === true
        tradingEnabled.value = status.tradingEnabled === true
        if (!featureEnabled.value) return
        await list()
        if (conversations.value[0]) await select(conversations.value[0])
    } catch (cause) {
        if (!disposed) fail(cause)
    }
}
async function create() {
    if (loading.value) return
    const current = generation
    loading.value = true
    try {
        const row = await support.create()
        if (current !== generation) return
        await list()
        if (current !== generation) return
        await select(row)
    } catch (cause) {
        if (current === generation) fail(cause)
    } finally {
        if (current === generation) loading.value = false
    }
}
async function rename() {
    if (!selected.value || !title.value.trim()) return
    const id = selected.value.id,
        name = title.value.trim(),
        current = generation
    try {
        await support.rename(id, name)
        if (current !== generation) return
        selected.value.title = name
        const row = conversations.value.find((row) => row.id === id)
        if (row) row.title = name
        notice.value = '名称已保存'
    } catch (cause) {
        if (current === generation) fail(cause)
    }
}
async function remove() {
    if (!selected.value || !window.confirm('确认删除当前会话及历史？')) return
    const id = selected.value.id,
        current = generation
    try {
        await support.remove(id)
        if (current !== generation) return
        cancel()
        selected.value = undefined
        messages.value = []
        actions.value = []
        successfulRuns.value = new Set()
        pending.value = undefined
        await list()
        if (conversations.value[0]) await select(conversations.value[0])
    } catch (cause) {
        if (current === generation) fail(cause)
    }
}
async function clear() {
    if (!selected.value || clearing.value || busy.value || pending.value) return
    const id = selected.value.id,
        current = generation
    clearing.value = true
    error.value = ''
    try {
        await support.clear(id)
        if (current !== generation) return
        // 隔离清空前已发出的卡片查询或确认响应，防止迟到内容重新填回页面。
        cancel()
        clearDialog.value?.close()
        messages.value = []
        actions.value = []
        successfulRuns.value = new Set()
        pending.value = undefined
        moreMessages.value = false
        notice.value = '本次会话已清空'
    } catch (cause) {
        if (current === generation) fail(cause)
    } finally {
        if (current === generation) {
            clearing.value = false
            clearDialog.value?.close()
        }
    }
}
function finish(run: Run) {
    if (pending.value?.runId !== run.id) return false
    if (run.status === 'RUNNING') return false
    if (run.status === 'SUCCEEDED') successfulRuns.value.add(run.id)
    else successfulRuns.value.delete(run.id)
    const row = messages.value.find(
        (m) => m.id === `reply-${run.id}` || (m.role === 'assistant' && m.runId === run.id),
    )
    if (run.reply) {
        if (row) {
            row.content = run.reply
            row.citations = normalizeCitations(run.citations)
        }
        else
            messages.value.push({
                id: `reply-${run.id}`,
                runId: run.id,
                role: 'assistant',
                content: run.reply,
                citations: normalizeCitations(run.citations),
            })
    } else if (row) messages.value = messages.value.filter((m) => m !== row)
    if (run.status !== 'SUCCEEDED') {
        error.value = run.errorMessage || '回复未完成，可重新编辑问题'
        const user = [...messages.value].reverse().find((m) => m.role === 'user')
        if (user) user.failed = true
    }
    pending.value = undefined
    if (selected.value) selected.value.activeRunId = null
    const conversation = conversations.value.find((row) => row.id === selected.value?.id)
    if (conversation) conversation.activeRunId = null
    void loadActions()
    void scroll()
    return true
}
async function recover() {
    // 已知运行只查询恢复；首事件前断流由用户明确重发同一幂等请求。
    const request = pending.value
    if (!request) return
    if (!request.runId) {
        await send(true)
        return
    }
    const current = generation,
        signal = controller.signal
    busy.value = true
    error.value = ''
    notice.value = '正在恢复运行结果，后台任务继续执行'
    const limit = Date.now() + 120000
    try {
        while (!signal.aborted && current === generation) {
            const run = await support.run(request.runId, signal)
            if (current !== generation) return
            if (finish(run)) {
                notice.value = ''
                return
            }
            if (Date.now() >= runPollingDeadline(run, limit))
                throw new Error('任务仍在后台执行，请稍后点击恢复原请求')
            await new Promise<void>((resolve) => {
                const done = () => {
                    clearTimeout(timer)
                    signal.removeEventListener('abort', done)
                    resolve()
                }
                const timer = setTimeout(done, 1000)
                signal.addEventListener('abort', done, { once: true })
            })
        }
    } catch (cause) {
        if (current === generation && !signal.aborted) fail(cause)
    } finally {
        if (current === generation) busy.value = false
    }
}
async function send(replay = false) {
    if (!selected.value || (!replay && !canSend.value)) return
    const request = replay
        ? pending.value!
        : {
              message: draft.value.trim(),
              clientMessageId: crypto.randomUUID(),
              runId: undefined as string | undefined,
          }
    if (!replay) {
        messages.value.push({ id: request.clientMessageId, role: 'user', content: request.message, citations: [] })
        draft.value = ''
    }
    pending.value = request
    busy.value = true
    error.value = ''
    notice.value = ''
    const current = generation,
        signal = controller.signal
    let terminal = false
    try {
        await streamMessage(
            selected.value.id,
            request.message,
            request.clientMessageId,
            signal,
            (event, data) => {
                if (current !== generation || signal.aborted) return
                if (event === 'run_started') {
                    request.runId = data.runId
                    if (selected.value) selected.value.activeRunId = data.runId
                    const row = conversations.value.find((row) => row.id === selected.value?.id)
                    if (row) row.activeRunId = data.runId
                }
                if (event !== 'run_started' &&
                    (event === 'done' ? data.id : data.runId) !== request.runId) return
                if (event === 'knowledge_sources') {
                    const id = `reply-${data.runId}`
                    let row = messages.value.find((message) => message.id === id)
                    if (!row) {
                        row = { id, runId: data.runId, role: 'assistant', content: '', citations: [] }
                        messages.value.push(row)
                    }
                    row.citations = normalizeCitations(data.citations)
                    void scroll()
                }
                if (event === 'action_proposed') {
                    if (data.action?.runId !== request.runId) return
                    mergeAction(data.action)
                    const id = `reply-${data.runId}`
                    if (!messages.value.some((message) => message.id === id))
                        messages.value.push({ id, runId: data.runId, role: 'assistant', content: '', citations: [] })
                    void scroll()
                }
                if (event === 'text_delta') {
                    const id = `reply-${data.runId}`
                    let row = messages.value.find((m) => m.id === id)
                    if (!row) {
                        row = { id, runId: data.runId, role: 'assistant', content: '', citations: [] }
                        messages.value.push(row)
                    }
                    row.content += data.text
                    void scroll()
                }
                if (event === 'tool_status') notice.value = queryNotice(data.toolName, data.status)
                if (event === 'error') error.value = data.message || '回复未完成'
                if (event === 'done') terminal = finish(data)
            },
        )
        if (current === generation && !terminal) {
            if (request.runId) await recover()
            else error.value = '连接中断，点击恢复原请求，不会重复创建任务'
        }
    } catch (cause) {
        if (current === generation && !signal.aborted) {
            if (request.runId) await recover()
            else fail(cause)
        }
    } finally {
        if (current === generation) {
            busy.value = false
            void scroll()
        }
    }
}
function keydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault()
        void send()
    }
}
onMounted(() => {
    clockTimer = setInterval(() => { actionNow.value = Date.now() }, 1000)
    void initialize()
})
onUnmounted(() => {
    clearInterval(clockTimer)
    disposed = true
    cancel()
})
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
            </div>
            <section v-if="featureEnabled === false" class="account-panel account-empty">
                <Wrench :size="40" />
                <h2>功能维护中</h2>
                <p>智能客服暂时无法提供服务，恢复后欢迎再来。</p>
                <div class="result-actions">
                    <RouterLink to="/account/orders" class="button primary">查看我的订单</RouterLink
                    ><RouterLink to="/events" class="button secondary">继续找演出</RouterLink>
                </div>
            </section>
            <section v-else-if="featureEnabled === null" class="account-panel">
                <p v-if="error" role="alert">{{ error }}</p>
                <p v-else>正在确认服务状态…</p>
                <button v-if="error" class="button secondary" @click="initialize">
                    重试服务状态
                </button>
            </section>
            <div v-else class="support-workspace">
                <aside class="support-conversations" aria-label="客服会话列表">
                    <button class="button primary" :disabled="loading" @click="create">
                        新建会话
                    </button>
                    <button
                        v-for="row in conversations"
                        :key="row.id"
                        class="support-conversation"
                        :class="{ active: selected?.id === row.id }"
                        @click="select(row)"
                    >
                        {{ row.title }}<small v-if="row.activeRunId">后台运行中</small>
                    </button>
                    <button
                        v-if="moreConversations"
                        class="text-button"
                        @click="list(true).catch(fail)"
                    >
                        加载更多会话
                    </button>
                </aside>
                <section class="chat-panel">
                    <div class="chat-service-heading">
                        <span class="chat-bot"><Bot :size="23" /></span>
                        <div>
                            <strong>现场小助手</strong>
                            <p>历史会话安全保存在当前账号下</p>
                        </div>
                        <RouterLink to="/account/orders" class="text-button">我的订单</RouterLink>
                    </div>
                    <div v-if="selected" class="support-manage">
                        <input
                            v-model="title"
                            aria-label="会话名称"
                            maxlength="100"
                            :disabled="loading"
                        /><button class="text-button" :disabled="loading" @click="rename">
                            保存名称</button
                        ><button
                            class="text-button"
                            :disabled="busy || !!pending"
                            @click="clearDialog?.showModal()"
                        >
                            清空会话</button
                        ><button class="text-button" :disabled="busy || !!pending" @click="remove">
                            删除会话
                        </button>
                    </div>
                    <div
                        ref="history"
                        class="chat-history"
                        aria-label="客服对话"
                        role="log"
                        aria-live="polite"
                    >
                        <button
                            v-if="moreMessages"
                            class="text-button"
                            :disabled="loading"
                            @click="earlier"
                        >
                            加载更早消息
                        </button>
                        <p v-if="loading">正在加载历史…</p>
                        <div v-if="!messages.length && !loading" class="chat-welcome">
                            <Bot :size="38" />
                            <h2>你好，下一场想去哪里？</h2>
                            <p>可以告诉我城市、艺人，或遇到的购票问题。</p>
                            <div class="chat-suggestions">
                                <button
                                    v-for="text in suggestions"
                                    :key="text"
                                    :disabled="busy || !!pending"
                                    @click="draft = text"
                                >
                                    {{ text }}
                                </button>
                            </div>
                            <small>刷新后可继续查看历史；离开页面不会停止后台任务。</small>
                        </div>
                        <div
                            v-for="message in messages"
                            :key="message.id"
                            class="chat-message"
                            :class="message.role"
                        >
                            <span class="message-avatar"
                                ><Bot v-if="message.role === 'assistant'" :size="18" /><UserRound
                                    v-else
                                    :size="18"
                            /></span>
                            <div>
                                <div class="message-bubble">{{ message.content }}</div>
                                <aside v-if="message.role === 'assistant' && message.citations?.length" class="knowledge-sources" aria-label="检索参考规则">
                                    <strong>检索参考规则</strong>
                                    <p>来源用于说明规则，具体订单、付款和库存仍需实时核验。</p>
                                    <RouterLink v-for="source in message.citations" :key="`${source.entryId}-${source.version}`" :to="citationPath(source)">
                                        {{ source.title }} · 版本 {{ source.version }}
                                    </RouterLink>
                                </aside>
                                <AgentActionCard
                                    v-for="action in actions.filter((row) => row.runId === message.runId && message.role === 'assistant')"
                                    :key="action.id"
                                    :action="action"
                                    :run-succeeded="successfulRuns.has(action.runId)"
                                    :run-pending="pending?.runId === action.runId"
                                    :available="featureEnabled === true && tradingEnabled"
                                    :processing="actionBusy.has(action.id) || busy || loading || clearing"
                                    :now="actionNow"
                                    @confirm="actionOperation(action, 'confirm')"
                                    @dismiss="actionOperation(action, 'dismiss')"
                                    @refresh="actionOperation(action, 'refresh')"
                                />
                                <button
                                    v-if="message.failed"
                                    class="text-button"
                                    :disabled="busy"
                                    @click="draft = message.content"
                                >
                                    未收到回复，重新编辑
                                </button>
                            </div>
                        </div>
                        <p v-if="busy" role="status">正在处理，请稍候…</p>
                    </div>
                    <div class="chat-composer">
                        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                        <p v-if="actionError" class="form-error" role="alert">{{ actionError }} <button class="text-button" @click="loadActions()">刷新卡片</button></p>
                        <p v-if="notice" class="save-success" role="status">{{ notice }}</p>
                        <button v-if="pending && !busy" class="text-button" @click="recover">
                            恢复原请求
                        </button>
                        <form @submit.prevent="send()">
                            <textarea
                                v-model="draft"
                                :disabled="loading || clearing || !selected"
                                aria-label="咨询内容"
                                maxlength="2000"
                                rows="2"
                                placeholder="输入你的问题，Enter发送，Shift+Enter换行"
                                @keydown="keydown"
                            /><button
                                class="button primary"
                                type="submit"
                                aria-label="发送消息"
                                :disabled="!canSend"
                            >
                                <ArrowUp :size="20" />
                            </button>
                        </form>
                        <div class="chat-footnote">
                            <span>AI回复仅供参考。切换会话或离开页面后，后台任务仍继续处理。</span
                            ><span>{{ draft.length }} / 2000</span>
                        </div>
                    </div>
                </section>
            </div>
        </div>
        <dialog ref="clearDialog" class="confirm-dialog">
            <h2>清空当前会话？</h2>
            <p>将清除当前会话的历史和上下文。</p>
            <div class="result-actions">
                <button class="button secondary" @click="clearDialog?.close()">保留会话</button
                ><button class="button primary" @click="clear">确认清空</button>
            </div>
        </dialog>
    </div>
</template>
<style scoped>
.support-workspace {
    display: grid;
    grid-template-columns: 190px minmax(0, 1fr);
    gap: 16px;
}
.support-conversations {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 0;
}
.support-conversation {
    text-align: left;
    background: white;
    border: 1px solid #e7e7eb;
    border-radius: 8px;
    padding: 12px;
    overflow-wrap: anywhere;
}
.support-conversation.active {
    border-color: #e52871;
    color: #d72266;
}
.support-conversation small {
    display: block;
    color: #777;
    margin-top: 4px;
}
.support-manage {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    border-bottom: 1px solid #eee;
}
.support-manage input {
    width: 140px;
    min-width: 0;
    border: 1px solid #ddd;
    border-radius: 6px;
    padding: 7px;
}
.chat-panel {
    min-width: 0;
}
.chat-message > div {
    min-width: 0;
}
.message-bubble {
    overflow-wrap: anywhere;
    white-space: pre-wrap;
}
.knowledge-sources { margin-top: 10px; padding: 12px; border: 1px solid #e7e7eb; border-radius: 8px; font-size: 13px; overflow-wrap: anywhere; }
.knowledge-sources p { margin: 6px 0; color: #777; }
.knowledge-sources a { display: block; color: #d72266; margin-top: 6px; }
@media (max-width: 760px) {
    .support-workspace {
        grid-template-columns: minmax(0, 1fr);
    }
    .support-conversations {
        max-height: 180px;
        overflow-y: auto;
    }
    .support-manage {
        gap: 8px;
    }
}
</style>
