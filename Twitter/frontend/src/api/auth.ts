import { ENDPOINTS } from './endpoints';
import { jsonRequest, readJson, send } from './http';


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

async function postJson(url: string, body: object): Promise<Response> {
    return send(
        url,
        jsonRequest('POST', body),
    );
}

async function readUser(response: Response): Promise<AuthUser> {
    return readJson<AuthUser>(response);
}

export async function register(request: RegisterRequest): Promise<AuthUser> {
    return readUser(await postJson(ENDPOINTS.auth.register, request));
}

export async function login(request: LoginRequest): Promise<AuthUser> {
    return readUser(await postJson(ENDPOINTS.auth.login, request));
}

export async function logout(): Promise<void> {
    await send(
        ENDPOINTS.auth.logout,
        { method: 'POST' },
    );
}

export async function me(): Promise<AuthUser> {
    const response = await send(
        ENDPOINTS.auth.me,
        { method: 'GET' },
    );

    return readUser(response);
}

export async function confirm(token: string): Promise<void> {
    await postJson(
        ENDPOINTS.auth.confirm,
        { token },
    );
}

export async function resendConfirmation(email: string): Promise<void> {
    await postJson(
        ENDPOINTS.auth.resendConfirmation,
        { email },
    );
}
