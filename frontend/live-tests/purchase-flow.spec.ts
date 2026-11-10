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
    // 经真实客服草稿工具创建取消卡片，刷新后明确确认，所有API仍透传隔离后端。
    await page.goto('/support')
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await expect(page.getByLabel('咨询内容')).toBeEnabled()
    await page.getByLabel('咨询内容').fill('创建取消确认卡片')
    await page.getByRole('button', { name: '发送消息' }).click()
    const cancelCard = page.getByRole('region', { name: '取消确认卡片' })
    await expect(cancelCard.getByRole('button', { name: '核对取消订单' })).toBeEnabled()
    await page.reload()
    await expect(cancelCard.getByRole('button', { name: '核对取消订单' })).toBeEnabled()
    await page.getByLabel('会话名称').fill('隔离验收取消会话')
    await page.getByRole('button', { name: '保存名称', exact: true }).click()
    await expect(page.getByRole('button', { name: '隔离验收取消会话', exact: true })).toBeVisible()
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.screenshot({ path: '../output/agent-stage4/cancel-confirmation-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: '../output/agent-stage4/cancel-confirmation-mobile.png', fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await cancelCard.getByRole('button', { name: '核对取消订单' }).click()
    await page.getByRole('button', { name: '确认取消此订单' }).click()
    await expect(cancelCard.getByRole('button', { name: '核对取消订单' })).toHaveCount(0)
    // 处理中/未知只允许查询，不能再次触发确认。
    for (let attempt = 0; attempt < 10 && await cancelCard.getByRole('button', { name: '查询原结果' }).isVisible(); attempt++) {
        await cancelCard.getByRole('button', { name: '查询原结果' }).click()
        await page.waitForTimeout(300)
    }
    await cancelCard.getByRole('link', { name: '查看这笔订单' }).click()
    await expect(page.locator('.order-state')).toHaveText('已取消')
    await page.goto('/account/refunds')
    await expect(page.getByRole('heading', { name: '暂无退款记录' })).toBeVisible()
    await page.goto('/support')
    // 等历史会话加载完再创建，避免把咨询发送到仍在恢复的取消会话。
    await expect(page.getByRole('button', { name: '隔离验收取消会话', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await expect(page.getByLabel('会话名称')).toBeEnabled()
    await expect(page.getByLabel('咨询内容')).toBeEnabled()
    await expect(page.locator('.chat-message')).toHaveCount(0)
    await page.getByLabel('咨询内容').fill('订单付款期限多久，刷新会延长吗？')
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.locator('.chat-message.assistant')).toContainText('隔离验收客服回复')
    const source = page.locator('.knowledge-sources a').first()
    await expect(source).toBeVisible()
    await expect(source).toHaveAttribute('href', /^\/support\/knowledge\/KB-[A-Z]+-\d{3}\?version=[1-9]\d*$/)
    await source.click()
    await expect(page.getByRole('heading', { name: '规则正文', exact: true })).toBeVisible()
    await expect(page.locator('.knowledge-card')).toContainText('付款')
    await expect(page.getByRole('heading', { name: '需实时核验', exact: true })).toBeVisible()
    await page.screenshot({ path: '../output/agent-stage3/knowledge-page.png', fullPage: true })
    await page.getByRole('link', { name: '返回客服', exact: true }).click()
    await page.getByLabel('会话名称').fill('隔离验收订单咨询')
    await page.getByRole('button', { name: '保存名称', exact: true }).click()
    await expect(page.getByRole('button', { name: '隔离验收订单咨询', exact: true })).toBeVisible()
    await page.reload()
    await expect(page.locator('.chat-message.assistant').filter({ hasText: '隔离验收客服回复' }).first()).toBeVisible()
    await expect(page.locator('.knowledge-sources a').first()).toBeVisible()
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.screenshot({ path: '../output/agent-stage3/support-citations-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: '../output/agent-stage3/support-citations-mobile.png', fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await expect(page.getByLabel('会话名称')).toBeEnabled()
    await expect(page.getByLabel('咨询内容')).toBeEnabled()
    await expect(page.locator('.chat-message')).toHaveCount(0)
    await page.getByRole('button', { name: '隔离验收取消会话', exact: true }).click()
    await expect(page.getByRole('region', { name: '取消确认卡片' })).toBeVisible()
    await page.getByRole('button', { name: '隔离验收订单咨询', exact: true }).click()
    await expect(page.locator('.chat-message.assistant').filter({ hasText: '隔离验收客服回复' }).first()).toBeVisible()
    await page.getByRole('button', { name: '清空会话', exact: true }).click()
    await page.getByRole('button', { name: '确认清空', exact: true }).click()
    await expect(page.locator('.chat-message')).toHaveCount(0)
    expect(apiFailures).toEqual([])
    expect(pageErrors).toEqual([])
})
