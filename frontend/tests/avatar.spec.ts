import {test,expect} from '@playwright/test'
import path from 'node:path'
const user={id:'9911',username:'现场朋友',email:'avatar@example.invalid',token:'avatar-test'}
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6VQAAAAASUVORK5CYII=','base64')

test('头像预览保存、顶部同步、刷新和恢复默认',async({page})=>{
    let avatar:string|null=null,uploads=0
    await page.addInitScript(value=>sessionStorage.setItem('youxi-auth',JSON.stringify(value)),user)
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        const p=new URL(route.request().url()).pathname
        if(p==='/api/user/avatar'){
            if(route.request().method()==='PUT'){
                uploads++
                expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=')
                expect(route.request().headers()['ticket-token']).toBe(user.token)
                avatar='/media/avatars/avatar-test.png'
            }else avatar=null
            return route.fulfill({json:{code:1,data:{avatar}}})
        }
        return route.fulfill({json:{code:1,data:p==='/api/user/profile'?{...user,avatar}:[]}})
    })
    await page.route('**/media/avatars/**',route=>route.fulfill({body:png,contentType:'image/png'}))
    await page.goto('/account/profile')
    await page.getByLabel('选择头像文件').setInputFiles({name:'me.png',mimeType:'image/png',buffer:png})
    await expect(page.getByAltText('头像预览')).toBeVisible()
    expect(uploads).toBe(0)
    await page.getByRole('button',{name:'保存头像',exact:true}).click()
    await expect(page.getByText('头像已保存',{exact:true})).toBeVisible()
    await expect(page.getByAltText('账户头像')).toHaveAttribute('src',avatar!)
    await page.reload()
    await expect(page.getByAltText('我的头像')).toHaveAttribute('src',avatar!)
    await page.getByRole('button',{name:'恢复默认头像'}).click()
    await expect(page.getByText('已恢复默认头像',{exact:true})).toBeVisible()
    await expect(page.getByAltText('账户头像')).toHaveCount(0)
    expect(uploads).toBe(1)
})

test('拒绝超限与非图片，失败时保留预览且不提示成功',async({page})=>{
    let uploads=0
    await page.addInitScript(value=>sessionStorage.setItem('youxi-auth',JSON.stringify(value)),user)
    await page.route(url=>url.pathname.startsWith('/api/'),route=>{
        if(new URL(route.request().url()).pathname==='/api/user/avatar'){
            uploads++
            return route.fulfill({json:{code:0,msg:'图片保存失败，请重试'}})
        }
        return route.fulfill({json:{code:1,data:route.request().url().includes('/user/profile')?user:[]}})
    })
    await page.goto('/account/profile')
    const input=page.getByLabel('选择头像文件')
    await input.setInputFiles({name:'me.svg',mimeType:'image/svg+xml',buffer:Buffer.from('<svg/>')})
    await expect(page.getByRole('alert')).toContainText('JPG 或 PNG')
    await input.setInputFiles({name:'huge.png',mimeType:'image/png',buffer:Buffer.alloc(2*1024*1024+1)})
    await expect(page.getByRole('alert')).toContainText('2MB')
    expect(uploads).toBe(0)
    await input.setInputFiles({name:'me.png',mimeType:'image/png',buffer:png})
    await page.getByRole('button',{name:'保存头像',exact:true}).click()
    await expect(page.getByRole('alert')).toContainText('图片保存失败')
    await expect(page.getByAltText('头像预览')).toBeVisible()
    await expect(page.getByText('头像已保存',{exact:true})).toHaveCount(0)
})

test('手机头像设置区没有横向溢出，取消选择保留已保存头像',async({page})=>{
    await page.addInitScript(value=>sessionStorage.setItem('youxi-auth',JSON.stringify(value)),user)
    await page.route(url=>url.pathname.startsWith('/api/'),route=>route.fulfill({json:{code:1,data:route.request().url().includes('/user/profile')?user:[]}}))
    for(const width of [1440,390,320]){
        await page.setViewportSize({width,height:950})
        await page.goto('/account/profile')
        await page.getByLabel('选择头像文件').setInputFiles({name:'me.png',mimeType:'image/png',buffer:png})
        await expect(page.getByAltText('头像预览')).toBeVisible()
        expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
        await page.screenshot({path:path.resolve(`../output/avatar-profile-${width}.png`),fullPage:true})
        await page.getByRole('button',{name:'取消选择',exact:true}).click()
        await expect(page.getByRole('button',{name:'保存头像',exact:true})).toHaveCount(0)
    }
})
