import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';


// jsdom ships no matchMedia, so anything reading prefers-reduced-motion (the VHS sweep hooks)
// would throw under test. Stub it to "no preference" — the default behaviour we want to verify.
window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
}));


// Unmount React trees between tests so the jsdom DOM never leaks state across cases.
afterEach(() => {
    cleanup();
});
