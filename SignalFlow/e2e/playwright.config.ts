import { defineConfig, devices } from '@playwright/test';
import { FRONTEND_URL } from './support/constants';

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    retries: 0,
    reporter: 'html',
    globalSetup: './global-setup.ts',
    globalTeardown: './global-teardown.ts',
    use: {
        baseURL: FRONTEND_URL,
        trace: 'retain-on-failure',
        // The app skips its success-curtain animation under prefers-reduced-motion,
        // which makes auth navigation deterministic instead of ~2s of animation.
        reducedMotion: 'reduce',
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
});
