import { useEffect, useRef } from 'react';
import type { RefObject } from 'react';


// Focus is lost when nothing holds it, or what held it is gone or hidden (the link that was clicked to get here).
function isFocusLost(): boolean {
    const focusedElement = document.activeElement;

    return !focusedElement
        || focusedElement === document.body
        || !focusedElement.isConnected
        || focusedElement.closest('[hidden]') !== null;
}

// For the heading of a page that was just opened: when focus was lost on the way, it would otherwise fall back to the
// start of the document. Focus that sits somewhere still on screen (the avatar after a menu choice) is left alone.
export function useFocusOnMount<T extends HTMLElement>(): RefObject<T | null> {
    const elementRef = useRef<T>(null);

    useEffect(() => {
        if (isFocusLost()) {
            elementRef.current?.focus({ preventScroll: true });
        }
    }, []);

    return elementRef;
}
