import { ApiError } from '../types/problem';
import type { ProblemDetail } from '../types/problem';


// A thin fetch wrapper that sends the httpOnly auth cookie with every request (credentials:
// 'include'), parses RFC-7807 errors into ApiError, and notifies a global handler on 401 so the
// app can clear auth + redirect.

let unauthorizedHandler: () => void = () => { };


export const setUnauthorizedHandler = (handler: () => void): void => {
    unauthorizedHandler = handler;
};


interface RequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    body?: unknown;
    // Auth endpoints set this so a 401 (bad credentials) surfaces as a form error
    // instead of triggering a global logout + redirect.
    suppressUnauthorizedHandler?: boolean;
}


/**
 * Performs a JSON request against the API and returns the parsed body.
 * Throws {@link ApiError} for any non-2xx response.
 */
export const apiRequest = async <TResponse>(
    url: string,
    options: RequestOptions = {},
): Promise<TResponse> => {
    const { method = 'GET', body, suppressUnauthorizedHandler = false } = options;

    const response = await fetch(url, {
        method,
        headers: buildHeaders(body !== undefined),
        credentials: 'include',
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    if (!response.ok) {
        throw await toApiError(response, suppressUnauthorizedHandler);
    }

    return parseBody<TResponse>(response);
};


/**
 * Fetches a binary resource (e.g. an asset image) as a Blob, carrying the bearer token a
 * plain <img> tag can't attach. Throws {@link ApiError} on any non-2xx response.
 */
export const apiRequestBlob = async (url: string): Promise<Blob> => {
    const response = await fetch(url, {
        method: 'GET',
        headers: buildHeaders(false),
        credentials: 'include',
    });

    if (!response.ok) {
        throw await toApiError(response, false);
    }

    return response.blob();
};


const buildHeaders = (hasBody: boolean): HeadersInit => {
    const headers: Record<string, string> = {
        Accept: 'application/json',
    };

    if (hasBody) {
        headers['Content-Type'] = 'application/json';
    }

    return headers;
};


const toApiError = async (response: Response, suppress: boolean): Promise<ApiError> => {
    if (response.status === 401 && !suppress) {
        unauthorizedHandler();
    }

    const problem = await readProblemDetail(response);
    const detail = problem?.detail ?? response.statusText ?? 'Request failed.';

    return new ApiError(response.status, detail, problem?.errors ?? {});
};


const readProblemDetail = async (response: Response): Promise<ProblemDetail | null> => {
    try {
        return (await response.json()) as ProblemDetail;
    } catch {
        return null;
    }
};


const parseBody = async <TResponse>(response: Response): Promise<TResponse> => {
    if (response.status === 204) {
        return undefined as TResponse;
    }

    return (await response.json()) as TResponse;
};
