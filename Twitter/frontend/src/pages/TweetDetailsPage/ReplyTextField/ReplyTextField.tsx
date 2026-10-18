import { useEffect, useId, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { countCharacters } from '../../../utils/text/count';
import { isOverCharacterLimit, MAX_TWEET_CHARACTERS } from '../../../utils/validation/composeChecks';
import './ReplyTextField.css';


interface ReplyTextFieldProps {
    label: string;
    text: string;
    errorId: string;
    submitLabel: string;
    isSubmitOff: boolean;
    placeholder?: string;
    isCancelHidden?: boolean;
    shouldFocusOnMount?: boolean;
    onChange: (text: string) => void;
    onCancel: () => void;
}

// The labelled textarea, its rule, and the row under it (CANCEL, the counter, the submit button), shared by the
// composer and the editor. The error sits under the field in the caller, which names it with `errorId`. A hidden
// CANCEL keeps its place in the row, so the row does not change when it shows.
function ReplyTextField({
    label,
    text,
    errorId,
    submitLabel,
    isSubmitOff,
    placeholder,
    isCancelHidden = false,
    shouldFocusOnMount = false,
    onChange,
    onCancel,
}: ReplyTextFieldProps) {
    const fieldId = useId();
    const counterId = useId();
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Without scrolling: the composer grows open at that moment and scrolls itself into view when it has.
    useEffect(() => {
        if (shouldFocusOnMount) {
            textareaRef.current?.focus({ preventScroll: true });
        }
    }, [shouldFocusOnMount]);

    function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
        if (event.key === 'Escape') {
            event.preventDefault();
            onCancel();
        }
    }

    return (
        <>
            <label className="reply-text-field-label" htmlFor={fieldId}>{label}</label>
            <textarea
                id={fieldId}
                className="reply-text-field-text"
                aria-describedby={`${counterId} ${errorId}`}
                value={text}
                placeholder={placeholder}
                rows={3}
                ref={textareaRef}
                onChange={(event) => onChange(event.currentTarget.value)}
                onKeyDown={handleKeyDown}
            />
            <div className="reply-text-field-rule" aria-hidden="true" />
            <div className="reply-text-field-meta">
                <button
                    type="button"
                    className="reply-text-field-cancel"
                    data-hidden={isCancelHidden}
                    onClick={onCancel}
                >
                    CANCEL
                </button>
                <p id={counterId} className="reply-text-field-counter" data-over-limit={isOverCharacterLimit(text)}>
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
