import { useEffect, useId, useRef, useState } from 'react';
import type { ChangeEvent, KeyboardEvent } from 'react';
import type { PublishedTweet } from '../../../../api/tweets';
import {
    ALLOWED_IMAGE_TYPES,
    canPublish,
    countCharacters,
    MAX_TWEET_CHARACTERS,
} from '../../../../utils/composeChecks';
import { isApplePlatform } from '../../../../utils/platform';
import ImagePreviews from './ImagePreviews/ImagePreviews';
import { useFocusTrap } from './useFocusTrap';
import { useImageAttachments } from './useImageAttachments';
import { usePublish } from './usePublish';
import { useScrollLock } from './useScrollLock';
import './ComposeModal.css';


// Must stay shorter than the 340ms fade-in in ComposeModal.css, so the textarea is focused while the modal is still
// appearing.
const AUTOFOCUS_DELAY_MS = 80;

const ACCEPTED_IMAGE_TYPES = ALLOWED_IMAGE_TYPES.join(',');

interface ComposeModalProps {
    onClose: () => void;
    onPublished: (tweet: PublishedTweet) => void;
}

function ComposeModal({ onClose, onPublished }: ComposeModalProps) {
    const [text, setText] = useState('');
    const [isConfirmingDiscard, setIsConfirmingDiscard] = useState(false);
    const attachments = useImageAttachments();
    const publishing = usePublish(onPublished);
    const headlineId = useId();
    const counterId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const keepEditingRef = useRef<HTMLButtonElement>(null);
    const handleTrapKeyDown = useFocusTrap(dialogRef);

    useScrollLock();

    // The modal fades in first, so the textarea takes focus a moment after it opens.
    useEffect(() => {
        const timerId = window.setTimeout(
            () => textareaRef.current?.focus(),
            AUTOFOCUS_DELAY_MS,
        );

        return () => window.clearTimeout(timerId);
    }, []);

    useEffect(() => {
        if (isConfirmingDiscard) {
            keepEditingRef.current?.focus();
        }
    }, [isConfirmingDiscard]);

    const characterCount = countCharacters(text);
    const isOverLimit = characterCount > MAX_TWEET_CHARACTERS;
    const hasDraft = characterCount > 0 || attachments.images.length > 0;
    const isSubmittable = canPublish(text, attachments.images.length) && !publishing.isPublishing;
    const shortcutHint = isApplePlatform() ? '⌘↵' : 'CTRL↵';

    function handleCancel() {
        if (publishing.isPublishing) {
            return;
        }

        if (hasDraft) {
            setIsConfirmingDiscard(true);

        } else {
            onClose();
        }
    }

    function handleKeepEditing() {
        setIsConfirmingDiscard(false);
        textareaRef.current?.focus();
    }

    function handlePublish() {
        if (!isSubmittable || isConfirmingDiscard) {
            return;
        }

        publishing.publish({
            content: text.trim(),
            images: attachments.images.map((image) => image.file),
        });
    }

    function handlePick(event: ChangeEvent<HTMLInputElement>) {
        attachments.addFiles(Array.from(event.currentTarget.files ?? []));

        // Cleared so picking the same file again after removing it still fires a change.
        event.currentTarget.value = '';
    }

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        handleTrapKeyDown(event);

        if (event.key === 'Escape') {
            event.preventDefault();

            if (isConfirmingDiscard) {
                handleKeepEditing();

            } else {
                handleCancel();
            }

        } else if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
            event.preventDefault();
            handlePublish();
        }
    }

    return (
        <div
            className="compose-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby={headlineId}
            ref={dialogRef}
            onKeyDown={handleKeyDown}
        >
            <div className="compose-modal-content">
                <p className="compose-modal-eyebrow">NEW POST</p>
                <h2 className="compose-modal-headline" id={headlineId}>What's worth sending up?</h2>
                <textarea
                    className="compose-modal-text"
                    ref={textareaRef}
                    value={text}
                    placeholder="write something"
                    aria-labelledby={headlineId}
                    aria-describedby={counterId}
                    rows={4}
                    onChange={(event) => setText(event.currentTarget.value)}
                />
                <div className="compose-modal-rule" aria-hidden="true" />
                <ImagePreviews images={attachments.images} onRemove={attachments.removeImage} />
                <div className="compose-modal-meta">
                    <label className="compose-modal-attach">
                        <input
                            type="file"
                            className="sr-only"
                            accept={ACCEPTED_IMAGE_TYPES}
                            multiple
                            onChange={handlePick}
                        />
                        ATTACH IMAGE
                    </label>
                    <p className="compose-modal-counter" id={counterId} data-over-limit={isOverLimit}>
                        {characterCount} / {MAX_TWEET_CHARACTERS}
                    </p>
                </div>
                <p className="compose-modal-error" role="alert">{attachments.errorMessage}</p>
                {isConfirmingDiscard ? (
                    <div className="compose-modal-actions">
                        <p className="compose-modal-question">DISCARD POST?</p>
                        <button type="button" className="compose-modal-discard" onClick={onClose}>DISCARD</button>
                        <button
                            type="button"
                            className="compose-modal-cancel"
                            ref={keepEditingRef}
                            onClick={handleKeepEditing}
                        >
                            KEEP EDITING
                        </button>
                    </div>
                ) : (
                    <div className="compose-modal-actions">
                        <button
                            type="button"
                            className="compose-modal-publish"
                            aria-disabled={!isSubmittable}
                            onClick={handlePublish}
                        >
                            PUBLISH {shortcutHint}
                        </button>
                        <button type="button" className="compose-modal-cancel" onClick={handleCancel}>CANCEL</button>
                    </div>
                )}
                <p className="compose-modal-error" role="alert">{publishing.errorMessage}</p>
            </div>
        </div>
    );
}

export default ComposeModal;
