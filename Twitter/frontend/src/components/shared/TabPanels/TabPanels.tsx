import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useActiveTab } from '../../../hooks/useActiveTab';
import { useOpenTweetId } from '../../../hooks/useOpenTweetId';
import { useOpenUsername } from '../../../hooks/useOpenUsername';
import FeedPage from '../../../pages/FeedPage/FeedPage';
import PeoplePage from '../../../pages/PeoplePage/PeoplePage';
import ProfilePage from '../../../pages/ProfilePage/ProfilePage';
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

// The layout of /feed, /users, /users/:username and /tweets/:tweetId: one panel per tab, plus a third panel for a
// post's details or a profile that belongs to no tab. A tab's page mounts the first time its tab opens and stays
// mounted, so the tab you left keeps its list, its place and its new-posts check, also while the details are open
// (Back lands on the same spot). The tab wrappers are always there, because the tabs point at them; the details and
// profile panels exist only while their address is open, one at a time.
function TabPanels() {
    const activeTab = useActiveTab();
    const openTweetId = useOpenTweetId();
    const openUsername = useOpenUsername();
    const { visitedTabs, hasSwitched } = useTabVisits(activeTab);

    useTabScrollMemory(activeTab);

    // These layouts hold only the tab, details and profile addresses, so none of them is a bad tweet id.
    if (!activeTab && !openTweetId && !openUsername) {
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
            {openUsername && (
                <section key={openUsername} aria-label="Profile">
                    <ProfilePage username={openUsername} />
                </section>
            )}
        </>
    );
}

export default TabPanels;
