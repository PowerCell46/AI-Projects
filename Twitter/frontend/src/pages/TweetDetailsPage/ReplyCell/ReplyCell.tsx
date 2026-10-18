import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api/core/http';
import { STATUS_NOT_FOUND } from '../../../api/core/httpStatus';
import { deleteReply } from '../../../api/tweets/replies';
import type { Reply } from '../../../api/tweets/replies';
import Avatar from '../../../components/shared/Avatar/Avatar';
import { useCollapse } from '../../../hooks/dom/useCollapse';
import { useHeightGlide } from '../../../hooks/dom/useHeightGlide';
import { stripBidiControls } from '../../../utils/text/bidi';
import { formatPostTime } from '../../../utils/text/relativeTime';
import ReplyEditor from '../ReplyEditor/ReplyEditor';
import './ReplyCell.css';


const DELETING_LABEL = 'DELETING…';

const DELETE_FAILED_MESSAGE = "Couldn't delete. Try again.";

const INDEX_DIGITS = 2;

type ReplyMode = 'viewing' | 'editing' | 'confirming-delete';

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
    const [isSaved, setIsSaved] = useState(false);
    const [deleteErrorMessage, setDeleteErrorMessage] = useState('');
    const editButtonRef = useRef<HTMLButtonElement>(null);
    const deleteButtonRef = useRef<HTMLButtonElement>(null);
    const keepButtonRef = useRef<HTMLButtonElement>(null);
    const previousModeRef = useRef<ReplyMode>('viewing');
    const { ref: viewRef, captureHeight } = useHeightGlide<HTMLDivElement>(mode);
    const { ref: rowRef, collapse: collapseRow } = useCollapse<HTMLElement>();

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

    // The editor gives way to the new text as one glide, and the index flashes to say it took.
    function handleSaved(savedReply: Reply) {
        captureHeight();
        onChanged(savedReply);
        setIsSaved(true);
        setMode('viewing');
    }

    async function handleDeleteConfirmed() {
        if (isDeleting) {
            return;
        }

        setIsDeleting(true);

        try {
            await deleteReply(reply.tweetId, reply.id);
            await collapseRow();
            onRemoved(reply.id);

        } catch (failure) {
            // A reply that is already gone is what the reader wanted; anything else leaves it in place.
            if (failure instanceof ApiError && failure.status === STATUS_NOT_FOUND) {
                await collapseRow();
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

    function handleEditAsked() {
        captureHeight();
        setIsSaved(false);
        setMode('editing');
    }

    function handleViewingRestored() {
        captureHeight();
        setMode('viewing');
    }

    const { author } = reply;
    const canEdit = currentUserId !== null && author.id === currentUserId;
    const canDelete = canEdit || (currentUserId !== null && postAuthorId === currentUserId);

    return (
        <article
            className="reply-cell"
            aria-label={`Reply by ${author.username}`}
            data-entering={isEntering}
            data-saved={isSaved}
            data-busy={isDeleting}
            ref={rowRef}
        >
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
                <div className="reply-cell-view" ref={viewRef}>
                    {mode === 'editing' ? (
                        <ReplyEditor reply={reply} onSaved={handleSaved} onCancel={handleViewingRestored} />
                    ) : (
                        <p className="reply-cell-body" dir="auto">{stripBidiControls(reply.content)}</p>
                    )}
                    {mode !== 'editing' && (canEdit || canDelete) && (
                        <div className="reply-cell-actions">
                            <div
                                className="reply-cell-choices"
                                data-active={mode === 'viewing'}
                                aria-hidden={mode !== 'viewing'}
                                inert={mode !== 'viewing'}
                            >
                                {canEdit && (
                                    <button
                                        type="button"
                                        className="reply-cell-action"
                                        data-action="edit"
                                        ref={editButtonRef}
                                        onClick={handleEditAsked}
                                    >
                                        EDIT
                                    </button>
                                )}
                                {canDelete && (
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
                            </div>
                            {canDelete && (
                                <div
                                    className="reply-cell-confirm"
                                    role="group"
                                    aria-label="Delete this reply?"
                                    data-active={mode === 'confirming-delete'}
                                    aria-hidden={mode !== 'confirming-delete'}
                                    inert={mode !== 'confirming-delete'}
                                >
                                    <span className="reply-cell-question">DELETE?</span>
                                    <button
                                        type="button"
                                        className="reply-cell-action"
                                        data-action="delete"
                                        aria-disabled={isDeleting}
                                        onClick={handleDeleteConfirmed}
                                    >
                                        {isDeleting ? DELETING_LABEL : 'YES'}
                                    </button>
                                    <button
                                        type="button"
                                        className="reply-cell-action"
                                        data-action="edit"
                                        ref={keepButtonRef}
                                        onClick={handleViewingRestored}
                                    >
                                        NO
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <p className="reply-cell-error" role="alert">{deleteErrorMessage}</p>
            </div>
        </article>
    );
}

export default ReplyCell;
