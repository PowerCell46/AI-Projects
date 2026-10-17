import { useEffect, useId, useRef, useState } from 'react';
import type { AnimationEvent, FormEvent } from 'react';
import { ApiError } from '../../../api/core/http';
import { STATUS_NOT_FOUND, STATUS_UNAVAILABLE } from '../../../api/core/httpStatus';
import { createReply } from '../../../api/tweets/replies';
import type { Reply } from '../../../api/tweets/replies';
import { canPublish } from '../../../utils/validation/composeChecks';
import ReplyTextField from '../ReplyTextField/ReplyTextField';
import './ReplyComposer.css';


const POST_DELETED_MESSAGE = 'This post was deleted.';

const BUSY_MESSAGE = 'Busy, try again.';

const OPENER_TEXT = 'Write a reply…';

const SEND_FAILED_MESSAGE = "Couldn't send. Try again.";

type ComposerView = 'closed' | 'open' | 'closing';

function describeSendFailure(failure: unknown): string {
    if (failure instanceof ApiError && failure.status === STATUS_NOT_FOUND) {
        return POST_DELETED_MESSAGE;
    }

    if (failure instanceof ApiError && failure.status === STATUS_UNAVAILABLE) {
        return BUSY_MESSAGE;
    }

    return SEND_FAILED_MESSAGE;
}

interface ReplyComposerProps {
    tweetId: string;
    onSent: (reply: Reply) => void;
}

function ReplyComposer({ tweetId, onSent }: ReplyComposerProps) {
    const [text, setText] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    const [isSending, setIsSending] = useState(false);
    const [view, setView] = useState<ComposerView>('closed');
    const openerRef = useRef<HTMLButtonElement>(null);
    const shouldRefocusOpenerRef = useRef(false);
    const errorId = useId();

    // Cancelling hands the focus back to the row that opened the field; a blur must not take it from where it went.
    useEffect(() => {
        if (view === 'closed' && shouldRefocusOpenerRef.current) {
            shouldRefocusOpenerRef.current = false;
            openerRef.current?.focus();
        }
    }, [view]);

    function handleOpen() {
        setView('open');
    }

    function handleCancel() {
        shouldRefocusOpenerRef.current = true;
        setView('closing');
    }

    // Typed text is never thrown away by a blur: only an empty field folds back into its row.
    function handleBlur() {
        if (text === '') {
            setView('closing');
        }
    }

    // The text and the error stay on screen while the field folds away, and go once it is gone. The end of the
    // form's own animation while closing is the fold (the opening animations end while the view is `open`).
    function handleAnimationEnd(event: AnimationEvent<HTMLFormElement>) {
        if (view !== 'closing' || event.target !== event.currentTarget) {
            return;
        }

        setText('');
        setErrorMessage('');
        setView('closed');
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!isSubmittable) {
            return;
        }

        setIsSending(true);
        setErrorMessage('');

        try {
            const reply = await createReply(tweetId, text.trim());

            setView('closing');
            onSent(reply);

        } catch (failure) {
            setErrorMessage(describeSendFailure(failure));

        } finally {
            setIsSending(false);
        }
    }

    const isSubmittable = canPublish(text, 0) && !isSending;

    if (view === 'closed') {
        return (
            <button
                type="button"
                className="reply-composer-opener"
                ref={openerRef}
                onClick={handleOpen}
            >
                <span className="reply-composer-opener-tag" aria-hidden="true">NEW</span>
                <span className="reply-composer-opener-text">{OPENER_TEXT}</span>
            </button>
        );
    }

    return (
        <form
            className="reply-composer"
            data-closing={view === 'closing'}
            inert={view === 'closing'}
            onSubmit={handleSubmit}
            onAnimationEnd={handleAnimationEnd}
        >
            <div className="reply-composer-inner">
                <ReplyTextField
                    label="YOUR REPLY"
                    text={text}
                    errorId={errorId}
                    shouldFocusOnMount
                    submitLabel="REPLY"
                    isSubmitOff={!isSubmittable}
                    onChange={setText}
                    onCancel={handleCancel}
                    onBlur={handleBlur}
                />
                <p id={errorId} className="reply-composer-error" role="alert">{errorMessage}</p>
            </div>
        </form>
    );
}

export default ReplyComposer;
