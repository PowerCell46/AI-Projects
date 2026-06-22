import { apiRequest } from './apiClient';
import { AUTH_ENDPOINTS } from '../constants/api';
import type { CurrentUser, Credentials } from '../types/auth';


// Maps the backend auth endpoints to typed calls. Register and login set the httpOnly auth cookie
// server-side and return the current user. A 401 here means "bad credentials", not "session
// expired", so we suppress the global unauthorized handler.

export const registerUser = (credentials: Credentials): Promise<CurrentUser> => {
    return apiRequest<CurrentUser>(AUTH_ENDPOINTS.REGISTER, {
        method: 'POST',
        body: credentials,
        suppressUnauthorizedHandler: true,
    });
};

export const loginUser = (credentials: Credentials): Promise<CurrentUser> => {
    return apiRequest<CurrentUser>(AUTH_ENDPOINTS.LOGIN, {
        method: 'POST',
        body: credentials,
        suppressUnauthorizedHandler: true,
    });
};

// Clears the auth cookie server-side. Suppresses the global 401 handler so logging out while
// already unauthenticated doesn't trigger a redundant redirect.
export const logoutUser = (): Promise<void> => {
    return apiRequest<void>(AUTH_ENDPOINTS.LOGOUT, {
        method: 'POST',
        suppressUnauthorizedHandler: true,
    });
};

export const fetchCurrentUser = (): Promise<CurrentUser> => {
    return apiRequest<CurrentUser>(AUTH_ENDPOINTS.ME);
};
