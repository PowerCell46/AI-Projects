import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';


// React Testing Library only advances fake timers inside its async wrapper when a `jest` global exists;
// without it, every user-event call hangs once vi.useFakeTimers() is on.
vi.stubGlobal('jest', { advanceTimersByTime: vi.advanceTimersByTime });

afterEach(() => {
    cleanup();
});
