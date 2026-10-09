import { Link } from 'react-router-dom';
import { likeTweet, unlikeTweet } from '../../../../../api/likes';
import { saveTweet, unsaveTweet } from '../../../../../api/savedTweets';
import type { PostUpdate } from '../../../../../contexts/PostUpdatesContext';
import { useOptimisticToggle } from '../../../../../hooks/useOptimisticToggle';
import { tweetPath } from '../../../../../routes';
import { formatCount } from '../../../../../utils/count';
import FillIcon from './FillIcon/FillIcon';
import './PostActions.css';


interface PostActionsProps {
    tweetId: string;
    isSavedInitially: boolean;
    isLikedInitially: boolean;
    likeCount: number;
    replyCount: number;
    isReplyLinked: boolean;
    onChange?: (update: PostUpdate) => void;
}

function PostActions({
    tweetId,
    isSavedInitially,
    isLikedInitially,
    likeCount,
    replyCount,
    isReplyLinked,
    onChange,
}: PostActionsProps) {
    const like = useOptimisticToggle(
        isLikedInitially,
        (shouldLike) => (shouldLike ? likeTweet(tweetId) : unlikeTweet(tweetId)),
    );
    const save = useOptimisticToggle(
        isSavedInitially,
        (shouldSave) => (shouldSave ? saveTweet(tweetId) : unsaveTweet(tweetId)),
    );

    // The server's count already holds your like when the post loaded liked; a read taken mid-race can say 0 for a
    // post that is liked by you, so the shown count never goes below 0.
    const shownLikeCount = Math.max(0, likeCount - Number(isLikedInitially) + Number(like.isOn));

    function handleLikeClick() {
        const isLikedNext = !like.isOn;

        like.toggle();
        onChange?.({
            likedByMe: isLikedNext,
            likes: Math.max(0, likeCount - Number(isLikedInitially) + Number(isLikedNext)),
        });
    }

    function handleSaveClick() {
        onChange?.({ savedByMe: !save.isOn });
        save.toggle();
    }

    const replyContent = (
        <>
            <span className="post-actions-icon">
                <FillIcon shape="bubble" isActive={false} />
            </span>
            <span className="sr-only">Replies</span>
            <span className="post-actions-count">{formatCount(replyCount)}</span>
        </>
    );

    return (
        <footer className="post-actions">
            {isReplyLinked ? (
                <Link
                    to={tweetPath(tweetId)}
                    className="post-actions-button"
                    data-action="reply"
                >
                    {replyContent}
                </Link>
            ) : (
                <div className="post-actions-button" data-action="reply" data-static="true">
                    {replyContent}
                </div>
            )}
            <button
                type="button"
                className="post-actions-button"
                data-action="like"
                data-active={like.isOn}
                aria-pressed={like.isOn}
                onClick={handleLikeClick}
            >
                <span className="post-actions-icon">
                    <FillIcon shape="heart" isActive={like.isOn} />
                </span>
                <span className="sr-only">Like</span>
                <span className="post-actions-count">{formatCount(shownLikeCount)}</span>
            </button>
            <button
                type="button"
                className="post-actions-button"
                data-action="save"
                data-active={save.isOn}
                aria-pressed={save.isOn}
                onClick={handleSaveClick}
            >
                <span className="post-actions-icon">
                    <FillIcon shape="bookmark" isActive={save.isOn} />
                </span>
                <span className="sr-only">Save</span>
            </button>
        </footer>
    );
}

export default PostActions;
