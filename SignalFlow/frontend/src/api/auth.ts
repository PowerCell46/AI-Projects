import { ENDPOINTS } from './endpoints';
import { requestJson, requestVoid } from './request';

export type Role = 'USER' | 'ADMIN';

export interface AuthUser {
    id: string;
    email: string;
    role: Role;
    createdAt: string;
}

export interface AuthCredentials {
    email: string;
    password: string;
}

function postCredentials(url: string, credentials: AuthCredentials): Promise<AuthUser> {
    return requestJson<AuthUser>(
        url,
        {
            method: 'POST',
            body: credentials,
        },
    );
}

export function register(credentials: AuthCredentials): Promise<AuthUser> {
    return postCredentials(ENDPOINTS.auth.register, credentials);
}

export function login(credentials: AuthCredentials): Promise<AuthUser> {
    return postCredentials(ENDPOINTS.auth.login, credentials);
}

export function logout(): Promise<void> {
    return requestVoid(
        ENDPOINTS.auth.logout,
        { method: 'POST' },
    );
}

export function me(): Promise<AuthUser> {
    return requestJson<AuthUser>(ENDPOINTS.auth.me);
}
