import { useOutletContext } from 'react-router-dom';
import type { TweetItem } from '../../../api/tweetPage';


export interface ShellContext {
    ownPosts: TweetItem[];
}

export function useShellContext(): ShellContext {
    return useOutletContext<ShellContext>();
}
