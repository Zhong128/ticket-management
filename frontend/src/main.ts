import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles.css'
import './transaction.css'
import './account.css'
import './admin/admin.css'

const app = createApp(App).use(createPinia()).use(router)
// 首次导航确认后再挂载，避免管理地址短暂启动用户端外壳及其请求。
void router.isReady().then(() => app.mount('#app'))
