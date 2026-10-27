import { defineConfig } from '@playwright/test'

// 只能由拥有一次性后端的集成测试调用，避免误将验收写入本地业务库。
const target = new URL(process.env.API_PROXY_TARGET || 'http://127.0.0.1:0')
if (
    process.env.LIVE_ISOLATED_ACCEPTANCE !== 'true' ||
    !['localhost', '127.0.0.1'].includes(target.hostname) ||
    Number(target.port) < 10000
) {
    throw new Error('真实联调验收仅允许由隔离后端测试以随机本机端口启动')
}
export default defineConfig({
    testDir: './live-tests',
    outputDir: './test-results-live',
    workers: 1,
    timeout: 120000,
    expect: { timeout: 25000 },
    reporter: 'list',
    use: {
        baseURL: 'http://127.0.0.1:5183',
        channel: 'msedge',
        actionTimeout: 15000,
        navigationTimeout: 20000,
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
    },
    webServer: {
        command: 'npm run dev -- --port 5183 --strictPort',
        url: 'http://127.0.0.1:5183',
        reuseExistingServer: false,
        timeout: 30000,
    },
})
