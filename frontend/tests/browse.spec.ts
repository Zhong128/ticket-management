import { test, expect } from '@playwright/test'

test('首页图文分类可跳转，并保留当前城市', async ({ page }) => {
    await page.goto('/')
    const shortcuts = page.getByRole('navigation', { name: '按类型找演出' })
    await expect(shortcuts.getByRole('link', { name: '音乐剧', exact: true })).toBeVisible()
    await expect(shortcuts.locator('svg').first()).toBeVisible()
    await expect(page.locator('.category-floor')).toHaveCount(8)
    await shortcuts.getByRole('link', { name: '音乐剧', exact: true }).click()
    await expect(page).toHaveURL(/categoryId=/)
    await expect(
        page.locator('.filter-panel').getByRole('button', { name: '音乐剧', exact: true }),
    ).toHaveClass(/active/)
    await expect(page.locator('.event-card').first()).toBeVisible()
    await page.goto('/')
    await page.locator('.city-trigger').click()
    await page.getByRole('dialog').getByRole('button', { name: '临沂', exact: true }).click()
    await shortcuts.getByRole('link', { name: '儿童亲子', exact: true }).click()
    await expect(page).toHaveURL(/cityId=/)
    await expect(page.locator('.city-trigger')).toContainText('临沂')
    await expect(page.locator('.event-card').first()).toContainText('临沂')
})

test('游客首页加载真实演出，并进入详情选择票档', async ({ page, request }) => {
    const api = await request.get('/api/public/events/page?name=' + encodeURIComponent('奇妙萌可'))
    const result = await api.json()
    expect(result.code).toBe(1)
    const event = result.data.records[0]
    expect(event).toBeTruthy()
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '现在，去现场' })).toBeVisible()
    await page.goto(`/events/${event.id}`)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('奇妙萌可')
    await expect(page.getByRole('button', { name: /普通票/ })).toBeEnabled()
    await page.getByRole('button', { name: /优选票/ }).click()
    await page.getByRole('button', { name: '增加数量' }).click()
    await expect(page.locator('#quantity')).toHaveText('2')
    await page.getByRole('button', { name: '立即购票' }).click()
    await expect(page).toHaveURL(/login\?redirect=.*checkout/)
    await page.reload()
    await expect(page.getByRole('heading', { name: '登录 / 注册' })).toBeVisible()
})

test('搜索、筛选及 URL 刷新恢复', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('textbox', { name: '搜索演出或艺人' }).fill('孙燕姿')
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(page.locator('.event-card')).toHaveCount(2)
    await page.locator('.filter-panel').getByRole('button', { name: '广州', exact: true }).click()
    await expect(page.locator('.event-card')).toHaveCount(1)
    await expect(page.locator('.event-card')).toContainText('广州')
    await expect(page.locator('.city-trigger')).toContainText('广州')
    await page.reload()
    await expect(page.locator('.event-card')).toHaveCount(1)
    await expect(page.getByRole('textbox', { name: '搜索演出或艺人' })).toHaveValue('孙燕姿')
    await expect(page.locator('.city-trigger')).toContainText('广州')
})

test('分页与浏览器后退恢复', async ({ page }) => {
    await page.goto('/events')
    await expect(page.locator('.event-card')).toHaveCount(12)
    const first = await page.locator('.event-card').first().getAttribute('href')
    await page.getByRole('button', { name: '下一页' }).click()
    await expect(page).toHaveURL(/page=2/)
    await expect(page.locator('.pagination')).toContainText('第 2')
    await expect(page.locator('.event-card').first()).not.toHaveAttribute('href', first!)
    await page.goBack()
    await expect(page.locator('.event-card').first()).toHaveAttribute('href', first!)
})

test('城市弹窗可用键盘关闭，选择后保存', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '现在，去现场' })).toBeVisible()
    await page.locator('.city-trigger').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await page.locator('.city-trigger').click()
    await page.getByRole('dialog').getByRole('button', { name: '临沂', exact: true }).click()
    await expect(page.locator('.city-trigger')).toContainText('临沂')
    await expect(page.locator('.event-card').first()).toContainText('临沂')
    await page.reload()
    await expect(page.locator('.city-trigger')).toContainText('临沂')
})

test('空结果、不存在详情及 404 有明确反馈', async ({ page }) => {
    await page.goto('/events?name=no-such-event-12345')
    await expect(page.getByRole('heading', { name: '还没有找到合适的演出' })).toBeVisible()
    await page.goto('/events/9223372036854775807')
    await expect(page.getByRole('status')).toContainText('演出不存在或已下架')
    await page.goto('/does-not-exist')
    await expect(page.getByRole('heading', { name: '这一场，还没开演' })).toBeVisible()
})

test('后端失败可重试，海报失败显示替代内容', async ({ page }) => {
    await page.route('**/api/public/events/page*', (route) =>
        route.fulfill({ status: 503, body: '{}' }),
    )
    await page.goto('/events')
    await expect(page.getByRole('heading', { name: '暂时没能加载' })).toBeVisible()
    await page.unroute('**/api/public/events/page*')
    await page.route('**/*', (route) =>
        route.request().resourceType() === 'image' ? route.abort() : route.continue(),
    )
    await page.getByRole('button', { name: '重新加载' }).click()
    await expect(page.locator('.event-card')).toHaveCount(12)
    await expect(page.locator('.poster-fallback').first()).toBeVisible()
})

test('手机首页、搜索和详情无横向溢出', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    for (const path of ['/', '/events', '/events?name=' + encodeURIComponent('孙燕姿')]) {
        await page.goto(path)
        await expect(page.locator('.event-card').first()).toBeVisible()
        expect(
            await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        ).toBe(true)
    }
    await page.locator('.event-card').first().click()
    await expect(page.locator('.detail-info h1')).toBeVisible()
    expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
})
