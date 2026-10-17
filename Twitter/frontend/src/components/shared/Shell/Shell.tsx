import { useRef, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import type { PublishedTweet } from '../../../api/tweets/tweets';
import type { TweetItem } from '../../../api/tweets/tweetPage';
import { useAuth } from '../../../contexts/AuthContext';
import { FollowUpdatesProvider } from '../../../contexts/FollowUpdatesContext';
import { PostUpdatesProvider } from '../../../contexts/PostUpdatesContext';
import { useActiveTab } from '../../../hooks/routing/useActiveTab';
import { toOwnPost } from './ownPost';
import { TABS } from '../../../utils/navigation/tabs';
import type { TabId } from '../../../utils/navigation/tabs';
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
    const { pictureUrl, setPictureUrl } = useProfilePicture(user?.username);
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
        onProfilePictureChanged: setPictureUrl,
    };

    return (
        <PostUpdatesProvider>
            <FollowUpdatesProvider>
                <div className="shell" data-signing-out={isSigningOut}>
                    <a className="shell-skip-link" href="#main-content">Skip to main content</a>
                    <div className="shell-top">
                        <Header
                            pictureUrl={pictureUrl}
                            postButtonRef={postButtonRef}
                            onPostClick={() => setIsComposing(true)}
                        />
                        {activeTab && <TabRow activeTab={activeTab} onSelect={handleTabSelect} />}
                    </div>
                    <main id="main-content" className="shell-main" tabIndex={-1}>
                        <Outlet context={outletContext} />
                    </main>
                </div>
                {isComposing && <ComposeModal onClose={closeCompose} onPublished={handlePublished} />}
            </FollowUpdatesProvider>
        </PostUpdatesProvider>
    );
}

export default Shell;
