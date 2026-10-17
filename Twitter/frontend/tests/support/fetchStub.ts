import { afterEach, beforeEach, vi } from 'vitest';


export const fetchMock = vi.fn<typeof fetch>();

export function respondWith(status: number, body?: object): void {
    const response = new Response(
        body ? JSON.stringify(body) : null,
        { status },
    );

    fetchMock.mockResolvedValueOnce(response);
}

// Call once at the top of a test file: every test starts with a fresh stub and no leftover global.
export function installFetchStub(): void {
    beforeEach(() => {
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        fetchMock.mockReset();
        vi.unstubAllGlobals();
    });
}
