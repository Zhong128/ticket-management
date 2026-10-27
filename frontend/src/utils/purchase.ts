import type { Reservation } from '../api/tradeTypes'

export interface PurchaseIntent {
    userId: string
    eventId: string
    sessionId: string
    tierId: string
    quantity: number
    requestKey: string
    createdAt: number
    response?: Reservation
}
type Selection = Pick<PurchaseIntent, 'userId' | 'eventId' | 'sessionId' | 'tierId' | 'quantity'>

export function safeRedirect(value: unknown): string {
    if (typeof value !== 'string') return '/'
    try {
        const decoded = decodeURIComponent(value)
        if (
            !decoded.startsWith('/') ||
            decoded.startsWith('//') ||
            /[\\\r\n]/.test(decoded) ||
            /^\/(login)([/?#]|$)/.test(decoded)
        )
            return '/'
        return value
    } catch {
        return '/'
    }
}
export function getOrCreateIntent(
    previous: PurchaseIntent | null,
    selection: Selection,
): PurchaseIntent {
    if (previous) {
        if (
            ['userId', 'eventId', 'sessionId', 'tierId', 'quantity'].every(
                (key) => previous[key as keyof Selection] === selection[key as keyof Selection],
            )
        )
            return previous
        throw new Error('已有购票申请，请先查看原申请结果')
    }
    return { ...selection, requestKey: crypto.randomUUID(), createdAt: Date.now() }
}
export function loadIntent(userId: string): PurchaseIntent | null {
    try {
        const saved = JSON.parse(localStorage.getItem(`youxi-purchase:${userId}`) || 'null')
        return saved?.userId === userId &&
            typeof saved.requestKey === 'string' &&
            saved.requestKey.length <= 64 &&
            typeof saved.tierId === 'string' &&
            Number.isInteger(saved.quantity) &&
            saved.quantity >= 1 &&
            saved.quantity <= 5
            ? saved
            : null
    } catch {
        return null
    }
}
export function saveIntent(intent: PurchaseIntent) {
    localStorage.setItem(`youxi-purchase:${intent.userId}`, JSON.stringify(intent))
}
export function forgetIntent(userId: string) {
    localStorage.removeItem(`youxi-purchase:${userId}`)
}
export function reservationView(reply: Partial<Reservation>) {
    if (reply.requestStatus === 'PAID') return 'paid'
    if (reply.requestStatus === 'RELEASED') return 'released'
    if (reply.requestStatus === 'REJECTED') return 'rejected'
    if (reply.orderId) return 'created'
    if (reply.requestStatus === 'UNKNOWN') return 'unknown'
    return 'processing'
}
export const orderLabel = (status: number) =>
    ['待支付', '已支付', '已取消', '已完成', '取消确认中'][status] || '状态待确认'
export function timestamp(value?: string) {
    if (!value) return Number.NaN
    return Date.parse(
        value.replace(' ', 'T') + (/(Z|[+-]\d{2}:?\d{2})$/i.test(value) ? '' : '+08:00'),
    )
}
export function remainingSeconds(value?: string, now = Date.now()) {
    const time = timestamp(value)
    return Number.isFinite(time) ? Math.max(0, Math.ceil((time - now) / 1000)) : 0
}
export function countdown(seconds: number) {
    return `${Math.floor(seconds / 60)
        .toString()
        .padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}
