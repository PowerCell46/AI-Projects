import { useEffect, useId, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import type { Person } from '../../../api/accounts/users';
import ListFooter from '../../../components/shared/ListFooter/ListFooter';
import PersonCard from '../../../components/shared/PersonCard/PersonCard';
import { useAuth } from '../../../contexts/AuthContext';
import { useFocusTrap } from '../../../hooks/dom/useFocusTrap';
import { useScrollLock } from '../../../hooks/dom/useScrollLock';
import { useBottomSentinel } from '../../../hooks/lists/useBottomSentinel';
import { bottomStateOf } from '../../../utils/posts/bottomState';
import { FOLLOW_LIST_TEXTS } from './followListKind';
import type { FollowListKind } from './followListKind';
import { useFollowList } from './useFollowList';
import './FollowListModal.css';


// Must stay shorter than the 340ms fade-in in FollowListModal.css, so the close button is focused while the modal is
// still appearing.
const AUTOFOCUS_DELAY_MS = 80;

interface FollowListModalProps {
    kind: FollowListKind;
    username: string;
    onFollowChanged: (person: Person, followedByMe: boolean) => void;
    onClose: () => void;
}

// The people who follow a profile, or whom it follows, over the page. Each is a card with its own follow button.
function FollowListModal({ kind, username, onFollowChanged, onClose }: FollowListModalProps) {
    const { user } = useAuth();
    const list = useFollowList(kind, username);
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.loadedItems.length}-${list.status}`);
    const headlineId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const closeButtonRef = useRef<HTMLButtonElement>(null);
    const handleTrapKeyDown = useFocusTrap(dialogRef);
    const texts = FOLLOW_LIST_TEXTS[kind];
    const bottomState = bottomStateOf(list.status, list.isEnd, list.loadedItems.length);

    useScrollLock();

    // A control pressed during the fade-in already holds focus; moving it would blur a follow button that was just armed.
    useEffect(() => {
        const timerId = window.setTimeout(
            () => {
                if (!dialogRef.current?.contains(document.activeElement)) {
                    closeButtonRef.current?.focus();
                }
            },
            AUTOFOCUS_DELAY_MS,
        );

        return () => window.clearTimeout(timerId);
    }, []);

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        handleTrapKeyDown(event);

        if (event.key === 'Escape') {
            event.preventDefault();
            onClose();
        }
    }

    function isOwnCard(person: Person): boolean {
        return person.username.toLowerCase() === user?.username.toLowerCase();
    }

    return (
        <div
            className="follow-list-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby={headlineId}
            ref={dialogRef}
            onKeyDown={handleKeyDown}
        >
            <div className="follow-list-modal-content">
                <header className="follow-list-modal-header">
                    <div className="follow-list-modal-title">
                        <p className="follow-list-modal-eyebrow">{username}</p>
                        <h2 id={headlineId} className="follow-list-modal-headline">{texts.title}</h2>
                    </div>
                    <button
                        type="button"
                        className="follow-list-modal-close"
                        ref={closeButtonRef}
                        onClick={onClose}
                    >
                        CLOSE
                    </button>
                </header>
                <ul className="follow-list-modal-list">
                    {list.loadedItems.map((person) => (
                        <PersonCard
                            key={person.id}
                            person={person}
                            isFollowable={!isOwnCard(person)}
                            onFollowChanged={(followedByMe) => onFollowChanged(person, followedByMe)}
                        />
                    ))}
                </ul>
                <ListFooter
                    bottomState={bottomState}
                    endText={texts.endText}
                    emptyText={texts.emptyText}
                    announcement={list.announcement}
                    sentinelRef={sentinelRef}
                    onRetry={list.retry}
                />
            </div>
        </div>
    );
}

export default FollowListModal;
