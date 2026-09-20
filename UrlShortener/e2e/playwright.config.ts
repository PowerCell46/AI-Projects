import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    retries: 0,
    reporter: 'html',
    globalSetup: './global-setup.ts',
    globalTeardown: './global-teardown.ts',
    use: {
        baseURL: 'http://localhost:8082',
        trace: 'retain-on-failure',
        launchOptions: {
            slowMo: process.env.SLOWMO ? Number(process.env.SLOWMO) : undefined,
        },
    },
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
    ],
})
