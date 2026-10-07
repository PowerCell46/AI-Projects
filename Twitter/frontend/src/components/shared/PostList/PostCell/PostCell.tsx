import type { MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { TweetItem } from '../../../../api/tweetPage';
import { tweetPath } from '../../../../routes';
import { stripBidiControls } from '../../../../utils/bidi';
import { formatPostTime } from '../../../../utils/relativeTime';
import Avatar from '../../Avatar/Avatar';
import PostActions from './PostActions/PostActions';
import PostImages from './PostImages/PostImages';
import './PostCell.css';


// A click on any of these keeps its own meaning (like, save, the reply link, an image) instead of opening the post.
const OWN_CLICK_SELECTOR = 'button, a, img';

function isOwnClick(target: EventTarget): boolean {
    return target instanceof Element && target.closest(OWN_CLICK_SELECTOR) !== null;
}

function endsTextSelection(): boolean {
    return (window.getSelection()?.toString() ?? '') !== '';
}

interface PostCellProps {
    post: TweetItem;
    now: Date;
    isClickable?: boolean;
}

function PostCell({ post, now, isClickable = true }: PostCellProps) {
    const navigate = useNavigate();
    const { author } = post;
    const body = stripBidiControls(post.content);

    function handleClick(event: MouseEvent<HTMLElement>) {
        if (!isClickable || isOwnClick(event.target) || endsTextSelection()) {
            return;
        }

        navigate(tweetPath(post.id));
    }

    return (
        <article
            className="post-cell"
            aria-label={`Post by ${author.username}`}
            data-clickable={isClickable}
            onClick={handleClick}
        >
            <header className="post-cell-author">
                <Avatar pictureUrl={author.profilePictureUrl} />
                <span className="post-cell-name">{author.username}</span>
                <time className="post-cell-time" dateTime={post.createdAt}>
                    {formatPostTime(post.createdAt, now)}
                </time>
            </header>
            {body && <p className="post-cell-body" dir="auto">{body}</p>}
            <PostImages tweetId={post.id} images={post.images} />
            <PostActions
                tweetId={post.id}
                isSavedInitially={post.savedByMe}
                isLikedInitially={post.likedByMe}
                likeCount={post.likes}
                replyCount={post.replyCount}
                isReplyLinked={isClickable}
            />
        </article>
    );
}

export default PostCell;
