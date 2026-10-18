import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../../api/core/http';
import { STATUS_NOT_FOUND, STATUS_UNAVAILABLE } from '../../../api/core/httpStatus';
import { createReply } from '../../../api/tweets/replies';
import type { Reply } from '../../../api/tweets/replies';
import { canPublish } from '../../../utils/validation/composeChecks';
import ReplyTextField from '../ReplyTextField/ReplyTextField';
import './ReplyComposer.css';


const POST_DELETED_MESSAGE = 'This post was deleted.';

const BUSY_MESSAGE = 'Busy, try again.';

const PLACEHOLDER = 'Write a reply…';

const SEND_FAILED_MESSAGE = "Couldn't send. Try again.";

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

// Always on the page at its full size, so nothing moves or is covered when it is used: the field is there, its error
// line is reserved, and CANCEL only shows (in its reserved place) once there is something to cancel.
function ReplyComposer({ tweetId, onSent }: ReplyComposerProps) {
    const [text, setText] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    const [isSending, setIsSending] = useState(false);
    const errorId = useId();

    // Dropping the draft also lets go of the field, so the keyboard on a phone goes away.
    function resetField() {
        setText('');
        setErrorMessage('');

        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
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

            resetField();
            onSent(reply);

        } catch (failure) {
            setErrorMessage(describeSendFailure(failure));

        } finally {
            setIsSending(false);
        }
    }

    const isSubmittable = canPublish(text, 0) && !isSending;

    return (
        <form className="reply-composer" onSubmit={handleSubmit}>
            <ReplyTextField
                label="YOUR REPLY"
                text={text}
                errorId={errorId}
                placeholder={PLACEHOLDER}
                submitLabel="REPLY"
                isSubmitOff={!isSubmittable}
                isCancelHidden={text === ''}
                onChange={setText}
                onCancel={resetField}
            />
            <p id={errorId} className="reply-composer-error" role="alert">{errorMessage}</p>
        </form>
    );
}

export default ReplyComposer;
