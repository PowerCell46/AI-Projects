import { useEffect, useLayoutEffect, useRef } from 'react';
import type { RefObject } from 'react';
import { prefersReducedMotion } from '../../utils/browser/motion';


const GLIDE_MS = 350;

// A transition that never ends (the element left the page, or nothing changed) must not keep the height fixed.
const GLIDE_FALLBACK_MS = GLIDE_MS + 150;

export interface HeightGlide<T extends HTMLElement> {
    ref: RefObject<T | null>;
    captureHeight: () => void;
}

// Lets an element change its content, and so its height, as one glide instead of a jump: `captureHeight` is called
// right before the content changes, and once the new content is in place (`changeKey` changed) the height runs from
// the captured value to the new natural one, so what follows moves in one motion. While it runs the element carries
// `data-gliding`, for a fade of its new content.
export function useHeightGlide<T extends HTMLElement>(changeKey: unknown): HeightGlide<T> {
    const ref = useRef<T>(null);
    const capturedHeightRef = useRef<number | null>(null);
    const fallbackTimerIdRef = useRef<number | null>(null);

    function captureHeight() {
        capturedHeightRef.current = ref.current?.offsetHeight ?? null;
    }

    useLayoutEffect(() => {
        const element = ref.current;
        const fromHeight = capturedHeightRef.current;

        capturedHeightRef.current = null;

        if (!element || fromHeight === null || prefersReducedMotion()) {
            return;
        }

        const toHeight = element.offsetHeight;

        if (fromHeight === toHeight) {
            return;
        }

        function finish() {
            if (fallbackTimerIdRef.current !== null) {
                window.clearTimeout(fallbackTimerIdRef.current);
                fallbackTimerIdRef.current = null;
            }

            element?.removeEventListener('transitionend', handleTransitionEnd);
            element?.style.removeProperty('height');
            element?.style.removeProperty('overflow');
            element?.style.removeProperty('transition');
            element?.removeAttribute('data-gliding');
        }

        function handleTransitionEnd(event: TransitionEvent) {
            if (event.target === element && event.propertyName === 'height') {
                finish();
            }
        }

        element.dataset.gliding = 'true';
        element.style.height = `${fromHeight}px`;
        element.style.overflow = 'hidden';
        // Reading the layout makes the browser take the start height before the transition is set.
        element.getBoundingClientRect();
        element.style.transition = `height ${GLIDE_MS}ms var(--e)`;
        element.style.height = `${toHeight}px`;
        element.addEventListener('transitionend', handleTransitionEnd);
        fallbackTimerIdRef.current = window.setTimeout(finish, GLIDE_FALLBACK_MS);
    }, [changeKey]);

    useEffect(() => () => {
        if (fallbackTimerIdRef.current !== null) {
            window.clearTimeout(fallbackTimerIdRef.current);
        }
    }, []);

    return {
        ref,
        captureHeight,
    };
}
