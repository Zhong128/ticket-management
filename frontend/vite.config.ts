import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

// 跟随本地脚本实际选用的端口；隔离测试或手动配置仍优先使用环境变量。
function localApiTarget() {
    if (process.env.API_PROXY_TARGET) return process.env.API_PROXY_TARGET
    try {
        const state = JSON.parse(readFileSync(new URL('../output/youxi-local/application.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''))
        if (Number.isInteger(state.port) && state.port >= 1024 && state.port <= 65535)
            return `http://127.0.0.1:${state.port}`
    } catch {
        // 尚未运行本地启动脚本时，沿用默认端口。
    }
    return 'http://127.0.0.1:8082'
}

export default defineConfig({
    plugins: [vue()],
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: localApiTarget(),
                changeOrigin: true,
            },
            '/media/avatars': {
                target: localApiTarget(),
                changeOrigin: true,
            },
        },
    },
    test: { environment: 'node', include: ['src/**/*.test.ts'] },
})
