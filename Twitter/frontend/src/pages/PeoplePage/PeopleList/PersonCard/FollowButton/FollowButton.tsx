import type { KeyboardEvent } from 'react';
import type { FollowPhase } from '../useFollowPhase';
import './FollowButton.css';


function labelOf(isFollowing: boolean, phase: FollowPhase): string {
    if (phase === 'failed') {
        return 'TRY AGAIN';
    }

    if (phase === 'armed') {
        return 'UNFOLLOW?';
    }

    return isFollowing ? 'FOLLOWING' : 'FOLLOW';
}

interface FollowButtonProps {
    username: string;
    isFollowing: boolean;
    phase: FollowPhase;
    onClick: () => void;
    onDisarm: () => void;
}

function FollowButton({ username, isFollowing, phase, onClick, onDisarm }: FollowButtonProps) {
    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
        if (event.key === 'Escape') {
            onDisarm();
        }
    }

    return (
        <button
            type="button"
            className="follow-button"
            aria-label={`Follow ${username}`}
            aria-pressed={isFollowing}
            data-following={isFollowing}
            data-phase={phase}
            onClick={onClick}
            onBlur={onDisarm}
            onKeyDown={handleKeyDown}
        >
            <span className="follow-button-label" aria-hidden="true">{labelOf(isFollowing, phase)}</span>
        </button>
    );
}

export default FollowButton;
