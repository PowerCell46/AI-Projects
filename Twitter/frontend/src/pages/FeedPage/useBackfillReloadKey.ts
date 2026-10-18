import { useEffect, useRef, useState } from 'react';


// The gateway outbox polls every 3s, so the followed person's posts land in the feed a little after the follow. The
// feed is read at both times, in case the first read comes just before they land.
const BACKFILL_DELAYS_MS = [4000, 8000];

// A key that changes when the back-fill has had time to land after a follow made while this tab is shown, so an
// empty feed reads again without the reader leaving and coming back. Follows made on the other tab are caught up by
// `useFollowCountWhenShown` instead.
export function useBackfillReloadKey(followChangeCount: number, isShown: boolean): number {
    const [reloadKey, setReloadKey] = useState(0);
    const lastCountRef = useRef(followChangeCount);

    useEffect(() => {
        if (!isShown || lastCountRef.current === followChangeCount) {
            return;
        }

        lastCountRef.current = followChangeCount;

        const timerIds = BACKFILL_DELAYS_MS.map((delayMs) => window.setTimeout(
            () => setReloadKey((currentKey) => currentKey + 1),
            delayMs,
        ));

        return () => {
            timerIds.forEach((timerId) => window.clearTimeout(timerId));
        };
    }, [followChangeCount, isShown]);

    return reloadKey;
}
