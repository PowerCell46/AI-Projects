import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useActiveTab } from '../../../hooks/useActiveTab';
import { useOpenTweetId } from '../../../hooks/useOpenTweetId';
import FeedPage from '../../../pages/FeedPage/FeedPage';
import PeoplePage from '../../../pages/PeoplePage/PeoplePage';
import TweetDetailsPage from '../../../pages/TweetDetailsPage/TweetDetailsPage';
import { ROUTES } from '../../../routes';
import { TABS, tabButtonId, tabPanelId } from '../../../utils/tabs';
import type { TabId } from '../../../utils/tabs';
import { useTabScrollMemory } from './useTabScrollMemory';
import { useTabVisits } from './useTabVisits';
import './TabPanels.css';


const PAGE_OF_TAB: Record<TabId, ReactNode> = {
    tweets: <FeedPage />,
    people: <PeoplePage />,
};

// The layout of /feed, /users and /tweets/:tweetId: one panel per tab, plus a third panel for a post's details that
// belongs to no tab. A tab's page mounts the first time its tab opens and stays mounted, so the tab you left keeps its
// list, its place and its new-posts check, also while the details are open (Back lands on the same spot). The tab
// wrappers are always there, because the tabs point at them; the details panel exists only while its address is open.
function TabPanels() {
    const activeTab = useActiveTab();
    const openTweetId = useOpenTweetId();
    const { visitedTabs, hasSwitched } = useTabVisits(activeTab);

    useTabScrollMemory(activeTab);

    // These layouts hold only the tab addresses and the details address, so no tab and no tweet is a bad tweet id.
    if (!activeTab && !openTweetId) {
        return <Navigate to={ROUTES.feed} replace />;
    }

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
            {openTweetId && (
                <section key={openTweetId} aria-label="Post details">
                    <TweetDetailsPage tweetId={openTweetId} />
                </section>
            )}
        </>
    );
}

export default TabPanels;
