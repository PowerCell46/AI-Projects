import { useId, useState } from 'react';
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
    const errorId = useId();

    const isSubmittable = canPublish(text, 0) && !isSending;

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
            onSent(reply);

        } catch (failure) {
            setErrorMessage(describeSendFailure(failure));

        } finally {
            setIsSending(false);
        }
    }

    return (
        <form className="reply-composer" onSubmit={handleSubmit}>
            <ReplyTextField label="YOUR REPLY" text={text} errorId={errorId} onChange={setText}>
                <button type="submit" className="reply-composer-send" aria-disabled={!isSubmittable}>REPLY</button>
            </ReplyTextField>
            <p className="reply-composer-error" id={errorId} role="alert">{errorMessage}</p>
        </form>
    );
}

export default ReplyComposer;
