// Mirrors the backend PageResponse<T> envelope (see dtos/PageResponse.java). A stable,
// explicit pagination shape rather than Spring Data's Page JSON.

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
