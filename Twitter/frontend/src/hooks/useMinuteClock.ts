import { useEffect, useState } from 'react';


const MINUTE_MS = 60_000;

// One shared tick for the whole list, so every relative time moves together instead of each cell keeping a timer.
// A hidden list keeps no timer; its times catch up on the next tick after it returns.
export function useMinuteClock(isEnabled = true): Date {
    const [now, setNow] = useState(() => new Date());

    useEffect(() => {
        if (!isEnabled) {
            return;
        }

        const intervalId = window.setInterval(
            () => setNow(new Date()),
            MINUTE_MS,
        );

        return () => window.clearInterval(intervalId);
    }, [isEnabled]);

    return now;
}
