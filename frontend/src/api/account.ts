import { get, post, put, remove, request } from './client'
import type { Address, AddressInput, Profile, Refund } from './accountTypes'

export const account = {
    status: (signal?: AbortSignal) => get<{ customerServiceEnabled?: boolean }>('/public/app-status', undefined, signal),
    profile: (signal?: AbortSignal) => get<Profile>('/user/profile', undefined, signal),
    saveProfile: (data: Partial<Profile>) => put<void>('/user/profile', data),
    uploadAvatar: (file: File, signal?: AbortSignal) => {
        const data = new FormData()
        data.append('file', file)
        return request<{avatar: string}>('put', '/user/avatar', data, undefined, signal)
    },
    resetAvatar: (signal?: AbortSignal) => remove<{avatar: null}>('/user/avatar', signal),
    password: (oldPassword: string, newPassword: string) =>
        request<void>('put', '/user/password', new URLSearchParams({ oldPassword, newPassword })),
    addresses: (signal?: AbortSignal) => get<Address[]>('/user/address', undefined, signal),
    addAddress: (data: AddressInput) => post<void>('/user/address', data),
    saveAddress: (id: string, data: AddressInput) =>
        put<void>(`/user/address/${encodeURIComponent(id)}`, data),
    defaultAddress: (id: string) => post<void>(`/user/address/${encodeURIComponent(id)}/default`),
    deleteAddress: (id: string) => remove<void>(`/user/address/${encodeURIComponent(id)}`),
    refunds: (signal?: AbortSignal) => get<Refund[]>('/user/refunds', undefined, signal),
}
