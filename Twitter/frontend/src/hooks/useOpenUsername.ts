import { matchPath, useLocation } from 'react-router-dom';
import { ROUTES } from '../routes';


// The username the address opens in the profile panel, or null on any other page. It is whatever the address holds:
// whether it can be a username at all is the page's call, because a bad one shows USER NOT FOUND.
export function useOpenUsername(): string | null {
    const { pathname } = useLocation();

    return matchPath(ROUTES.profile, pathname)?.params.username ?? null;
}
