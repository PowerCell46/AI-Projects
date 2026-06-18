// RFC-7807 ProblemDetail as returned by the backend GlobalExceptionHandler, plus the
// typed error the API client throws so callers never inspect raw responses.

export interface ProblemDetail {
    type?: string;
    title?: string;
    status: number;
    detail?: string;
    instance?: string;
    // Present on 400 validation failures: a field-name -> message map.
    errors?: Record<string, string>;
}


export class ApiError extends Error {
    readonly status: number;
    readonly detail: string;
    readonly fieldErrors: Record<string, string>;

    constructor(status: number, detail: string, fieldErrors: Record<string, string> = {}) {
        super(detail);
        this.name = 'ApiError';
        this.status = status;
        this.detail = detail;
        this.fieldErrors = fieldErrors;
    }

    get isUnauthorized(): boolean {
        return this.status === 401;
    }

    get isConflict(): boolean {
        return this.status === 409;
    }
}
