import { useEffect, useRef, useState } from 'react';


// What a screen reader is told when a counter crosses its limit, in either direction, and nothing while the number
// merely changes: reading out every keystroke's count would drown the typing.
export function useOverLimitAnnouncement(isOver: boolean, overMessage: string, withinMessage: string): string {
    const [announcement, setAnnouncement] = useState('');
    const wasOverRef = useRef(isOver);

    useEffect(() => {
        if (wasOverRef.current === isOver) {
            return;
        }

        wasOverRef.current = isOver;
        setAnnouncement(isOver ? overMessage : withinMessage);
    }, [isOver, overMessage, withinMessage]);

    return announcement;
}
