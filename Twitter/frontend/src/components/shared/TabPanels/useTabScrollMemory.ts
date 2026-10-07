import { useEffect, useLayoutEffect, useRef } from 'react';
import type { TabId } from '../../../utils/tabs';


// Keeps the page's scroll position per tab, in memory. The position is tracked as the page scrolls, not read at the
// switch: by then the tab being left is hidden and the browser has already pulled the page up to the shorter content.
// Leaving a tab for a post's details (no active tab) keeps its position for the way back; the details page itself
// scrolls to its own top.
export function useTabScrollMemory(activeTab: TabId | null): void {
    const latestScrollYRef = useRef(0);
    const shownTabRef = useRef<TabId | null>(activeTab);
    const savedScrollYsRef = useRef<Partial<Record<TabId, number>>>({});

    useEffect(() => {
        function handleScroll() {
            latestScrollYRef.current = window.scrollY;
        }

        handleScroll();
        window.addEventListener('scroll', handleScroll, { passive: true });
        // A press that switches the tab can land before the browser has delivered the latest scroll event, which
        // would save an older position. Reading in the capture phase runs ahead of the tab's own handler, while
        // the leaving panel is still shown.
        window.addEventListener('click', handleScroll, { capture: true });
        window.addEventListener('keydown', handleScroll, { capture: true });

        return () => {
            window.removeEventListener('scroll', handleScroll);
            window.removeEventListener('click', handleScroll, { capture: true });
            window.removeEventListener('keydown', handleScroll, { capture: true });
        };
    }, []);

    useLayoutEffect(() => {
        if (shownTabRef.current === activeTab) {
            return;
        }

        if (shownTabRef.current) {
            savedScrollYsRef.current[shownTabRef.current] = latestScrollYRef.current;
        }

        shownTabRef.current = activeTab;

        if (!activeTab) {
            latestScrollYRef.current = 0;
            return;
        }

        const restoredScrollY = savedScrollYsRef.current[activeTab] ?? 0;

        latestScrollYRef.current = restoredScrollY;
        window.scrollTo({ top: restoredScrollY });
    }, [activeTab]);
}
