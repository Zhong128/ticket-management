import type { Id } from './types'

export interface LoginInfo {
    id: Id
    username: string
    email: string
    token: string
}
export interface Reservation {
    requestKey?: string
    requestId?: Id
    requestStatus: string
    orderId?: Id | null
    orderNo?: string
    expireTime?: string
    quantity?: number
    price?: number
    totalAmount?: number
    releaseReason?: string
    message?: string
}
export interface Order {
    id: Id
    orderNo: string
    eventId: Id
    eventName: string
    eventImage: string
    sessionId: Id
    sessionName: string
    sessionTime: string
    venueName: string
    tierId: Id
    tierName: string
    tierPrice: number
    quantity: number
    totalAmount: number
    orderStatus: number
    orderStatusDesc?: string
    expireTime: string
    createTime: string
    payTime?: string
    cancelReason?: string
    remark?: string
}
export interface Payment {
    paymentNo: string
    orderNo: string
    orderId?: Id
    totalAmount: number
    payStatus: number
    expireTime: string
    paymentPageHtml?: string
    qrCode?: string
}
