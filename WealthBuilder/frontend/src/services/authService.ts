import { apiRequest } from './apiClient';
import { AUTH_ENDPOINTS } from '../constants/api';
import type { AuthTokenResponse, CurrentUser, Credentials } from '../types/auth';


// Maps the backend auth endpoints to typed calls. A 401 here means "bad credentials",
// not "session expired", so we suppress the global unauthorized handler.

export const registerUser = (credentials: Credentials): Promise<AuthTokenResponse> => {
    return apiRequest<AuthTokenResponse>(AUTH_ENDPOINTS.REGISTER, {
        method: 'POST',
        body: credentials,
        suppressUnauthorizedHandler: true,
    });
};

export const loginUser = (credentials: Credentials): Promise<AuthTokenResponse> => {
    return apiRequest<AuthTokenResponse>(AUTH_ENDPOINTS.LOGIN, {
        method: 'POST',
        body: credentials,
        suppressUnauthorizedHandler: true,
    });
};

export const fetchCurrentUser = (): Promise<CurrentUser> => {
    return apiRequest<CurrentUser>(AUTH_ENDPOINTS.ME);
};
