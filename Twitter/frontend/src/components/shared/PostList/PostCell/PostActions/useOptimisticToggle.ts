import { useRef, useState } from 'react';


interface OptimisticToggle {
    isOn: boolean;
    toggle: () => void;
}

// Flips at once, then tells the server. One request is in flight at a time and the last click wins: clicks made
// meanwhile are sent afterwards only if they left the toggle somewhere other than the server's last answer.
// A failed request puts the toggle back to what the server last confirmed, without a message.
export function useOptimisticToggle(
    initialValue: boolean,
    sendChange: (nextValue: boolean) => Promise<void>,
): OptimisticToggle {
    const [isOn, setIsOn] = useState(initialValue);
    const wantedValueRef = useRef(initialValue);
    const confirmedValueRef = useRef(initialValue);
    const isSendingRef = useRef(false);

    async function syncWithServer() {
        if (isSendingRef.current) {
            return;
        }

        isSendingRef.current = true;

        try {
            while (wantedValueRef.current !== confirmedValueRef.current) {
                const valueToSend = wantedValueRef.current;

                await sendChange(valueToSend);

                confirmedValueRef.current = valueToSend;
            }

        } catch {
            wantedValueRef.current = confirmedValueRef.current;
            setIsOn(confirmedValueRef.current);

        } finally {
            isSendingRef.current = false;
        }
    }

    function toggle() {
        wantedValueRef.current = !wantedValueRef.current;
        setIsOn(wantedValueRef.current);

        syncWithServer();
    }

    return {
        isOn,
        toggle,
    };
}
