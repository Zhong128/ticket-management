import { get, post, put, remove, request } from './client'
import type { Address, AddressInput, Profile, Refund } from './accountTypes'

export const account = {
    status: (signal?: AbortSignal) => get<{ customerServiceEnabled?: boolean }>('/public/app-status', undefined, signal),
    profile: (signal?: AbortSignal) => get<Profile>('/user/profile', undefined, signal),
    saveProfile: (data: Partial<Profile>) => put<void>('/user/profile', data),
    password: (oldPassword: string, newPassword: string) =>
        request<void>('put', '/user/password', new URLSearchParams({ oldPassword, newPassword })),
    addresses: (signal?: AbortSignal) => get<Address[]>('/user/address', undefined, signal),
    addAddress: (data: AddressInput) => post<void>('/user/address', data),
    saveAddress: (id: string, data: AddressInput) =>
        put<void>(`/user/address/${encodeURIComponent(id)}`, data),
    defaultAddress: (id: string) => post<void>(`/user/address/${encodeURIComponent(id)}/default`),
    deleteAddress: (id: string) => remove<void>(`/user/address/${encodeURIComponent(id)}`),
    refunds: (signal?: AbortSignal) => get<Refund[]>('/user/refunds', undefined, signal),
    chat: (message: string, signal?: AbortSignal) =>
        request<string>(
            'post',
            '/user/ai-customer-service/chat',
            { message },
            undefined,
            signal,
            60000,
        ),
    clearChat: (signal?: AbortSignal) =>
        remove<void>('/user/ai-customer-service/clear-history', signal),
}
