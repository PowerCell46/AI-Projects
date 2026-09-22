const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? ''

export type Role = 'USER' | 'ADMIN'

export interface AuthUser {
    id: string
    email: string
    role: Role
    createdAt: string
}

export interface AuthCredentials {
    email: string
    password: string
}

export class AuthApiError extends Error {
    readonly status: number

    constructor(status: number) {
        super(`Auth request failed with status ${status}`)
        this.status = status
    }
}

async function postCredentials(path: string, credentials: AuthCredentials): Promise<AuthUser> {
    const response = await fetch(`${BASE_URL}${path}`, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
    })

    if (!response.ok) {
        throw new AuthApiError(response.status)
    }

    return response.json() as Promise<AuthUser>
}

export function register(credentials: AuthCredentials): Promise<AuthUser> {
    return postCredentials('/api/v1/auth/register', credentials)
}

export function login(credentials: AuthCredentials): Promise<AuthUser> {
    return postCredentials('/api/v1/auth/login', credentials)
}

export async function logout(): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/v1/auth/logout`, {
        method: 'POST',
        credentials: 'include',
    })

    if (!response.ok) {
        throw new AuthApiError(response.status)
    }
}

export async function me(): Promise<AuthUser> {
    const response = await fetch(`${BASE_URL}/api/v1/auth/me`, {
        credentials: 'include',
    })

    if (!response.ok) {
        throw new AuthApiError(response.status)
    }

    return response.json() as Promise<AuthUser>
}
