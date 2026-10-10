import { matchPath } from 'react-router-dom';
import { ROUTES } from '../routes';


const APP_NAME = 'Twitter';

const TITLE_SEPARATOR = ' · ';

const TITLE_OF_ROUTE: Record<string, string> = {
    [ROUTES.login]: 'Log in',
    [ROUTES.register]: 'Sign up',
    [ROUTES.confirm]: 'Confirm your account',
    [ROUTES.resend]: 'Resend the link',
    [ROUTES.feed]: 'Feed',
    [ROUTES.people]: 'People',
    [ROUTES.saved]: 'Saved tweets',
    [ROUTES.liked]: 'Liked tweets',
    [ROUTES.tweet]: 'Post',
};

function withAppName(pageTitle: string): string {
    return `${pageTitle}${TITLE_SEPARATOR}${APP_NAME}`;
}

// The browser tab, the history list and a screen reader all name a page by this title.
export function titleOfPath(pathname: string): string {
    const matchedRoute = Object.keys(TITLE_OF_ROUTE)
        .find((route) => matchPath(route, pathname) !== null);

    if (matchedRoute) {
        return withAppName(TITLE_OF_ROUTE[matchedRoute]);
    }

    const profileMatch = matchPath(ROUTES.profile, pathname);

    if (profileMatch?.params.username) {
        return withAppName(profileMatch.params.username);
    }

    return APP_NAME;
}
