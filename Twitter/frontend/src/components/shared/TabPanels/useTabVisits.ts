import { useState } from 'react';
import type { TabId } from '../../../utils/tabs';


export interface TabVisits {
    visitedTabs: TabId[];
    hasSwitched: boolean;
}

// Which tabs have been opened since the panels mounted, and whether one tab has given way to another. Both are worked
// out while rendering, from the tab that just changed, so the first frame of a new tab already has its panel. No tab
// is active while a post's details are open: that opens no panel, and coming back from it is no tab switch.
export function useTabVisits(activeTab: TabId | null): TabVisits {
    const [shownTab, setShownTab] = useState(activeTab);
    const [visitedTabs, setVisitedTabs] = useState<TabId[]>(activeTab ? [activeTab] : []);
    const [hasSwitched, setHasSwitched] = useState(false);

    if (activeTab !== shownTab) {
        setShownTab(activeTab);

        if (shownTab && activeTab) {
            setHasSwitched(true);
        }

        if (activeTab && !visitedTabs.includes(activeTab)) {
            setVisitedTabs([...visitedTabs, activeTab]);
        }
    }

    return {
        visitedTabs,
        hasSwitched,
    };
}
