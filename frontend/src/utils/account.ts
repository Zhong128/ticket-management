import type { AddressInput, Profile } from '../api/accountTypes'

export function profilePayload(value: Partial<Profile>) {
    return {
        username: value.username?.trim() || undefined,
        nickname: value.nickname?.trim(),
        realName: value.realName?.trim(),
        phone: value.phone?.trim() || undefined,
        gender: value.gender,
        birthday: value.birthday || undefined,
    }
}
export function addressPayload<T extends AddressInput>(value: T) {
    return {
        receiverName: value.receiverName.trim(),
        receiverPhone: value.receiverPhone.trim(),
        province: value.province.trim(),
        city: value.city.trim(),
        district: value.district?.trim() || '',
        detailAddress: value.detailAddress.trim(),
        ...(value.postalCode?.trim() ? { postalCode: value.postalCode.trim() } : {}),
    }
}
export function validatePassword(oldPassword: string, next: string, confirmation: string) {
    if (!oldPassword) return '请输入当前密码'
    if (next.length < 6 || next.length > 20 || !next.trim()) return '新密码需要6–20位'
    if (oldPassword === next) return '新密码不能与当前密码相同'
    if (next !== confirmation) return '两次输入的新密码不一致'
    return ''
}
export function refundLabel(status: string) {
    const labels: Record<string, string> = {
        REQUESTED: '待退款',
        PROCESSING: '退款处理中',
        UNKNOWN: '结果确认中',
        RETRYABLE_FAILED: '等待重试',
        MANUAL_REQUIRED: '待人工处理',
        SUCCESS: '退款成功',
    }
    return labels[status] || '状态待确认'
}
export function displayTime(value?: string | number[]) {
    if (!value) return '暂无记录'
    if (Array.isArray(value)) {
        const [y, m, d, h = 0, min = 0] = value
        return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`
    }
    return value.replace('T', ' ').slice(0, 19)
}
