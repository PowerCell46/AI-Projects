export interface Identified {
    id: string;
}

export interface Page<T extends Identified> {
    items: T[];
    nextCursor: string | null;
}

export interface PageRequest {
    cursor: string | null;
    size: number;
}

export function pageUrl(listUrl: string, request: PageRequest): string {
    const params = new URLSearchParams({ size: String(request.size) });

    if (request.cursor) {
        params.set('cursor', request.cursor);
    }

    return `${listUrl}?${params.toString()}`;
}
