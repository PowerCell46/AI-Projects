import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api/http';
import { deleteReply } from '../../../api/replies';
import type { Reply } from '../../../api/replies';
import Avatar from '../../../components/shared/Avatar/Avatar';
import { stripBidiControls } from '../../../utils/bidi';
import { formatPostTime } from '../../../utils/relativeTime';
import ReplyEditor from '../ReplyEditor/ReplyEditor';
import './ReplyCell.css';


type ReplyMode = 'viewing' | 'editing' | 'confirming-delete';

const NOT_FOUND_STATUS = 404;

const DELETE_FAILED_MESSAGE = "Couldn't delete. Try again.";

const INDEX_DIGITS = 2;

// Two digits, and past 99 the number simply grows.
function formatReplyIndex(position: number): string {
    return String(position).padStart(INDEX_DIGITS, '0');
}

interface ReplyCellProps {
    reply: Reply;
    position: number;
    isEntering: boolean;
    now: Date;
    currentUserId: string | null;
    postAuthorId: string;
    onChanged: (reply: Reply) => void;
    onRemoved: (replyId: string) => void;
}

function ReplyCell({
    reply,
    position,
    isEntering,
    now,
    currentUserId,
    postAuthorId,
    onChanged,
    onRemoved,
}: ReplyCellProps) {
    const [mode, setMode] = useState<ReplyMode>('viewing');
    const [isDeleting, setIsDeleting] = useState(false);
    const [deleteErrorMessage, setDeleteErrorMessage] = useState('');
    const editButtonRef = useRef<HTMLButtonElement>(null);
    const deleteButtonRef = useRef<HTMLButtonElement>(null);
    const keepButtonRef = useRef<HTMLButtonElement>(null);
    const previousModeRef = useRef<ReplyMode>('viewing');
    const { author } = reply;

    // Focus follows the change of mode: into the question's safe answer, and back to the button that opened it.
    useEffect(() => {
        const previousMode = previousModeRef.current;

        previousModeRef.current = mode;

        if (mode === 'confirming-delete') {
            keepButtonRef.current?.focus();

        } else if (previousMode === 'editing') {
            editButtonRef.current?.focus();

        } else if (previousMode === 'confirming-delete') {
            deleteButtonRef.current?.focus();
        }
    }, [mode]);

    const canEdit = currentUserId !== null && author.id === currentUserId;
    const canDelete = canEdit || (currentUserId !== null && postAuthorId === currentUserId);

    function handleSaved(savedReply: Reply) {
        onChanged(savedReply);
        setMode('viewing');
    }

    async function handleDeleteConfirmed() {
        if (isDeleting) {
            return;
        }

        setIsDeleting(true);

        try {
            await deleteReply(reply.tweetId, reply.id);
            onRemoved(reply.id);

        } catch (failure) {
            // A reply that is already gone is what the reader wanted; anything else leaves it in place.
            if (failure instanceof ApiError && failure.status === NOT_FOUND_STATUS) {
                onRemoved(reply.id);

            } else {
                setDeleteErrorMessage(DELETE_FAILED_MESSAGE);
                setMode('viewing');
            }

        } finally {
            setIsDeleting(false);
        }
    }

    function handleDeleteAsked() {
        setDeleteErrorMessage('');
        setMode('confirming-delete');
    }

    return (
        <article className="reply-cell" aria-label={`Reply by ${author.username}`} data-entering={isEntering}>
            <span className="reply-cell-index" aria-hidden="true">{formatReplyIndex(position)}</span>
            <div className="reply-cell-content">
                <header className="reply-cell-author">
                    <Avatar pictureUrl={author.profilePictureUrl} size="tiny" />
                    <span className="reply-cell-name">{author.username}</span>
                    <time className="reply-cell-time" dateTime={reply.createdAt}>
                        {formatPostTime(reply.createdAt, now)}
                    </time>
                    {reply.edited && (
                        <span className="reply-cell-edited">
                            <span aria-hidden="true">EDITED</span>
                            <span className="sr-only">edited</span>
                        </span>
                    )}
                </header>
                {mode === 'editing' ? (
                    <ReplyEditor reply={reply} onSaved={handleSaved} onCancel={() => setMode('viewing')} />
                ) : (
                    <p className="reply-cell-body" dir="auto">{stripBidiControls(reply.content)}</p>
                )}
                {mode !== 'editing' && (canEdit || canDelete) && (
                    <div className="reply-cell-actions">
                        {canEdit && mode === 'viewing' && (
                            <button
                                type="button"
                                className="reply-cell-action"
                                data-action="edit"
                                ref={editButtonRef}
                                onClick={() => setMode('editing')}
                            >
                                EDIT
                            </button>
                        )}
                        {canDelete && mode === 'viewing' && (
                            <button
                                type="button"
                                className="reply-cell-action"
                                data-action="delete"
                                ref={deleteButtonRef}
                                onClick={handleDeleteAsked}
                            >
                                DELETE
                            </button>
                        )}
                        {mode === 'confirming-delete' && (
                            <div className="reply-cell-confirm" role="group" aria-label="Delete this reply?">
                                <span className="reply-cell-question">DELETE?</span>
                                <button
                                    type="button"
                                    className="reply-cell-action"
                                    data-action="delete"
                                    aria-disabled={isDeleting}
                                    onClick={handleDeleteConfirmed}
                                >
                                    YES
                                </button>
                                <button
                                    type="button"
                                    className="reply-cell-action"
                                    data-action="edit"
                                    ref={keepButtonRef}
                                    onClick={() => setMode('viewing')}
                                >
                                    NO
                                </button>
                            </div>
                        )}
                    </div>
                )}
                <p className="reply-cell-error" role="alert">{deleteErrorMessage}</p>
            </div>
        </article>
    );
}

export default ReplyCell;
