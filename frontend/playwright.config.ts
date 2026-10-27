import { defineConfig } from '@playwright/test'

export default defineConfig({
    testDir: './tests',
    fullyParallel: false,
    workers: 1,
    timeout: 30000,
    expect: { timeout: 10000 },
    reporter: 'list',
    use: {
        baseURL: 'http://127.0.0.1:5173',
        channel: 'msedge',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
    },
    webServer: { command: 'npm run dev', url: 'http://127.0.0.1:5173', reuseExistingServer: true },
})
