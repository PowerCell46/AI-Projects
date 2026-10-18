import { useEffect, useRef } from 'react';
import type { RefObject } from 'react';
import { prefersReducedMotion } from '../../utils/browser/motion';


const COLLAPSE_MS = 350;

// A transition that never ends must not hold the removal back.
const COLLAPSE_FALLBACK_MS = COLLAPSE_MS + 150;

export interface Collapse<T extends HTMLElement> {
    ref: RefObject<T | null>;
    collapse: () => Promise<void>;
}

// Folds an element away (its height and padding to nothing, and a fade) before the caller removes it, so what follows
// glides up instead of jumping. An element that has no layout (a test DOM) or with reduced motion is not waited for.
export function useCollapse<T extends HTMLElement>(): Collapse<T> {
    const ref = useRef<T>(null);
    const fallbackTimerIdRef = useRef<number | null>(null);

    useEffect(() => () => {
        if (fallbackTimerIdRef.current !== null) {
            window.clearTimeout(fallbackTimerIdRef.current);
        }
    }, []);

    function collapse(): Promise<void> {
        const element = ref.current;

        if (!element || element.offsetHeight === 0 || prefersReducedMotion()) {
            return Promise.resolve();
        }

        return new Promise((resolve) => {
            function finish() {
                if (fallbackTimerIdRef.current !== null) {
                    window.clearTimeout(fallbackTimerIdRef.current);
                    fallbackTimerIdRef.current = null;
                }

                element?.removeEventListener('transitionend', handleTransitionEnd);
                resolve();
            }

            function handleTransitionEnd(event: TransitionEvent) {
                if (event.target === element && event.propertyName === 'height') {
                    finish();
                }
            }

            element.style.height = `${element.offsetHeight}px`;
            element.style.overflow = 'hidden';
            // Reading the layout makes the browser take the start height before the transition is set.
            element.getBoundingClientRect();
            element.style.transition = [
                `height ${COLLAPSE_MS}ms var(--e)`,
                `padding ${COLLAPSE_MS}ms var(--e)`,
                `opacity ${COLLAPSE_MS}ms var(--e)`,
            ].join(', ');
            element.style.height = '0';
            element.style.paddingTop = '0';
            element.style.paddingBottom = '0';
            element.style.opacity = '0';
            element.addEventListener('transitionend', handleTransitionEnd);
            fallbackTimerIdRef.current = window.setTimeout(finish, COLLAPSE_FALLBACK_MS);
        });
    }

    return {
        ref,
        collapse,
    };
}
