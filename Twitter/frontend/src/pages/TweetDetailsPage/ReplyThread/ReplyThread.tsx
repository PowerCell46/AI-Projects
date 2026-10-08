import { useState } from 'react';
import type { Reply } from '../../../api/replies';
import PostListStatus from '../../../components/shared/PostListStatus/PostListStatus';
import { useAuth } from '../../../contexts/AuthContext';
import { useBottomSentinel } from '../../../hooks/useBottomSentinel';
import { bottomStateOf } from '../../../utils/bottomState';
import ReplyCell from '../ReplyCell/ReplyCell';
import ReplyComposer from '../ReplyComposer/ReplyComposer';
import type { ReplyThreadState } from '../useReplyThread';
import './ReplyThread.css';


const END_TEXT = 'END OF REPLIES';

const EMPTY_TEXT = 'NO REPLIES YET';

interface ReplyThreadProps {
    tweetId: string;
    postAuthorId: string;
    replyCount: number;
    thread: ReplyThreadState;
    now: Date;
}

function ReplyThread({ tweetId, postAuthorId, replyCount, thread, now }: ReplyThreadProps) {
    const { user } = useAuth();
    const [enteringReplyId, setEnteringReplyId] = useState<string | null>(null);
    const sentinelRef = useBottomSentinel(thread.loadMore, `${thread.replies.length}-${thread.status}`);
    const bottomState = bottomStateOf(thread.status, thread.isEnd, thread.replies.length);

    // Only the reply you just sent plays the entrance; the ones loaded with the page are simply there.
    function handleSent(reply: Reply) {
        setEnteringReplyId(reply.id);
        thread.addSentReply(reply);
    }

    return (
        <>
            <ReplyComposer tweetId={tweetId} onSent={handleSent} />
            <h2 className="reply-thread-title">REPLIES · {replyCount}</h2>
            <ol className="reply-thread-list">
                {thread.replies.map((reply, replyIndex) => (
                    <li key={reply.id}>
                        <ReplyCell
                            reply={reply}
                            position={replyIndex + 1}
                            isEntering={reply.id === enteringReplyId}
                            now={now}
                            currentUserId={user?.id ?? null}
                            postAuthorId={postAuthorId}
                            onChanged={thread.replaceReply}
                            onRemoved={thread.removeReply}
                        />
                    </li>
                ))}
            </ol>
            {bottomState && (
                <PostListStatus
                    state={bottomState}
                    endText={END_TEXT}
                    emptyText={EMPTY_TEXT}
                    onRetry={thread.retry}
                />
            )}
            <div ref={sentinelRef} />
            <p className="sr-only" aria-live="polite">{thread.announcement}</p>
        </>
    );
}

export default ReplyThread;
