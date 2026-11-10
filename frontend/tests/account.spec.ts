import { test, expect, type Page } from '@playwright/test'
const user = {
    id: '7001',
    username: 'viewer7001',
    email: 'viewer@example.invalid',
    token: 'account-fixture-token',
}
const baseAddress = {
    receiverName: '小林',
    receiverPhone: '13800138000',
    province: '北京市',
    city: '北京市',
    district: '海淀区',
    detailAddress: '现场路1号',
    postalCode: '',
}
async function setup(page: Page) {
    await page.addInitScript(
        (info) => sessionStorage.setItem('youxi-auth', JSON.stringify(info)),
        user,
    )
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        (route) =>
            route.fulfill({
                json: {
                    code: 1,
                    data:
                        new URL(route.request().url()).pathname === '/api/user/profile'
                            ? {
                                  username: user.username,
                                  email: user.email,
                                  nickname: '现场朋友',
                                  gender: 0,
                              }
                            : [],
                },
            }),
    )
}

test('资料保存传可编辑字段，失败不提示成功，刷新读取服务端资料', async ({ page }) => {
    await setup(page)
    let nickname = '现场朋友',
        fail = true
    await page.route('**/api/user/profile', async (route) => {
        if (route.request().method() === 'PUT') {
            const data = route.request().postDataJSON()
            expect(data).not.toHaveProperty('email')
            expect(data).not.toHaveProperty('id')
            expect(data).not.toHaveProperty('status')
            if (fail) {
                await route.fulfill({ json: { code: 0, msg: '用户名已存在' } })
                return
            }
            nickname = data.nickname
            await route.fulfill({ json: { code: 1 } })
            return
        }
        await route.fulfill({ json: { code: 1, data: { ...user, nickname } } })
    })
    await page.goto('/account/profile')
    await page.getByLabel('昵称', { exact: true }).fill('周末看现场')
    await page.getByRole('button', { name: '保存资料' }).click()
    await expect(page.getByRole('alert')).toHaveText('用户名已存在')
    await expect(page.getByText('资料已保存', { exact: true })).toHaveCount(0)
    fail = false
    await page.getByRole('button', { name: '保存资料' }).click()
    await expect(page.getByText('资料已保存', { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByLabel('昵称', { exact: true })).toHaveValue('周末看现场')
})

test('修改密码使用表单请求体，校验确认且不将密码放入URL或存储', async ({ page }) => {
    await setup(page)
    let writes = 0
    await page.route('**/api/user/password', async (route) => {
        writes++
        expect(route.request().url()).not.toContain('oldPassword')
        const form = new URLSearchParams(route.request().postData() || '')
        expect(form.get('oldPassword')).toBe('old-pass-123')
        expect(form.get('newPassword')).toBe('new-pass-456')
        expect(route.request().headers()['content-type']).toContain(
            'application/x-www-form-urlencoded',
        )
        await route.fulfill({ json: { code: 1 } })
    })
    await page.goto('/account/security')
    await page.getByLabel('当前密码', { exact: true }).fill('old-pass-123')
    await page.getByLabel('新密码', { exact: true }).fill('new-pass-456')
    await page.getByLabel('确认新密码', { exact: true }).fill('different-pass')
    await page.getByRole('button', { name: '修改密码', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('两次输入的新密码不一致')
    expect(writes).toBe(0)
    await page.getByLabel('确认新密码', { exact: true }).fill('new-pass-456')
    await page.getByRole('button', { name: '修改密码', exact: true }).click()
    await expect(page.getByText('密码已修改，下次登录请使用新密码')).toBeVisible()
    await expect(page.getByLabel('当前密码', { exact: true })).toHaveValue('')
    expect(
        await page.evaluate(() => JSON.stringify({ ...sessionStorage, ...localStorage })),
    ).not.toContain('pass-')
})

test('地址新增编辑、独立设置默认和非默认删除均调用对应接口', async ({ page }) => {
    await setup(page)
    let records = [{ ...baseAddress, id: '7101', isDefault: 1 }]
    await page.route('**/api/user/address', async (route) => {
        if (route.request().method() === 'POST') {
            const body = route.request().postDataJSON()
            expect(body).not.toHaveProperty('postalCode')
            expect(body).not.toHaveProperty('isDefault')
            records.push({ ...body, id: '7102', isDefault: 0 })
            await route.fulfill({ json: { code: 1 } })
        } else await route.fulfill({ json: { code: 1, data: records } })
    })
    await page.route('**/api/user/address/7102/default', async (route) => {
        records = records.map((a) => ({ ...a, isDefault: a.id === '7102' ? 1 : 0 }))
        await route.fulfill({ json: { code: 1 } })
    })
    await page.route('**/api/user/address/7101', async (route) => {
        if (route.request().method() === 'DELETE') records = records.filter((a) => a.id !== '7101')
        else records[0] = { ...records[0]!, ...route.request().postDataJSON() }
        await route.fulfill({ json: { code: 1 } })
    })
    await page.goto('/account/addresses')
    const original = page.locator('.address-card').first()
    await expect(original.getByRole('button', { name: '删除', exact: true })).toBeDisabled()
    await original.getByRole('button', { name: '编辑', exact: true }).click()
    await page.getByLabel('详细地址', { exact: true }).fill('音乐路2号')
    await page.getByRole('button', { name: '保存地址' }).click()
    await expect(page.locator('.default-address')).toContainText('音乐路2号')
    await page.getByRole('button', { name: '新增地址', exact: true }).click()
    for (const [label, value] of Object.entries({
        收件人: '小张',
        联系电话: '13900139000',
        省份: '上海市',
        城市: '上海市',
        详细地址: '剧场路3号',
    }))
        await page.getByLabel(label, { exact: true }).fill(value)
    await page.getByRole('button', { name: '保存地址' }).click()
    await expect(page.locator('.address-card')).toHaveCount(2)
    await page.getByRole('button', { name: '设为默认' }).click()
    await expect(page.locator('.default-address')).toContainText('小张')
    await page
        .locator('.address-card')
        .filter({ hasText: '小林' })
        .getByRole('button', { name: '删除', exact: true })
        .click()
    await page.getByRole('button', { name: '确认删除' }).click()
    await expect(page.locator('.address-card')).toHaveCount(1)
})

test('退款保留长订单ID，未知状态不当作成功，按状态过滤', async ({ page }) => {
    await setup(page)
    await page.route('**/api/user/refunds', (route) =>
        route.fulfill({
            contentType: 'application/json',
            body: '{"code":1,"data":[{"refund_no":"RF-A","payment_no":"P-A","order_id":9007199254740993123,"amount":360,"reason":"LATE_OR_DUPLICATE_PAYMENT","status":"UNKNOWN","created_at":"2026-10-26T09:00:00","updated_at":"2026-10-26T09:01:00"},{"refund_no":"RF-B","order_id":99,"amount":180,"status":"SUCCESS"}]}',
        }),
    )
    await page.goto('/account/refunds')
    await expect(page.locator('.refund-card')).toHaveCount(2)
    await expect(page.getByRole('link', { name: '查看关联订单' }).first()).toHaveAttribute(
        'href',
        '/account/orders/9007199254740993123',
    )
    await expect(page.locator('.refund-card').first()).toContainText('结果确认中')
    await page.getByRole('button', { name: '退款成功', exact: true }).click()
    await expect(page.locator('.refund-card')).toHaveCount(1)
    await expect(page.locator('.refund-card')).toContainText('RF-B')
    await expect(page.getByRole('button', { name: /申请退款|重试退款/ })).toHaveCount(0)
})

test('客服消息纯文本渲染，清空成功前保留消息，失败可重新编辑', async ({ page }) => {
    await setup(page)
    let fail = false,
        clears = 0
    await page.route('**/api/public/app-status', route => route.fulfill({ json: { code: 1, data: { customerServiceEnabled: true } } }))
    await page.route('**/api/user/ai-customer-service/conversations?*', route => route.fulfill({ json: { code: 1, data: { records: [{ id: 'c1', title: '默认会话', activeRunId: null }], hasNext: false } } }))
    await page.route('**/api/user/ai-customer-service/conversations/c1', route => route.fulfill({ json: { code: 1, data: { id: 'c1', title: '默认会话', activeRunId: null } } }))
    await page.route('**/api/user/ai-customer-service/conversations/c1/messages?*', route => route.fulfill({ json: { code: 1, data: { records: [], hasNext: false } } }))
    await page.route('**/api/user/ai-customer-service/conversations/c1/messages/stream', async route => {
        const run = { id: 'r1', status: fail ? 'FAILED' : 'SUCCEEDED', reply: fail ? null : '<img src=x onerror="window.__chatInjected=true">请查看我的订单。', errorMessage: fail ? '智能客服暂时不可用，请稍后重试' : null }
        await route.fulfill({ contentType: 'text/event-stream', body: `event: run_started\ndata: {"runId":"r1"}\n\nevent: done\ndata: ${JSON.stringify(run)}\n\n` })
    })
    await page.route('**/api/user/ai-customer-service/conversations/c1/messages', async route => {
        clears++
        await route.fulfill({ json: clears === 1 ? { code: 0, msg: '清空失败' } : { code: 1 } })
    })
    await page.goto('/support')
    await page.getByRole('button', { name: '如何查看我的订单？', exact: true }).click()
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.locator('.chat-message.assistant')).toContainText('<img src=x')
    expect(await page.evaluate(() => '__chatInjected' in window)).toBe(false)
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('清空失败')
    await expect(page.locator('.chat-message')).toHaveCount(2)
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect(page.locator('.chat-message')).toHaveCount(0)
    fail = true
    await page.getByLabel('咨询内容').fill('查询音乐会')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.getByRole('button', { name: '未收到回复，重新编辑' })).toBeVisible()
    await page.getByRole('button', { name: '未收到回复，重新编辑' }).click()
    await expect(page.getByLabel('咨询内容')).toHaveValue('查询音乐会')
})

test('个人中心导航和手机表单无横向溢出', async ({ page }) => {
    await setup(page)
    await page.setViewportSize({ width: 390, height: 844 })
    for (const path of [
        '/account/profile',
        '/account/security',
        '/account/addresses',
        '/account/refunds',
        '/support',
    ]) {
        await page.goto(path)
        await expect(page.getByRole('navigation', { name: '个人中心导航' })).toBeVisible()
        await expect(page.locator('main h1')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
            true,
        )
    }
})
