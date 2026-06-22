import { useEffect, useRef } from 'react';


const FOCUSABLE_SELECTOR = [
    'a[href]',
    'button:not([disabled])',
    'textarea:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
].join(', ');


/**
 * Wires the shared modal-dialog behaviours onto a container element: moves focus inside on open,
 * traps Tab within it, closes on Escape, and restores focus to the previously focused element on
 * close. Attach the returned ref to the dialog element (give it `tabIndex={-1}` as a focus
 * fallback). `onClose` is read through a ref, so passing a fresh closure each render is fine and
 * never re-runs the setup.
 */
export const useModalBehavior = <T extends HTMLElement>(onClose: () => void) => {
    const containerRef = useRef<T>(null);
    const onCloseRef = useRef(onClose);

    // Keep the latest onClose without re-running the setup effect (which would re-steal focus).
    useEffect(() => {
        onCloseRef.current = onClose;
    });

    useEffect(() => {
        const container = containerRef.current;

        if (container === null) {
            return;
        }

        const previouslyFocused = document.activeElement as HTMLElement | null;
        const focusables = (): HTMLElement[] =>
            Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));

        (focusables()[0] ?? container).focus();

        const handleKeyDown = (event: KeyboardEvent): void => {
            if (event.key === 'Escape') {
                event.stopPropagation();
                onCloseRef.current();

                return;
            }

            if (event.key !== 'Tab') {
                return;
            }

            const items = focusables();
            const first = items[0];
            const last = items[items.length - 1];

            if (first === undefined || last === undefined) {
                event.preventDefault();

                return;
            }

            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        };

        container.addEventListener('keydown', handleKeyDown);

        return () => {
            container.removeEventListener('keydown', handleKeyDown);
            previouslyFocused?.focus?.();
        };
    }, []);

    return containerRef;
};
