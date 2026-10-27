import { describe, expect, it } from 'vitest'
import { addressPayload, profilePayload, refundLabel, validatePassword } from './account'

describe('个人中心字段与状态', () => {
    it('省略可选空值和身份字段，保留可清空昵称', () => {
        expect(
            profilePayload({
                username: '  viewer  ',
                email: 'not-editable@example.invalid',
                nickname: '',
                phone: '',
                gender: 0,
            }),
        ).toEqual({ username: 'viewer', nickname: '', gender: 0 })
        const address = addressPayload({
            id: '1',
            userId: '2',
            receiverName: ' 小林 ',
            receiverPhone: '13800138000',
            province: '北京市',
            city: '北京市',
            district: '',
            detailAddress: '现场路1号',
            postalCode: '',
            isDefault: 1,
        })
        expect(address).not.toHaveProperty('postalCode')
        expect(address).not.toHaveProperty('id')
        expect(address).not.toHaveProperty('userId')
        expect(address).not.toHaveProperty('isDefault')
        expect(address.receiverName).toBe('小林')
    })
    it('原密码不正确格式、重复及确认不一致时阻止提交', () => {
        expect(validatePassword('old-pass', 'short', 'short')).toBeTruthy()
        expect(validatePassword('old-pass', 'new-pass', 'different')).toBeTruthy()
        expect(validatePassword('old-pass', 'old-pass', 'old-pass')).toBeTruthy()
        expect(validatePassword('old-pass', 'new-pass', 'new-pass')).toBe('')
    })
    it('退款未知或处理中不显示已到账', () => {
        expect(refundLabel('SUCCESS')).toBe('退款成功')
        expect(refundLabel('UNKNOWN')).toBe('结果确认中')
        expect(refundLabel('MANUAL_REQUIRED')).toBe('待人工处理')
        expect(refundLabel('new-state')).toBe('状态待确认')
    })
})
