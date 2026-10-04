import { useEffect, useRef } from 'react';


// Reads the first page again when the key changes (not when the list is first shown) and there is nothing on screen:
// a list with posts keeps its place, and one still loading or failed already has a read of its own to show.
export function useReloadEmptyList(isEmpty: boolean, reload: () => void, reloadKey: number | undefined) {
    const lastKeyRef = useRef(reloadKey);
    const reloadRef = useRef(reload);

    useEffect(() => {
        reloadRef.current = reload;
    });

    useEffect(() => {
        if (lastKeyRef.current === reloadKey) {
            return;
        }

        lastKeyRef.current = reloadKey;

        if (isEmpty) {
            reloadRef.current();
        }
    }, [reloadKey, isEmpty]);
}
