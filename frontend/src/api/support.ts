import { ApiError, get, request, unwrapResult } from './client'
import { readSession, saveSession } from '../utils/session'

export interface Conversation {
    id: string
    title: string
    activeRunId: string | null
    defaultConversation: boolean
}
export type ActionStatus = 'PENDING' | 'EXECUTING' | 'SUCCEEDED' | 'REJECTED' | 'UNKNOWN' | 'DISMISSED' | 'EXPIRED'
export interface AgentAction {
    id: string
    conversationId: string
    runId: string
    type: 'PURCHASE' | 'CANCEL'
    status: ActionStatus
    targetId: string
    title: string
    eventName: string
    sessionName: string
    sessionTime: string | null
    tierName: string
    quantity: number
    unitPrice: number
    totalAmount: number
    orderId: string | null
    requestStatus: string | null
    message: string
    expiresAt: string
    expiresEpochMillis: number
}
const actionStatuses = new Set(['PENDING', 'EXECUTING', 'SUCCEEDED', 'REJECTED', 'UNKNOWN', 'DISMISSED', 'EXPIRED'])
const safeId = (value: unknown): value is string => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(value)
export function normalizeActions(value: unknown): AgentAction[] {
    if (!Array.isArray(value)) return []
    return value.filter((item): item is AgentAction => !!item && safeId(item.id) && safeId(item.conversationId) && safeId(item.runId) &&
        typeof item.targetId === 'string' && /^[1-9]\d*$/.test(item.targetId) &&
        (item.type === 'PURCHASE' || item.type === 'CANCEL') && actionStatuses.has(item.status) &&
        ['title', 'eventName', 'sessionName', 'tierName', 'message', 'expiresAt'].every((key) => typeof item[key] === 'string') &&
        Number.isSafeInteger(item.quantity) && item.quantity > 0 && item.quantity <= 5 &&
        Number.isFinite(item.unitPrice) && item.unitPrice >= 0 && Number.isFinite(item.totalAmount) && item.totalAmount >= 0 &&
        Number.isFinite(item.expiresEpochMillis))
}
export function canConfirmAction(action: AgentAction, runSucceeded: boolean, available: boolean, now: number) {
    return action.status === 'PENDING' && runSucceeded && available && now < action.expiresEpochMillis
}
export function actionOrderPath(action: Pick<AgentAction, 'orderId'>) {
    return typeof action.orderId === 'string' && /^[1-9]\d*$/.test(action.orderId)
        ? `/account/orders/${action.orderId}` : null
}
export interface Citation {
    entryId: string
    documentId: string
    title: string
    version: number
    reviewedAt: string
    url: string
}
export function normalizeCitations(value: unknown): Citation[] {
    if (!Array.isArray(value)) return []
    return value.filter((item): item is Citation => !!item &&
        typeof item.entryId === 'string' && /^KB-[A-Z]+-\d{3}$/.test(item.entryId) &&
        Number.isSafeInteger(item.version) && item.version > 0 &&
        typeof item.documentId === 'string' && typeof item.title === 'string' &&
        typeof item.reviewedAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(item.reviewedAt))
}
export function citationPath(source: Citation) {
    return `/support/knowledge/${source.entryId}?version=${source.version}`
}
export interface KnowledgeEntry {
    entryId: string
    documentId: string
    documentTitle: string
    version: number
    question: string
    answer: string
    liveCheck: string
    reviewedAt: string
    current: boolean
}
export interface ChatMessage {
    id: string
    runId?: string
    role: 'user' | 'assistant'
    content: string
    citations: Citation[]
    failed?: boolean
}
export interface Run {
    id: string
    conversationId: string
    clientMessageId: string
    status: string
    reply: string | null
    citations: Citation[]
    errorMessage?: string
    deadlineAt?: string
    deadlineEpochMillis?: number
}
export function runPollingDeadline(run: Pick<Run, 'deadlineAt' | 'deadlineEpochMillis'>, limit: number) {
    const deadline = Number.isFinite(run.deadlineEpochMillis)
        ? run.deadlineEpochMillis!
        : Date.parse(run.deadlineAt || '')
    return Number.isFinite(deadline) ? Math.min(limit, deadline + 10000) : limit
}
export interface Page<T> {
    records: T[]
    hasNext: boolean
}
const base = '/user/ai-customer-service'
export const support = {
    actions: (id: string, signal?: AbortSignal) =>
        get<AgentAction[]>(`${base}/conversations/${encodeURIComponent(id)}/actions`, undefined, signal),
    action: (id: string, signal?: AbortSignal) =>
        get<AgentAction>(`${base}/actions/${encodeURIComponent(id)}`, undefined, signal),
    confirmAction: (id: string, signal?: AbortSignal) =>
        request<AgentAction>('post', `${base}/actions/${encodeURIComponent(id)}/confirm`, undefined, undefined, signal),
    dismissAction: (id: string, signal?: AbortSignal) =>
        request<AgentAction>('post', `${base}/actions/${encodeURIComponent(id)}/dismiss`, undefined, undefined, signal),
    knowledge: (entryId: string, version?: number, signal?: AbortSignal) =>
        get<KnowledgeEntry>(`${base}/knowledge/${encodeURIComponent(entryId)}`, { version }, signal),
    conversation: (id: string, signal?: AbortSignal) =>
        get<Conversation>(`${base}/conversations/${encodeURIComponent(id)}`, undefined, signal),
    conversations: (page: number, signal?: AbortSignal) =>
        get<Page<Conversation>>(`${base}/conversations`, { page, size: 20 }, signal),
    create: () => request<Conversation>('post', `${base}/conversations`, {}),
    rename: (id: string, title: string) =>
        request<void>('patch', `${base}/conversations/${encodeURIComponent(id)}`, { title }),
    remove: (id: string) =>
        request<void>('delete', `${base}/conversations/${encodeURIComponent(id)}`),
    clear: (id: string) =>
        request<void>('delete', `${base}/conversations/${encodeURIComponent(id)}/messages`),
    messages: (id: string, page: number, signal?: AbortSignal) =>
        get<Page<ChatMessage>>(
            `${base}/conversations/${encodeURIComponent(id)}/messages`,
            { page, size: 50 },
            signal,
        ),
    run: (id: string, signal?: AbortSignal) =>
        get<Run>(`${base}/runs/${encodeURIComponent(id)}`, undefined, signal),
}

// TextDecoder 保留跨字节分片；按 SSE 空行分派，支持 CRLF 和多行 data。
export async function readEventStream(
    stream: ReadableStream<Uint8Array>,
    receive: (event: string, data: any) => void,
) {
    const reader = stream.getReader(),
        decoder = new TextDecoder()
    let buffer = '',
        event = 'message',
        data: string[] = []
    function line(value: string) {
        if (!value) {
            if (data.length) receive(event, JSON.parse(data.join('\n')))
            event = 'message'
            data = []
        } else if (value.startsWith('event:')) event = value.slice(6).trim()
        else if (value.startsWith('data:')) data.push(value.slice(5).replace(/^ /, ''))
    }
    try {
        while (true) {
            const chunk = await reader.read()
            buffer += decoder.decode(chunk.value, { stream: !chunk.done })
            let end: number
            while ((end = buffer.indexOf('\n')) >= 0) {
                line(buffer.slice(0, end).replace(/\r$/, ''))
                buffer = buffer.slice(end + 1)
            }
            if (chunk.done) break
        }
        if (buffer) line(buffer.replace(/\r$/, ''))
        line('')
    } finally {
        reader.releaseLock()
    }
}

export async function streamMessage(
    id: string,
    message: string,
    clientMessageId: string,
    signal: AbortSignal,
    receive: (event: string, data: any) => void,
) {
    const token = readSession()?.token
    const response = await fetch(
        `/api${base}/conversations/${encodeURIComponent(id)}/messages/stream`,
        {
            method: 'POST',
            signal,
            headers: {
                'Content-Type': 'application/json',
                Accept: 'text/event-stream',
                ...(token ? { 'ticket-token': token } : {}),
            },
            body: JSON.stringify({ message, clientMessageId }),
        },
    )
    if (
        response.status === 401 ||
        !response.headers.get('content-type')?.includes('text/event-stream')
    ) {
        const result = await response
            .json()
            .catch(() => ({ code: response.status, msg: '服务暂不可用' }))
        if (response.status === 401 || result.code === 401) {
            if (readSession()?.token === token) {
                saveSession(null)
                window.dispatchEvent(new Event('youxi-auth-expired'))
            }
            throw new ApiError(401, '登录已失效，请重新登录')
        }
        unwrapResult(result)
        throw new Error('未收到事件流，请恢复原请求')
    }
    if (!response.ok || !response.body) throw new Error('连接失败，请恢复原请求')
    await readEventStream(response.body, receive)
}
