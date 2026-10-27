import { test, expect } from '@playwright/test'

test('账户下拉支持悬停、键盘、页面跳转与退出', async ({ page }) => {
    await page.addInitScript(() =>
        sessionStorage.setItem(
            'youxi-auth',
            JSON.stringify({
                id: '801',
                username: '现场朋友',
                email: 'menu@example.invalid',
                token: 'menu-fixture',
            }),
        ),
    )
    let loggedOut = false
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        async (route) => {
            const path = new URL(route.request().url()).pathname
            if (path === '/api/auth/logout') loggedOut = true
            await route.fulfill({
                json: {
                    code: 1,
                    data:
                        path === '/api/user/profile'
                            ? { username: '现场朋友', email: 'menu@example.invalid' }
                            : [],
                },
            })
        },
    )
    await page.goto('/account/security')
    const trigger = page.getByRole('button', { name: '账户菜单：现场朋友', exact: true })
    const menu = page.getByRole('navigation', { name: '账户菜单', exact: true })
    await expect(menu).toHaveCount(0)
    await trigger.hover()
    await expect(menu).toBeVisible()
    await expect(menu.getByRole('link', { name: '个人信息', exact: true })).toHaveAttribute(
        'href',
        '/account/profile',
    )
    await expect(menu.getByRole('link', { name: '账户设置', exact: true })).toHaveAttribute(
        'href',
        '/account/security',
    )
    await expect(menu.getByRole('link', { name: '订单管理', exact: true })).toHaveAttribute(
        'href',
        '/account/orders',
    )
    await page.locator('main h1').click()
    await expect(menu).toHaveCount(0)
    await trigger.focus()
    await page.keyboard.press('ArrowDown')
    await expect(menu.getByRole('link', { name: '个人信息', exact: true })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(menu).toHaveCount(0)
    await expect(trigger).toBeFocused()
    await trigger.click()
    await menu.getByRole('link', { name: '个人信息', exact: true }).click()
    await expect(page).toHaveURL(/account\/profile/)
    await expect(menu).toHaveCount(0)
    await trigger.hover()
    await menu.getByRole('button', { name: '退出登录', exact: true }).click()
    await expect(page).toHaveURL(/login/)
    expect(loggedOut).toBe(true)
    expect(await page.evaluate(() => sessionStorage.getItem('youxi-auth'))).toBeNull()
})

test('游客手机账户下拉可点击，选择订单后进入带回跳地址的登录页', async ({ browser }) => {
    const context = await browser.newContext({
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        baseURL: 'http://127.0.0.1:5173',
    })
    const page = await context.newPage()
    await page.route(
        (url) => url.pathname.startsWith('/api/'),
        (route) => route.fulfill({ json: { code: 1, data: [] } }),
    )
    await page.goto('/login')
    const trigger = page.getByRole('button', { name: '账户菜单：登录 / 注册', exact: true })
    const menu = page.getByRole('navigation', { name: '账户菜单', exact: true })
    await trigger.tap()
    await expect(menu).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await trigger.tap()
    await expect(menu).toHaveCount(0)
    await trigger.tap()
    await menu.getByRole('link', { name: '订单管理', exact: true }).tap()
    await expect(page).toHaveURL(/login\?redirect=.*account.*orders/)
    await context.close()
})
