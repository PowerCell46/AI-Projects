// Domain types mirroring the backend auth contract (see AuthController + DTOs).

export type Role = 'USER' | 'MODERATOR';

export type AuthMode = 'login' | 'register';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';


export interface Credentials {
    username: string;
    password: string;
}

// Body of POST /api/auth/register and /login responses.
export interface AuthTokenResponse {
    token: string;
}

// Body of GET /api/auth/me. `balance` is the server-computed net invested amount.
export interface CurrentUser {
    username: string;
    role: Role;
    balance: number;
}

export interface AuthContextValue {
    user: CurrentUser | null;
    token: string | null;
    status: AuthStatus;
    isAuthenticated: boolean;
    // True only for the render right after an interactive sign-in (not a refresh-time
    // rehydrate), so the home screen knows to play its entrance sweep. Cleared on read.
    justAuthenticated: boolean;
    login: (credentials: Credentials) => Promise<void>;
    register: (credentials: Credentials) => Promise<void>;
    logout: () => void;
    clearJustAuthenticated: () => void;
}
