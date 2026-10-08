import { formatFullCount, pluralLabel } from '../../../utils/profileFormat';
import './ProfileStats.css';


interface Stat {
    key: string;
    value: string;
    label: string;
}

interface ProfileStatsProps {
    followersCount: number;
    followingCount: number;
    tweetCount: number | null;
}

// The number and its label read as one phrase ("1,204 FOLLOWERS"), which is also what a screen reader says.
function ProfileStats({ followersCount, followingCount, tweetCount }: ProfileStatsProps) {
    const stats: Stat[] = [
        {
            key: 'followers',
            value: formatFullCount(followersCount),
            label: pluralLabel(followersCount, 'FOLLOWER', 'FOLLOWERS'),
        },
        {
            key: 'following',
            value: formatFullCount(followingCount),
            label: 'FOLLOWING',
        },
    ];

    if (tweetCount !== null) {
        stats.push({
            key: 'tweets',
            value: formatFullCount(tweetCount),
            label: pluralLabel(tweetCount, 'TWEET', 'TWEETS'),
        });
    }

    return (
        <ul className="profile-stats" aria-label="Profile statistics">
            {stats.map((stat) => (
                <li key={stat.key} className="profile-stats-item">
                    <span className="profile-stats-value">{stat.value}</span>
                    {' '}
                    <span className="profile-stats-label">{stat.label}</span>
                </li>
            ))}
        </ul>
    );
}

export default ProfileStats;
