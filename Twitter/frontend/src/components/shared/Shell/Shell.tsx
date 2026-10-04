import { useRef, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import type { PublishedTweet } from '../../../api/tweets';
import type { TweetItem } from '../../../api/tweetPage';
import { useAuth } from '../../../contexts/AuthContext';
import { useActiveTab } from '../../../hooks/useActiveTab';
import { toOwnPost } from '../../../utils/ownPost';
import { TABS } from '../../../utils/tabs';
import type { TabId } from '../../../utils/tabs';
import ComposeModal from './ComposeModal/ComposeModal';
import Header from './Header/Header';
import TabRow from './TabRow/TabRow';
import { useProfilePicture } from './useProfilePicture';
import type { ShellContext } from './useShellContext';
import './Shell.css';


function Shell() {
    const { user, isSigningOut } = useAuth();
    const navigate = useNavigate();
    const activeTab = useActiveTab();
    const pictureUrl = useProfilePicture(user?.username);
    const [isComposing, setIsComposing] = useState(false);
    const [ownPosts, setOwnPosts] = useState<TweetItem[]>([]);
    const [followChangeCount, setFollowChangeCount] = useState(0);
    const postButtonRef = useRef<HTMLButtonElement>(null);

    function closeCompose() {
        setIsComposing(false);
        postButtonRef.current?.focus();
    }

    // A new history entry, so Back returns to the tab before.
    function handleTabSelect(tab: TabId) {
        const selectedTab = TABS.find((candidate) => candidate.id === tab);

        if (selectedTab) {
            navigate(selectedTab.path);
        }
    }

    // The new post goes to the top of the feed at once, without waiting for the fan-out to deliver it.
    function handlePublished(tweet: PublishedTweet) {
        if (user) {
            setOwnPosts((currentPosts) => [toOwnPost(tweet, user, pictureUrl), ...currentPosts]);
        }

        closeCompose();
    }


    const outletContext: ShellContext = {
        ownPosts,
        followChangeCount,
        onFollowChanged: () => setFollowChangeCount((currentCount) => currentCount + 1),
    };

    return (
        <>
            <div className="shell" data-signing-out={isSigningOut}>
                <div className="shell-top">
                    <Header
                        pictureUrl={pictureUrl}
                        postButtonRef={postButtonRef}
                        onPostClick={() => setIsComposing(true)}
                    />
                    {activeTab && <TabRow activeTab={activeTab} onSelect={handleTabSelect} />}
                </div>
                <main className="shell-main">
                    <Outlet context={outletContext} />
                </main>
            </div>
            {isComposing && <ComposeModal onClose={closeCompose} onPublished={handlePublished} />}
        </>
    );
}

export default Shell;
