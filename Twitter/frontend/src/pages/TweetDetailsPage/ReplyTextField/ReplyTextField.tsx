import { useEffect, useId, useRef } from 'react';
import type { KeyboardEvent, MouseEvent } from 'react';
import { countCharacters, isOverCharacterLimit, MAX_TWEET_CHARACTERS } from '../../../utils/composeChecks';
import './ReplyTextField.css';


interface ReplyTextFieldProps {
    label: string;
    text: string;
    errorId: string;
    shouldFocusOnMount?: boolean;
    submitLabel: string;
    isSubmitOff: boolean;
    onChange: (text: string) => void;
    onCancel: () => void;
    onBlur?: () => void;
}

// The labelled textarea, its rule, and the row under it (CANCEL, the counter, the submit button), shared by the
// composer and the editor. The error sits under the field in the caller, which names it with `errorId`.
function ReplyTextField({
    label,
    text,
    errorId,
    shouldFocusOnMount = false,
    submitLabel,
    isSubmitOff,
    onChange,
    onCancel,
    onBlur,
}: ReplyTextFieldProps) {
    const fieldId = useId();
    const counterId = useId();
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    useEffect(() => {
        if (shouldFocusOnMount) {
            textareaRef.current?.focus();
        }
    }, [shouldFocusOnMount]);

    function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
        if (event.key === 'Escape') {
            event.preventDefault();
            onCancel();
        }
    }

    // On mouse down, so it lands before the textarea's blur, which collapses an empty composer. The click is for the
    // keyboard.
    function handleCancelMouseDown(event: MouseEvent<HTMLButtonElement>) {
        event.preventDefault();
        onCancel();
    }

    return (
        <>
            <label className="sr-only" htmlFor={fieldId}>{label}</label>
            <textarea
                className="reply-text-field-text"
                id={fieldId}
                ref={textareaRef}
                value={text}
                rows={3}
                aria-describedby={`${counterId} ${errorId}`}
                onChange={(event) => onChange(event.currentTarget.value)}
                onKeyDown={handleKeyDown}
                onBlur={onBlur}
            />
            <div className="reply-text-field-rule" aria-hidden="true" />
            <div className="reply-text-field-meta">
                <button
                    type="button"
                    className="reply-text-field-cancel"
                    onMouseDown={handleCancelMouseDown}
                    onClick={onCancel}
                >
                    CANCEL
                </button>
                <p className="reply-text-field-counter" id={counterId} data-over-limit={isOverCharacterLimit(text)}>
                    {countCharacters(text)} / {MAX_TWEET_CHARACTERS}
                </p>
                <button
                    type="submit"
                    className="reply-text-field-submit"
                    aria-disabled={isSubmitOff}
                >
                    {submitLabel}
                </button>
            </div>
        </>
    );
}

export default ReplyTextField;
