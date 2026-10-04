import { useState } from 'react';


// The follow count as of the last time the tab was shown. A follow happens on the other tab, so while this one is
// hidden the value stays behind; the render that shows the tab again catches up, and that change is what tells the
// empty feed to look again.
export function useFollowCountWhenShown(followChangeCount: number, isShown: boolean): number {
    const [shownCount, setShownCount] = useState(followChangeCount);

    if (isShown && shownCount !== followChangeCount) {
        setShownCount(followChangeCount);
    }

    return shownCount;
}
