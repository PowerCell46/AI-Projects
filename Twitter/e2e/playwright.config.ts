import { defineConfig, devices } from '@playwright/test';

const FRONTEND_PORT = process.env.E2E_FRONTEND_PORT ?? '5273';

const GATEWAY_URL = `http://localhost:${process.env.E2E_GATEWAY_PORT ?? '8180'}`;

const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`;

const FRONTEND_READY_URL = `${FRONTEND_URL}/login`;

const EXPECT_TIMEOUT_MS = 10_000;

// Only the specs that drive a page: the API-only specs would run the same requests twice at phone size.
const PAGE_DRIVEN_SPECS = [/-ui\.spec\.ts$/, /\/auth\.spec\.ts$/];

// Phone-size layout checks: at desktop size the same assertions would fail by design.
const MOBILE_ONLY_SPEC = /\/mobile-layout-ui\.spec\.ts$/;

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    retries: 0,
    reporter: 'html',
    globalSetup: './global-setup.ts',
    globalTeardown: './global-teardown.ts',
    expect: { timeout: EXPECT_TIMEOUT_MS },
    use: {
        baseURL: FRONTEND_URL,
        trace: 'retain-on-failure',
        // The step transitions and the arrival draw collapse to immediate, so navigation is deterministic.
        reducedMotion: 'reduce',
        launchOptions: {
            slowMo: process.env.SLOWMO ? Number(process.env.SLOWMO) : undefined,
        },
    },
    webServer: {
        command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
        cwd: '../frontend',
        url: FRONTEND_READY_URL,
        env: { GATEWAY_URL },
        reuseExistingServer: false,
    },
    projects: [
        {
            name: 'chromium',
            testIgnore: MOBILE_ONLY_SPEC,
            use: { ...devices['Desktop Chrome'] },
        },
        {
            name: 'mobile-chromium',
            testMatch: PAGE_DRIVEN_SPECS,
            use: { ...devices['Pixel 7'] },
        },
    ],
});
