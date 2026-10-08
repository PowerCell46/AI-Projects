import type { MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { Person } from '../../../../api/users';
import Avatar from '../../../../components/shared/Avatar/Avatar';
import FollowButton from '../../../../components/shared/FollowButton/FollowButton';
import { truncateBio } from '../../../../utils/bio';
import { hasTextSelection, isInsideElement } from '../../../../utils/clickTarget';
import { profilePath } from '../../../../routes';
import { formatCount } from '../../../../utils/count';
import { usePersonFollow } from '../../../../hooks/usePersonFollow';
import './PersonCard.css';


// A click on any of these keeps its own meaning (the follow button, the name link) instead of opening the profile.
const OWN_CLICK_SELECTOR = 'button, a';

function followersTextOf(followersCount: number): string {
    return `${formatCount(followersCount)} ${followersCount === 1 ? 'FOLLOWER' : 'FOLLOWERS'}`;
}

interface PersonCardProps {
    person: Person;
    onFollowChanged: () => void;
}

function PersonCard({ person, onFollowChanged }: PersonCardProps) {
    const navigate = useNavigate();
    const follow = usePersonFollow(person, onFollowChanged);
    // The name link is the way in for the keyboard and screen readers; the rest of the card is a larger target.
    function handleClick(event: MouseEvent<HTMLElement>) {
        if (isInsideElement(event.target, OWN_CLICK_SELECTOR) || hasTextSelection()) {
            return;
        }

        navigate(profilePath(person.username));
    }

    const bio = truncateBio(person.bio);

    return (
        <li className="person-card" onClick={handleClick}>
            <Avatar pictureUrl={person.profilePictureUrl} size="large" />
            <div className="person-card-body">
                <p className="person-card-name">
                    <Link className="person-card-link" to={profilePath(person.username)}>{person.username}</Link>
                </p>
                <p className="person-card-bio" dir="auto" data-empty={bio === null}>{bio}</p>
                <p className="person-card-followers">{followersTextOf(follow.followersCount)}</p>
            </div>
            <div className="person-card-action">
                <FollowButton
                    username={person.username}
                    isFollowing={follow.isFollowing}
                    phase={follow.phase}
                    onClick={follow.handleClick}
                    onDisarm={follow.handleDisarm}
                />
                <p className="sr-only" aria-live="polite">{follow.announcement}</p>
            </div>
        </li>
    );
}

export default PersonCard;
