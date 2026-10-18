import { useEffect, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import PostCell from '../../components/shared/PostList/PostCell/PostCell';
import { useViewTracking } from '../../components/shared/PostList/useViewTracking';
import PostListStatus from '../../components/shared/PostListStatus/PostListStatus';
import type { BottomState } from '../../components/shared/PostListStatus/PostListStatus';
import type { PostUpdate } from '../../contexts/PostUpdatesContext';
import { usePostUpdates } from '../../contexts/PostUpdatesContext';
import { useFocusOnMount } from '../../hooks/dom/useFocusOnMount';
import { useMinuteClock } from '../../hooks/dom/useMinuteClock';
import { ROUTES } from '../../routes';
import { prefersReducedMotion } from '../../utils/browser/motion';
import ReplyThread from './ReplyThread/ReplyThread';
import { useReplyThread } from './useReplyThread';
import { useTweetDetails } from './useTweetDetails';
import type { DetailsProblem } from './useTweetDetails';
import './TweetDetailsPage.css';


const NOT_FOUND_TEXT = 'POST NOT FOUND';

const BACK_LABEL = 'BACK';

// The router names the first entry of a session `default`; any other key has an entry behind it to go back to.
const FIRST_ENTRY_KEY = 'default';

// How long the opening animation runs (the rule's 1000ms draw in TweetDetailsPage.css and the replies' stagger in
// ReplyThread.css end within it); after that, replies loaded later just appear.
const OPEN_MS = 1200;

// How long the back link waits for the leave animation; it must cover the longest leave rule in TweetDetailsPage.css
// and ReplyThread.css (the rule's 100ms delay plus its 350ms retract).
const LEAVE_MS = 450;

const STATUS_OF_PROBLEM: Record<DetailsProblem, BottomState> = {
    'not-found': 'empty',
    failed: 'failed',
};

interface TweetDetailsPageProps {
    tweetId: string;
}

function TweetDetailsPage({ tweetId }: TweetDetailsPageProps) {
    const navigate = useNavigate();
    const location = useLocation();
    const { reportUpdate } = usePostUpdates();
    const headingRef = useFocusOnMount<HTMLHeadingElement>();
    const details = useTweetDetails(tweetId);
    const thread = useReplyThread(tweetId);
    const now = useMinuteClock();
    const trackView = useViewTracking();
    const reportedCountChangeRef = useRef(0);
    const leaveTimerIdRef = useRef<number | null>(null);
    const [isOpening, setIsOpening] = useState(true);
    const [isLeaving, setIsLeaving] = useState(false);

    useEffect(() => {
        const openTimerId = window.setTimeout(
            () => setIsOpening(false),
            OPEN_MS,
        );

        return () => {
            window.clearTimeout(openTimerId);
        };
    }, []);

    useEffect(() => {
        return () => {
            if (leaveTimerIdRef.current !== null) {
                window.clearTimeout(leaveTimerIdRef.current);
            }
        };
    }, []);

    useEffect(() => {
        window.scrollTo({ top: 0 });
    }, [tweetId]);

    // The feed and People stay mounted under this page, so what you changed here is handed to them for the way back.
    useEffect(() => {
        if (thread.countChange === reportedCountChangeRef.current || !details.post) {
            return;
        }

        reportedCountChangeRef.current = thread.countChange;
        reportUpdate(
            tweetId,
            { replyCount: details.post.replyCount + thread.countChange },
        );
    }, [thread.countChange, details.post, tweetId, reportUpdate]);

    // The page plays its leave animation first, then goes back; with nothing behind it, the feed is the way out.
    function handleBackClick(event: MouseEvent<HTMLAnchorElement>) {
        event.preventDefault();

        if (isLeaving) {
            return;
        }

        setIsLeaving(true);
        leaveTimerIdRef.current = window.setTimeout(
            leaveDetails,
            prefersReducedMotion() ? 0 : LEAVE_MS,
        );
    }

    function leaveDetails() {
        if (hasEntryBehind) {
            navigate(-1);

        } else {
            navigate(ROUTES.feed);
        }
    }

    function handlePostChange(update: PostUpdate) {
        reportUpdate(tweetId, update);
    }

    const hasEntryBehind = location.key !== FIRST_ENTRY_KEY;
    // Your sends and deletes move the count at once; the server's count is read when the page opens.
    const post = details.post && {
        ...details.post,
        replyCount: details.post.replyCount + thread.countChange,
    };
    const bottomState = details.problem ? STATUS_OF_PROBLEM[details.problem] : 'loading';

    return (
        <div
            className="tweet-details-page"
            data-opening={isOpening}
            data-leaving={isLeaving}
        >
            <h1 className="sr-only" tabIndex={-1} ref={headingRef}>Post</h1>
            <Link className="tweet-details-page-back" to={ROUTES.feed} onClick={handleBackClick}>
                <span className="tweet-details-page-back-arrow" aria-hidden="true" />
                {BACK_LABEL}
            </Link>
            <span className="tweet-details-page-rule" aria-hidden="true" />
            {post ? (
                <>
                    <div className="tweet-details-page-post" data-tweet-id={post.id} ref={trackView}>
                        <PostCell
                            post={post}
                            now={now}
                            isClickable={false}
                            onChange={handlePostChange}
                        />
                    </div>
                    <div className="tweet-details-page-thread">
                        <ReplyThread
                            tweetId={tweetId}
                            postAuthorId={post.author.id}
                            replyCount={post.replyCount}
                            thread={thread}
                            now={now}
                        />
                    </div>
                </>
            ) : (
                <PostListStatus
                    state={bottomState}
                    endText={NOT_FOUND_TEXT}
                    emptyText={NOT_FOUND_TEXT}
                    onRetry={details.retry}
                />
            )}
        </div>
    );
}

export default TweetDetailsPage;
