import { useEffect, useId, useRef } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import { countCharacters, isOverCharacterLimit, MAX_TWEET_CHARACTERS } from '../../../utils/composeChecks';
import './ReplyTextField.css';


interface ReplyTextFieldProps {
    label: string;
    text: string;
    errorId: string;
    shouldFocusOnMount?: boolean;
    children: ReactNode;
    onChange: (text: string) => void;
    onEscape?: () => void;
}

// The labelled textarea, its rule and its counter, shared by the composer and the editor. The buttons go in the row
// next to the counter, and the error sits under the field in the caller, which names it with `errorId`.
function ReplyTextField({
    label,
    text,
    errorId,
    shouldFocusOnMount = false,
    children,
    onChange,
    onEscape,
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
        if (event.key === 'Escape' && onEscape) {
            event.preventDefault();
            onEscape();
        }
    }

    return (
        <>
            <label className="reply-text-field-label" htmlFor={fieldId}>{label}</label>
            <textarea
                className="reply-text-field-text"
                id={fieldId}
                ref={textareaRef}
                value={text}
                rows={3}
                aria-describedby={`${counterId} ${errorId}`}
                onChange={(event) => onChange(event.currentTarget.value)}
                onKeyDown={handleKeyDown}
            />
            <div className="reply-text-field-rule" aria-hidden="true" />
            <div className="reply-text-field-meta">
                <p className="reply-text-field-counter" id={counterId} data-over-limit={isOverCharacterLimit(text)}>
                    {countCharacters(text)} / {MAX_TWEET_CHARACTERS}
                </p>
                {children}
            </div>
        </>
    );
}

export default ReplyTextField;
