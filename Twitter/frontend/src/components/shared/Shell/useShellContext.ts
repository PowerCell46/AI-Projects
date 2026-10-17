import { useOutletContext } from 'react-router-dom';
import type { TweetItem } from '../../../api/tweets/tweetPage';


export interface ShellContext {
    ownPosts: TweetItem[];
    followChangeCount: number;
    onFollowChanged: () => void;
    onProfilePictureChanged: (pictureUrl: string | null) => void;
}

export function useShellContext(): ShellContext {
    return useOutletContext<ShellContext>();
}
