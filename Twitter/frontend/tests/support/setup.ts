import './animationEvent';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { IntersectionObserverDouble, resetIntersectionObservers } from './intersectionObserver';


// React Testing Library only advances fake timers inside its async wrapper when a `jest` global exists;
// without it, every user-event call hangs once vi.useFakeTimers() is on.
vi.stubGlobal(
    'jest',
    { advanceTimersByTime: vi.advanceTimersByTime },
);

// Stubbed before every test, because a test file may call vi.unstubAllGlobals() when it finishes a test.
beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', IntersectionObserverDouble);

    // jsdom has no object URLs; the compose previews need them.
    URL.createObjectURL = vi.fn((file: Blob | MediaSource) => {
        const name = file instanceof File ? file.name : 'media';

        return `blob:preview-${name}`;
    });
    URL.revokeObjectURL = vi.fn();

    // jsdom does not implement scrolling.
    window.scrollTo = vi.fn();
});

afterEach(() => {
    cleanup();
    resetIntersectionObservers();
});
