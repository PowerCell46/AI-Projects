import { createHmac } from 'node:crypto';
import { expect } from '@playwright/test';
import { API_PATHS, HTTP_STATUS, type Account } from './fixtures';

export const ACCESS_TOKEN_COOKIE = 'access_token';

// The same value as the gateway's JWT_SECRET in docker-compose.e2e.yml, so a spec can sign a token the gateway accepts.
export const E2E_JWT_SECRET = 'e2e-test-only-jwt-secret-not-for-any-real-use-0123456789abcdef';

export const HOUR_IN_SECONDS = 3_600;

const MILLISECONDS_PER_SECOND = 1_000;

export interface TokenClaims {
    userId: string;
    username: string;
    email: string;
}

interface CurrentUser {
    id: string;
    username: string;
    email: string;
}

function encodeSegment(value: object): string {
    return Buffer
        .from(JSON.stringify(value))
        .toString('base64url');
}

// Signs the same HS256 token the gateway mints at login. A negative lifetime gives a token that has already expired.
export function signAccessToken(claims: TokenClaims, secret: string, lifetimeSeconds: number): string {
    const issuedAt = Math.floor(Date.now() / MILLISECONDS_PER_SECOND);
    const header = encodeSegment({
        alg: 'HS256',
        typ: 'JWT',
    });
    const payload = encodeSegment({
        sub: claims.userId,
        username: claims.username,
        email: claims.email,
        iat: issuedAt,
        exp: issuedAt + lifetimeSeconds,
    });
    const signature = createHmac('sha256', secret)
        .update(`${header}.${payload}`)
        .digest('base64url');

    return `${header}.${payload}.${signature}`;
}

export function cookieHeader(token: string): Record<string, string> {
    return { cookie: `${ACCESS_TOKEN_COOKIE}=${token}` };
}

export async function claimsOf(account: Account): Promise<TokenClaims> {
    const response = await account.api.get(API_PATHS.me);

    expect(response.status()).toBe(HTTP_STATUS.ok);

    const currentUser: CurrentUser = await response.json();

    return {
        userId: currentUser.id,
        username: currentUser.username,
        email: currentUser.email,
    };
}

export async function expiredTokenOf(account: Account): Promise<string> {
    return signAccessToken(await claimsOf(account), E2E_JWT_SECRET, -HOUR_IN_SECONDS);
}
