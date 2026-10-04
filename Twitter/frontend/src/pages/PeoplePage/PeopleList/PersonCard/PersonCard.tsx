import type { Person } from '../../../../api/users';
import Avatar from '../../../../components/shared/Avatar/Avatar';
import { truncateBio } from '../../../../utils/bio';
import { formatFollowers } from '../../../../utils/followers';
import FollowButton from './FollowButton/FollowButton';
import { usePersonFollow } from './usePersonFollow';
import './PersonCard.css';


function followersTextOf(followersCount: number): string {
    return `${formatFollowers(followersCount)} ${followersCount === 1 ? 'FOLLOWER' : 'FOLLOWERS'}`;
}

interface PersonCardProps {
    person: Person;
    onFollowChanged: () => void;
}

function PersonCard({ person, onFollowChanged }: PersonCardProps) {
    const follow = usePersonFollow(person, onFollowChanged);
    const bio = truncateBio(person.bio);

    return (
        <li className="person-card">
            <Avatar
                userId={person.id}
                username={person.username}
                pictureUrl={person.profilePictureUrl}
                size="large"
            />
            <div className="person-card-body">
                <p className="person-card-name">{person.username}</p>
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
