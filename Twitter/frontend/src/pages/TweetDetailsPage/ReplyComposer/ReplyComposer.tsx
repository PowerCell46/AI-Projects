import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../../api/http';
import { createReply } from '../../../api/replies';
import type { Reply } from '../../../api/replies';
import { canPublish } from '../../../utils/composeChecks';
import ReplyTextField from '../ReplyTextField/ReplyTextField';
import './ReplyComposer.css';


const NOT_FOUND_STATUS = 404;

const UNAVAILABLE_STATUS = 503;

const POST_DELETED_MESSAGE = 'This post was deleted.';

const BUSY_MESSAGE = 'Busy, try again.';

const OPENER_TEXT = 'Write a reply…';

const SEND_FAILED_MESSAGE = "Couldn't send. Try again.";

function describeSendFailure(failure: unknown): string {
    if (failure instanceof ApiError && failure.status === NOT_FOUND_STATUS) {
        return POST_DELETED_MESSAGE;
    }

    if (failure instanceof ApiError && failure.status === UNAVAILABLE_STATUS) {
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
    const [isOpen, setIsOpen] = useState(false);
    const openerRef = useRef<HTMLButtonElement>(null);
    const shouldRefocusOpenerRef = useRef(false);
    const errorId = useId();

    const isSubmittable = canPublish(text, 0) && !isSending;

    // Cancelling hands the focus back to the row that opened the field; a blur must not take it from where it went.
    useEffect(() => {
        if (!isOpen && shouldRefocusOpenerRef.current) {
            shouldRefocusOpenerRef.current = false;
            openerRef.current?.focus();
        }
    }, [isOpen]);

    function handleCancel() {
        shouldRefocusOpenerRef.current = true;
        setText('');
        setErrorMessage('');
        setIsOpen(false);
    }

    // Typed text is never thrown away by a blur: only an empty field folds back into its row.
    function handleBlur() {
        if (text === '') {
            setIsOpen(false);
        }
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

            setText('');
            setIsOpen(false);
            onSent(reply);

        } catch (failure) {
            setErrorMessage(describeSendFailure(failure));

        } finally {
            setIsSending(false);
        }
    }

    if (!isOpen) {
        return (
            <button type="button" className="reply-composer-opener" ref={openerRef} onClick={() => setIsOpen(true)}>
                <span className="reply-composer-opener-tag" aria-hidden="true">NEW</span>
                <span className="reply-composer-opener-text">{OPENER_TEXT}</span>
            </button>
        );
    }

    return (
        <form className="reply-composer" onSubmit={handleSubmit}>
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
            <p className="reply-composer-error" id={errorId} role="alert">{errorMessage}</p>
        </form>
    );
}

export default ReplyComposer;
