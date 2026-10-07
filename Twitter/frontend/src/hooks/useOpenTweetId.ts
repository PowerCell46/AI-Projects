import { matchPath, useLocation } from 'react-router-dom';
import { ROUTES } from '../routes';


const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// The tweet the address opens in the details panel, or null on any other page. The id goes into API paths, so
// anything that is not a UUID (a `..` or an encoded slash, say) counts as no tweet at all.
export function useOpenTweetId(): string | null {
    const { pathname } = useLocation();
    const tweetId = matchPath(ROUTES.tweet, pathname)?.params.tweetId;

    return tweetId && UUID_PATTERN.test(tweetId) ? tweetId : null;
}
