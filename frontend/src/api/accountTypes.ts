export interface Profile {
    username: string
    email: string
    nickname?: string
    phone?: string
    avatar?: string
    realName?: string
    gender?: number
    birthday?: string
}
export interface AddressInput {
    receiverName: string
    receiverPhone: string
    province: string
    city: string
    district?: string
    detailAddress: string
    postalCode?: string
}
export interface Address extends AddressInput {
    id: string
    userId?: string
    isDefault: number
    fullAddress?: string
    formattedCreateTime?: string
}
// 退款接口沿用Mapper返回的下划线字段，不转换或截断订单ID。
export interface Refund {
    refund_no: string
    payment_no: string
    order_id: string
    amount: number
    reason: string
    status: string
    created_at: string
    updated_at: string
}
