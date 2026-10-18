import { useEffect, useRef } from 'react';
import type { FollowListKind } from '../FollowListModal/followListKind';
import { formatFullCount, pluralLabel } from '../../../utils/text/profileFormat';
import './ProfileStats.css';


interface Stat {
    key: string;
    value: string;
    label: string;
    listKind?: FollowListKind;
}

interface ProfileStatsProps {
    followersCount: number;
    followingCount: number;
    tweetCount: number | null;
    openListKind: FollowListKind | null;
    onOpenList: (kind: FollowListKind) => void;
}

// The number and its label read as one phrase ("1,204 FOLLOWERS"), which is also what a screen reader says. Followers
// and following open their list; when it closes, focus goes back to the button that opened it.
function ProfileStats({ followersCount, followingCount, tweetCount, openListKind, onOpenList }: ProfileStatsProps) {
    const openerRef = useRef<HTMLButtonElement | null>(null);
    const wasListOpenRef = useRef(false);

    useEffect(() => {
        if (wasListOpenRef.current && openListKind === null) {
            openerRef.current?.focus();
        }

        wasListOpenRef.current = openListKind !== null;
    }, [openListKind]);

    const stats: Stat[] = [
        {
            key: 'followers',
            value: formatFullCount(followersCount),
            label: pluralLabel(followersCount, 'FOLLOWER', 'FOLLOWERS'),
            listKind: 'followers',
        },
        {
            key: 'following',
            value: formatFullCount(followingCount),
            label: 'FOLLOWING',
            listKind: 'following',
        },
    ];

    if (tweetCount !== null) {
        stats.push({
            key: 'tweets',
            value: formatFullCount(tweetCount),
            label: pluralLabel(tweetCount, 'TWEET', 'TWEETS'),
        });
    }

    function renderStat(stat: Stat) {
        const { listKind } = stat;
        const text = (
            <>
                <span className="profile-stats-value">{stat.value}</span>
                {' '}
                <span className="profile-stats-label">{stat.label}</span>
            </>
        );

        // One element around the text keeps the space between number and label, which a flex item list would drop.
        if (!listKind) {
            return <span>{text}</span>;
        }

        return (
            <button
                type="button"
                className="profile-stats-button"
                aria-haspopup="dialog"
                onClick={(event) => {
                    openerRef.current = event.currentTarget;
                    onOpenList(listKind);
                }}
            >
                {text}
            </button>
        );
    }

    return (
        <ul className="profile-stats" aria-label="Profile statistics">
            {stats.map((stat) => (
                <li key={stat.key} className="profile-stats-item">
                    {renderStat(stat)}
                </li>
            ))}
        </ul>
    );
}

export default ProfileStats;
