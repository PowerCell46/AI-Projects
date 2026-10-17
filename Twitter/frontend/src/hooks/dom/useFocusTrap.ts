import type { KeyboardEvent, RefObject } from 'react';


const FOCUSABLE_SELECTOR = 'button, textarea, input, a[href], [tabindex]:not([tabindex="-1"])';

// Keeps Tab and Shift+Tab inside the dialog: from the last control it wraps to the first, and the other way round.
export function useFocusTrap(dialogRef: RefObject<HTMLElement | null>): (event: KeyboardEvent) => void {
    return (event) => {
        if (event.key !== 'Tab' || !dialogRef.current) {
            return;
        }

        const controls = Array
            .from(dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
            .filter((control) => !control.matches(':disabled'));
        const first = controls.at(0);
        const last = controls.at(-1);
        const isOnFirst = document.activeElement === first;
        const isOnLast = document.activeElement === last;

        if (event.shiftKey && isOnFirst) {
            event.preventDefault();
            last?.focus();

        } else if (!event.shiftKey && isOnLast) {
            event.preventDefault();
            first?.focus();
        }
    };
}
