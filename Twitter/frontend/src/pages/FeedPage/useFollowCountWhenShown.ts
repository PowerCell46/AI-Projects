import { useState } from 'react';


// The follow count as of the last time the tab was shown. A follow happens on the other tab, so while this one is
// hidden the value stays behind; the render that shows the tab again catches up, and that change is what tells the
// empty feed to look again. A follow made while the tab stays shown (from the empty panel's suggestions) does not
// move it: the card that was just followed must not be replaced, and `useBackfillReloadKey` reads the feed later.
export function useFollowCountWhenShown(followChangeCount: number, isShown: boolean): number {
    const [shownCount, setShownCount] = useState(followChangeCount);
    const [wasShown, setWasShown] = useState(isShown);

    if (isShown !== wasShown) {
        setWasShown(isShown);

        if (isShown) {
            setShownCount(followChangeCount);
        }
    }

    return shownCount;
}
