import { useEffect, useRef } from 'react';
import type { RefObject } from 'react';


// The 500px must match the root margin in test/postListHelpers.ts, which finds this observer by it.
const NEAR_BOTTOM_ROOT_MARGIN = '0px 0px 500px 0px';

// Calls onReach whenever the sentinel is within 500px of the viewport. The observer is rebuilt when rearmKey changes,
// because a browser only reports a change: after a page is added, a sentinel that is still near the bottom would
// otherwise stay quiet.
export function useBottomSentinel(onReach: () => void, rearmKey: unknown): RefObject<HTMLDivElement | null> {
    const sentinelRef = useRef<HTMLDivElement>(null);
    const onReachRef = useRef(onReach);

    useEffect(() => {
        onReachRef.current = onReach;
    });

    useEffect(() => {
        const sentinel = sentinelRef.current;

        if (!sentinel) {
            return;
        }

        const observer = new IntersectionObserver(
            (entries) => {
                if (entries.some((entry) => entry.isIntersecting)) {
                    onReachRef.current();
                }
            },
            { rootMargin: NEAR_BOTTOM_ROOT_MARGIN },
        );

        observer.observe(sentinel);

        return () => observer.disconnect();
    }, [rearmKey]);

    return sentinelRef;
}
