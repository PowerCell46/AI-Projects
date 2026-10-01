import { defineConfig, devices } from '@playwright/test'

const FRONTEND_PORT = 5273
const GATEWAY_URL = 'http://localhost:8180'

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    retries: 0,
    reporter: 'html',
    globalSetup: './global-setup.ts',
    globalTeardown: './global-teardown.ts',
    expect: { timeout: 10_000 },
    use: {
        baseURL: `http://localhost:${FRONTEND_PORT}`,
        trace: 'retain-on-failure',
        // The step transitions and the arrival draw collapse to immediate, so navigation is deterministic.
        reducedMotion: 'reduce',
    },
    webServer: {
        command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
        cwd: '../frontend',
        url: `http://localhost:${FRONTEND_PORT}/login`,
        env: { GATEWAY_URL },
        reuseExistingServer: false,
    },
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
    ],
})
