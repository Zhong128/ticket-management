import { test, expect, type Page } from '@playwright/test'

async function login(page: Page, email: string, password: string) {
    await page.getByLabel('邮箱', { exact: true }).fill(email)
    await page.getByLabel('密码', { exact: true }).fill(password)
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await expect(page).not.toHaveURL(/\/login(?:[?#]|$)/)
}
async function logout(page: Page) {
    await page.getByRole('button', { name: /^账户菜单：/ }).hover()
    await page
        .getByRole('navigation', { name: '账户菜单', exact: true })
        .getByRole('button', { name: '退出登录', exact: true })
        .click()
    await expect(page).toHaveURL(/\/login/)
}
async function submitSelection(page: Page, quantity: number) {
    await expect(page.getByRole('heading', { name: '隔离联调音乐会', level: 1 })).toBeVisible()
    for (let i = 1; i < quantity; i++) await page.getByRole('button', { name: '增加数量' }).click()
    await page.getByRole('button', { name: '立即购票' }).click()
}
async function confirm(page: Page) {
    await expect(page.getByRole('heading', { name: '确认购票信息' })).toBeVisible()
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: '确认并提交申请' }).click()
    await expect(page.getByRole('link', { name: '去支付', exact: true })).toBeVisible()
}

test('隔离真实API完整流程：游客选票、建单付款、账户维护、另一用户取消与客服', async ({
    page,
    context,
}) => {
    const eventId = process.env.LIVE_EVENT_ID!
    const password = 'journey-test-pass'
    const email = 'journey-7501@example.invalid'
    const apiFailures: string[] = []
    const pageErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('response', (response) => {
        if (new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 500)
            apiFailures.push(response.url())
    })
    // 仅拦截外部沙箱网站，所有 /api 均透传真实隔离后端。
    await context.route('https://openapi-sandbox.dl.alipaydev.com/**', (route) =>
        route.fulfill({ contentType: 'text/html', body: '<h1>隔离支付网关页面</h1>' }),
    )
    await page.goto(`/events/${eventId}`)
    await submitSelection(page, 2)
    await expect(page).toHaveURL(/login\?redirect=.*checkout/)
    await login(page, email, password)
    await confirm(page)
    await page.reload()
    await expect(page.getByRole('link', { name: '去支付', exact: true })).toBeVisible()
    await page.getByRole('link', { name: '去支付', exact: true }).click()
    const submitted = context.waitForEvent('request', {
        predicate: (request) =>
            request.url().startsWith('https://openapi-sandbox.dl.alipaydev.com/') &&
            request.method() === 'POST',
    })
    await page.getByRole('button', { name: '前往支付宝支付' }).click()
    await submitted
    await expect(page.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
    await page.getByRole('link', { name: '查看订单详情', exact: true }).click()
    await expect(page.locator('.order-state')).toHaveText('已支付')
    await expect(page.getByRole('button', { name: '取消订单', exact: true })).toHaveCount(0)

    await page.goto('/account/profile')
    await page.getByLabel('昵称', { exact: true }).fill('真实联调现场朋友')
    await page.getByRole('button', { name: '保存资料' }).click()
    await expect(page.getByText('资料已保存', { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByLabel('昵称', { exact: true })).toHaveValue('真实联调现场朋友')
    await page.goto('/account/addresses')
    await page.getByRole('button', { name: '新增地址', exact: true }).click()
    for (const [name, value] of Object.entries({
        收件人: '小林',
        联系电话: '13800138000',
        省份: '北京市',
        城市: '北京市',
        详细地址: '隔离验收路1号',
    }))
        await page.getByLabel(name, { exact: true }).fill(value)
    await page.getByRole('button', { name: '保存地址' }).click()
    await expect(page.locator('.address-card')).toHaveCount(1)
    await expect(page.locator('.default-address')).toContainText('隔离验收路1号')
    await page.reload()
    await expect(page.locator('.default-address')).toBeVisible()
    await page.goto('/account/security')
    await page.getByLabel('当前密码', { exact: true }).fill(password)
    await page.getByLabel('新密码', { exact: true }).fill('browser-new-pass')
    await page.getByLabel('确认新密码', { exact: true }).fill('browser-new-pass')
    await page.getByRole('button', { name: '修改密码', exact: true }).click()
    await expect(page.getByText('密码已修改，下次登录请使用新密码')).toBeVisible()
    await logout(page)
    await login(page, email, 'browser-new-pass')
    await page.goto('/account/orders')
    await expect(page.locator('.order-card')).toHaveCount(1)
    await expect(page.locator('.order-state')).toHaveText('已支付')

    await logout(page)
    await login(page, 'journey-7502@example.invalid', password)
    await page.goto(`/events/${eventId}`)
    await submitSelection(page, 1)
    await confirm(page)
    await page.getByRole('link', { name: '查看订单', exact: true }).click()
    await page.getByRole('button', { name: '取消订单', exact: true }).click()
    await page.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(page.locator('.order-state')).toHaveText('已取消')
    await page.goto('/account/refunds')
    await expect(page.getByRole('heading', { name: '暂无退款记录' })).toBeVisible()
    await page.goto('/support')
    await page.getByLabel('咨询内容').fill('如何查看我的订单？')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.locator('.chat-message.assistant')).toContainText('隔离验收客服回复')
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect(page.locator('.chat-message')).toHaveCount(0)
    expect(apiFailures).toEqual([])
    expect(pageErrors).toEqual([])
})
