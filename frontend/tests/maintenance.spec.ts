import { test, expect } from '@playwright/test'

test('客服维护状态不显示输入框，也不发出模型聊天请求',async({page})=>{
    await page.addInitScript(()=>sessionStorage.setItem('youxi-auth',JSON.stringify({id:'9901',username:'维护验收',email:'maintenance@example.invalid',token:'maintenance-fixture'})))
    let chats=0
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const path=new URL(route.request().url()).pathname
        if(path.includes('/ai-customer-service/'))chats++
        return route.fulfill({json:{code:1,data:path==='/api/public/app-status'?{ready:true,tradingEnabled:true,customerServiceEnabled:false}:path==='/api/user/profile'?{username:'维护验收',email:'maintenance@example.invalid'}:[]}})
    })
    await page.goto('/support')
    await expect(page.getByRole('heading',{name:'功能维护中',exact:true})).toBeVisible()
    await expect(page.getByLabel('咨询内容')).toHaveCount(0)
    await expect(page.getByRole('link',{name:'查看我的订单',exact:true})).toHaveAttribute('href','/account/orders')
    expect(chats).toBe(0)
})
