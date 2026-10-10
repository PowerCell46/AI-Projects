interface ErrorBody {
    messages?: unknown;
}

type UnauthorizedHandler = () => void;

const NETWORK_FAILURE_STATUS = 0;

const UNAUTHORIZED_STATUS = 401;

const REQUEST_TIMEOUT_MS = 30_000;

// Image uploads are up to 20 MB, which a slow connection cannot send in the normal time.
const UPLOAD_TIMEOUT_MS = 120_000;

const JSON_HEADERS = {
    'Content-Type': 'application/json',
};

let unauthorizedHandler: UnauthorizedHandler | null = null;

export class ApiError extends Error {
    readonly status: number;
    readonly messages: string[];

    constructor(status: number, messages: string[]) {
        super(messages.join(' ') || `Request failed with status ${status}.`);
        this.name = 'ApiError';
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

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
    unauthorizedHandler = handler;
}

export function jsonRequest(method: string, body: object): RequestInit {
    return {
        method,
        headers: JSON_HEADERS,
        body: JSON.stringify(body),
    };
}

export async function readJson<T>(response: Response): Promise<T> {
    return await response.json() as T;
}

function timeoutFor(init: RequestInit): number {
    return init.body instanceof FormData ? UPLOAD_TIMEOUT_MS : REQUEST_TIMEOUT_MS;
}

// A request that never answers would leave its list or button waiting forever; the timeout makes it a network failure.
export async function send(url: string, init: RequestInit): Promise<Response> {
    let response: Response;

    try {
        response = await fetch(url, {
            ...init,
            credentials: 'include',
            signal: AbortSignal.timeout(timeoutFor(init)),
        });

    } catch {
        throw new ApiError(NETWORK_FAILURE_STATUS, []);
    }

    if (!response.ok) {
        throw new ApiError(response.status, await readErrorMessages(response));
    }

    return response;
}

// The auth endpoints use `send` directly: a 401 there means wrong credentials or no session, not an expired one.
export async function sendAuthenticated(url: string, init: RequestInit): Promise<Response> {
    try {
        return await send(url, init);

    } catch (failure) {
        if (failure instanceof ApiError && failure.status === UNAUTHORIZED_STATUS) {
            unauthorizedHandler?.();
        }

        throw failure;
    }
}
