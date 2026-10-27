import { test, expect } from '@playwright/test'

const admin = {id:'8001',username:'演出运营',email:'admin@example.invalid',token:'admin-token',role:'ADMIN'}
const user = {id:'8002',username:'观众',email:'user@example.invalid',token:'user-token'}
test('后台使用独立会话和布局，管理员过期不清除用户登录', async ({page}) => {
    await page.addInitScript(({admin,user}) => {
        sessionStorage.setItem('youxi-admin-auth',JSON.stringify(admin))
        sessionStorage.setItem('youxi-auth',JSON.stringify(user))
    },{admin,user})
    let expire=false
    const tokens:string[]=[]
    let storefrontCalls=0
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const path=new URL(route.request().url()).pathname
        tokens.push(route.request().headers()['ticket-token'] || '')
        if(path==='/api/user/profile')storefrontCalls++
        if(expire)return route.fulfill({json:{code:401,msg:'登录已失效'}})
        const data=path==='/api/auth/userinfo'?{userId:admin.id,role:'ADMIN'}:
            path.endsWith('/summary')?{onSaleEventCount:12,upcomingSessionCount:5,pendingOrderCount:3,refundAttentionCount:0,asOf:'2026-10-27T10:00:00'}:
            {records:[],total:0,page:1,size:10,totalPages:0}
        return route.fulfill({json:{code:1,data}})
    })
    await page.goto('/admin/dashboard')
    await expect(page.getByRole('heading',{name:'演出运营工作台'})).toBeVisible()
    expect(storefrontCalls).toBe(0)
    expect(tokens.every(token=>token==='admin-token')).toBeTruthy()
    expire=true
    await page.getByRole('button',{name:'刷新数据'}).click()
    await expect(page).toHaveURL(/\/admin\/login/)
    expect(await page.evaluate(()=>JSON.parse(sessionStorage.getItem('youxi-auth')!).token)).toBe('user-token')
})

test('后台登录验证角色，普通账号不能取得后台会话',async({page})=>{
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const path=new URL(route.request().url()).pathname
        return route.fulfill({json:{code:1,data:path.endsWith('/login')?user:{userId:user.id,role:'USER'}}})
    })
    await page.goto('/admin/login')
    await page.getByLabel('用户名',{exact:true}).fill('viewer')
    await page.getByLabel('密码',{exact:true}).fill('password123')
    await page.getByRole('button',{name:'登录管理端'}).click()
    await expect(page.getByRole('alert')).toContainText('没有管理权限')
    expect(await page.evaluate(()=>sessionStorage.getItem('youxi-admin-auth'))).toBeNull()
})

test('后台切换栏目重新核对运行状态',async({page})=>{
    let enabled=true
    await page.addInitScript(value=>sessionStorage.setItem('youxi-admin-auth',JSON.stringify(value)),admin)
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const path=new URL(route.request().url()).pathname
        const data=path.endsWith('/userinfo')?{role:'ADMIN'}:path.endsWith('/app-status')?{tradingEnabled:enabled}:
            path.endsWith('/summary')?{onSaleEventCount:0,upcomingSessionCount:0,pendingOrderCount:0,refundAttentionCount:0,asOf:''}:
            {records:[],total:0,page:1,size:10}
        return route.fulfill({json:{code:1,data}})
    })
    await page.goto('/admin/dashboard')
    await expect(page.getByText('交易已开启',{exact:true})).toBeVisible()
    enabled=false
    await page.getByRole('navigation',{name:'管理端导航'}).getByRole('link',{name:'演出管理',exact:true}).click()
    await expect(page.getByText('交易已关闭',{exact:true})).toBeVisible()
})
