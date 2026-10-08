import type { KeyboardEvent } from 'react';
import type { ArmPhase } from '../../../hooks/useArmPhase';
import GlowButton from '../GlowButton/GlowButton';
import './FollowButton.css';


function labelOf(isFollowing: boolean, phase: ArmPhase): string {
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
    phase: ArmPhase;
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
        <GlowButton
            className="follow-button"
            label={labelOf(isFollowing, phase)}
            isLabelHidden
            aria-label={`Follow ${username}`}
            aria-pressed={isFollowing}
            data-following={isFollowing}
            data-phase={phase}
            onClick={onClick}
            onBlur={onDisarm}
            onKeyDown={handleKeyDown}
        />
    );
}

export default FollowButton;
