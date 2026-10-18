export type FollowListKind = 'followers' | 'following';

interface FollowListTexts {
    title: string;
    endText: string;
    emptyText: string;
}

export const FOLLOW_LIST_TEXTS: Record<FollowListKind, FollowListTexts> = {
    followers: {
        title: 'FOLLOWERS',
        endText: 'END OF FOLLOWERS',
        emptyText: 'NO FOLLOWERS YET',
    },
    following: {
        title: 'FOLLOWING',
        endText: 'END OF FOLLOWING',
        emptyText: 'NOT FOLLOWING ANYONE YET',
    },
};

// The open list lives in the router's location state, so the browser's Back button closes it. State from anywhere
// else (or none) opens nothing.
export function followListOf(locationState: unknown): FollowListKind | null {
    if (typeof locationState !== 'object' || locationState === null || !('followList' in locationState)) {
        return null;
    }

    const { followList } = locationState;

    return followList === 'followers' || followList === 'following' ? followList : null;
}
