import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';


const GATEWAY_URL = process.env.GATEWAY_URL ?? 'http://localhost:8080';

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            '/api': {
                target: GATEWAY_URL,
                changeOrigin: true,
            },
        },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
    },
});
