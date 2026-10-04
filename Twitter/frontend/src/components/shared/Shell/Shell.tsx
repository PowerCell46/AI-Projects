import { useRef, useState } from 'react';
import { Outlet } from 'react-router-dom';
import type { PublishedTweet } from '../../../api/tweets';
import type { TweetItem } from '../../../api/tweetPage';
import { useAuth } from '../../../contexts/AuthContext';
import { toOwnPost } from '../../../utils/ownPost';
import ComposeModal from './ComposeModal/ComposeModal';
import Header from './Header/Header';
import { useProfilePicture } from './useProfilePicture';
import type { ShellContext } from './useShellContext';
import './Shell.css';


function Shell() {
    const { user, isSigningOut } = useAuth();
    const pictureUrl = useProfilePicture(user?.username);
    const [isComposing, setIsComposing] = useState(false);
    const [ownPosts, setOwnPosts] = useState<TweetItem[]>([]);
    const postButtonRef = useRef<HTMLButtonElement>(null);
    const outletContext: ShellContext = { ownPosts };

    function closeCompose() {
        setIsComposing(false);
        postButtonRef.current?.focus();
    }

    // The new post goes to the top of the feed at once, without waiting for the fan-out to deliver it.
    function handlePublished(tweet: PublishedTweet) {
        if (user) {
            setOwnPosts((currentPosts) => [toOwnPost(tweet, user, pictureUrl), ...currentPosts]);
        }

        closeCompose();
    }

    return (
        <>
            <div className="shell" data-signing-out={isSigningOut}>
                <Header
                    pictureUrl={pictureUrl}
                    postButtonRef={postButtonRef}
                    onPostClick={() => setIsComposing(true)}
                />
                <main className="shell-main">
                    <Outlet context={outletContext} />
                </main>
            </div>
            {isComposing && <ComposeModal onClose={closeCompose} onPublished={handlePublished} />}
        </>
    );
}

export default Shell;
