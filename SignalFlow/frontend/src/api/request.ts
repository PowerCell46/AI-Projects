const JSON_HEADERS = { 'Content-Type': 'application/json' };

type HttpMethod = 'GET' | 'POST' | 'DELETE';

interface RequestOptions {
    method?: HttpMethod;
    body?: unknown;
}

export class ApiError extends Error {
    readonly status: number;

    constructor(status: number) {
        super(`Request failed with status ${status}`);
        this.status = status;
    }
}

function buildInit(options: RequestOptions): RequestInit {
    const { method = 'GET', body } = options;

    if (body === undefined) {
        return {
            method,
            credentials: 'include',
        };
    }

    return {
        method,
        credentials: 'include',
        headers: JSON_HEADERS,
        body: JSON.stringify(body),
    };
}

async function send(url: string, options: RequestOptions): Promise<Response> {
    const response = await fetch(url, buildInit(options));

    if (!response.ok) {
        throw new ApiError(response.status);
    }

    return response;
}

export async function requestJson<T>(url: string, options: RequestOptions = {}): Promise<T> {
    const response = await send(url, options);

    return response.json();
}

export async function requestVoid(url: string, options: RequestOptions = {}): Promise<void> {
    await send(url, options);
}
