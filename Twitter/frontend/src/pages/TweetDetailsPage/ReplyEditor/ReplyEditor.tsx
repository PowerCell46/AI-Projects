import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { updateReply } from '../../../api/replies';
import type { Reply } from '../../../api/replies';
import { canPublish } from '../../../utils/composeChecks';
import ReplyTextField from '../ReplyTextField/ReplyTextField';
import './ReplyEditor.css';


const SAVE_FAILED_MESSAGE = "Couldn't save. Try again.";

interface ReplyEditorProps {
    reply: Reply;
    onSaved: (reply: Reply) => void;
    onCancel: () => void;
}

function ReplyEditor({ reply, onSaved, onCancel }: ReplyEditorProps) {
    const [text, setText] = useState(reply.content);
    const [errorMessage, setErrorMessage] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    const errorId = useId();

    const isSavable = canPublish(text, 0) && text.trim() !== reply.content.trim() && !isSaving;

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!isSavable) {
            return;
        }

        setIsSaving(true);
        setErrorMessage('');

        try {
            onSaved(await updateReply(reply.tweetId, reply.id, text.trim()));

        } catch {
            setErrorMessage(SAVE_FAILED_MESSAGE);

        } finally {
            setIsSaving(false);
        }
    }

    return (
        <form className="reply-editor" onSubmit={handleSubmit}>
            <ReplyTextField
                label="EDIT YOUR REPLY"
                text={text}
                errorId={errorId}
                shouldFocusOnMount
                onChange={setText}
                onEscape={onCancel}
            >
                <div className="reply-editor-actions">
                    <button type="submit" className="reply-editor-save" aria-disabled={!isSavable}>SAVE</button>
                    <button type="button" className="reply-editor-cancel" onClick={onCancel}>CANCEL</button>
                </div>
            </ReplyTextField>
            <p className="reply-editor-error" id={errorId} role="alert">{errorMessage}</p>
        </form>
    );
}

export default ReplyEditor;
