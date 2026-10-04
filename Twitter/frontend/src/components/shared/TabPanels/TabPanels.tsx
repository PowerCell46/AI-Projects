import type { ReactNode } from 'react';
import { useActiveTab } from '../../../hooks/useActiveTab';
import FeedPage from '../../../pages/FeedPage/FeedPage';
import PeoplePage from '../../../pages/PeoplePage/PeoplePage';
import { DEFAULT_TAB, TABS, tabButtonId, tabPanelId } from '../../../utils/tabs';
import type { TabId } from '../../../utils/tabs';
import { useTabScrollMemory } from './useTabScrollMemory';
import { useTabVisits } from './useTabVisits';
import './TabPanels.css';


const PAGE_OF_TAB: Record<TabId, ReactNode> = {
    tweets: <FeedPage />,
    people: <PeoplePage />,
};

// The layout of /feed and /users: one panel per tab. A panel's page mounts the first time its tab opens and stays
// mounted, so the tab you left keeps its list, its place and its new-posts check. The wrappers are always there,
// because the tabs point at them.
function TabPanels() {
    const activeTab = useActiveTab() ?? DEFAULT_TAB;
    const { visitedTabs, hasSwitched } = useTabVisits(activeTab);

    useTabScrollMemory(activeTab);

    return (
        <>
            {TABS.map((tab) => (
                <section
                    key={tab.id}
                    id={tabPanelId(tab.id)}
                    className="tab-panel"
                    role="tabpanel"
                    aria-labelledby={tabButtonId(tab.id)}
                    hidden={tab.id !== activeTab}
                    data-entering={hasSwitched && tab.id === activeTab}
                >
                    {visitedTabs.includes(tab.id) && PAGE_OF_TAB[tab.id]}
                </section>
            ))}
        </>
    );
}

export default TabPanels;
