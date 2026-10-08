import { useEffect } from 'react';


// The page behind a full-screen dialog must not scroll; index.css turns the attribute into `overflow: hidden`.
export function useScrollLock(): void {
    useEffect(() => {
        document.documentElement.dataset.scrollLocked = 'true';

        return () => {
            delete document.documentElement.dataset.scrollLocked;
        };
    }, []);
}
