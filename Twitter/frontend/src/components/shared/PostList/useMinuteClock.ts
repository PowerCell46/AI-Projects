import { useEffect, useState } from 'react';


const MINUTE_MS = 60_000;

// One shared tick for the whole list, so every relative time moves together instead of each cell keeping a timer.
export function useMinuteClock(): Date {
    const [now, setNow] = useState(() => new Date());

    useEffect(() => {
        const intervalId = window.setInterval(
            () => setNow(new Date()),
            MINUTE_MS,
        );

        return () => window.clearInterval(intervalId);
    }, []);

    return now;
}
