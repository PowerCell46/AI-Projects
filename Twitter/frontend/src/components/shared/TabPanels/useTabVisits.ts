import { useState } from 'react';
import type { TabId } from '../../../utils/tabs';


export interface TabVisits {
    visitedTabs: TabId[];
    hasSwitched: boolean;
}

// Which tabs have been opened since the panels mounted, and whether the tab has changed at all. Both are worked out
// while rendering, from the tab that just changed, so the first frame of a new tab already has its panel.
export function useTabVisits(activeTab: TabId): TabVisits {
    const [shownTab, setShownTab] = useState(activeTab);
    const [visitedTabs, setVisitedTabs] = useState<TabId[]>([activeTab]);
    const [hasSwitched, setHasSwitched] = useState(false);

    if (activeTab !== shownTab) {
        setShownTab(activeTab);
        setHasSwitched(true);

        if (!visitedTabs.includes(activeTab)) {
            setVisitedTabs([...visitedTabs, activeTab]);
        }
    }

    return {
        visitedTabs,
        hasSwitched,
    };
}
