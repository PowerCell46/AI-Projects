import { ENDPOINTS } from './endpoints';


export interface AuthUser {
    id: string;
    username: string;
    email: string;
}

export interface RegisterRequest {
    email: string;
    username: string;
    password: string;
}

export interface LoginRequest {
    identifier: string;
    password: string;
}

interface ErrorBody {
    messages?: unknown;
}

const NETWORK_FAILURE_STATUS = 0;

const JSON_HEADERS = {
    'Content-Type': 'application/json',
};

export class AuthApiError extends Error {
    readonly status: number;
    readonly messages: string[];

    constructor(status: number, messages: string[]) {
        super(messages.join(' ') || `Auth request failed with status ${status}.`);
        this.name = 'AuthApiError';
        this.status = status;
        this.messages = messages;
    }
}

function extractMessages(body: ErrorBody | null): string[] {
    if (!body || !Array.isArray(body.messages)) {
        return [];
    }

    return body.messages.filter((message): message is string => typeof message === 'string');
}

async function readErrorMessages(response: Response): Promise<string[]> {
    try {
        const body = await response.json() as ErrorBody | null;

        return extractMessages(body);

    } catch {
        return [];
    }
}

async function send(url: string, init: RequestInit): Promise<Response> {
    let response: Response;

    try {
        response = await fetch(url, {
            ...init,
            credentials: 'include',
        });

    } catch {
        throw new AuthApiError(NETWORK_FAILURE_STATUS, []);
    }

    if (!response.ok) {
        throw new AuthApiError(response.status, await readErrorMessages(response));
    }

    return response;
}

async function postJson(url: string, body: object): Promise<Response> {
    return send(url, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify(body),
    });
}

async function readUser(response: Response): Promise<AuthUser> {
    return await response.json() as AuthUser;
}

export async function register(request: RegisterRequest): Promise<AuthUser> {
    return readUser(await postJson(ENDPOINTS.auth.register, request));
}

export async function login(request: LoginRequest): Promise<AuthUser> {
    return readUser(await postJson(ENDPOINTS.auth.login, request));
}

export async function logout(): Promise<void> {
    await send(ENDPOINTS.auth.logout, { method: 'POST' });
}

export async function me(): Promise<AuthUser> {
    return readUser(await send(ENDPOINTS.auth.me, { method: 'GET' }));
}

export async function confirm(token: string): Promise<void> {
    await postJson(ENDPOINTS.auth.confirm, { token });
}

export async function resendConfirmation(email: string): Promise<void> {
    await postJson(ENDPOINTS.auth.resendConfirmation, { email });
}
