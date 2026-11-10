import { get, post, put } from './client'
import type { LoginInfo, Order, Payment, Reservation } from './tradeTypes'
import type { Page } from './types'

export const trade = {
    login: (email: string, password: string) =>
        post<LoginInfo>('/auth/user/login-or-register', { email, password }),
    verify: (email: string, code: string, password: string) =>
        post<LoginInfo>('/auth/user/verify-registration', { email, code, password }),
    resend: (email: string) =>
        post<void>('/public/verification/resend', { email, type: 'REGISTER' }),
    logout: () => post<void>('/auth/logout'),
    profile: () => get<{ id: string; username: string; email: string; avatar?: string | null }>('/user/profile'),
    reserve: (tierId: string, quantity: number, requestKey: string) =>
        post<Reservation>('/user/seckill', { tierId, quantity, requestKey }),
    reservation: (requestKey: string, signal?: AbortSignal) =>
        get<Reservation>('/user/seckill/requests/by-key', { requestKey }, signal),
    orders: (page: number, orderStatus?: number, signal?: AbortSignal) =>
        get<Page<Order>>('/user/orders/page', { page, size: 6, orderStatus }, signal),
    order: (id: string, signal?: AbortSignal) =>
        get<Order>(`/user/orders/${encodeURIComponent(id)}`, undefined, signal),
    cancel: (id: string) => put<void>(`/user/orders/${encodeURIComponent(id)}/cancel`),
    pay: (orderId: string) => post<Payment>('/user/payments/create', { orderId, payType: 1 }),
    payment: (paymentNo: string, signal?: AbortSignal) =>
        get<Payment>(`/user/payments/status/${encodeURIComponent(paymentNo)}`, undefined, signal),
}
