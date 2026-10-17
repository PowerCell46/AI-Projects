import type { APIRequestContext } from '@playwright/test';

export interface CreatedResource {
    id: string;
}

export async function postOrThrow<T = unknown>(
    request: APIRequestContext,
    path: string,
    data: unknown,
): Promise<T> {
    const response = await request.post(
        path,
        { data },
    );

    if (!response.ok()) {
        const body = await response.text();

        throw new Error(`POST ${path} failed with ${response.status()}: ${body}`);
    }

    return response.json();
}
