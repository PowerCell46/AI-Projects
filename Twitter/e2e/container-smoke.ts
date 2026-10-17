// Smoke checks for the production frontend container: nginx serving the built SPA and proxying
// /api to the gateway. The specs run against the vite dev server instead, so without these a typo
// in frontend/nginx/ would only break on deploy.

const FRONTEND_CONTAINER_URL = `http://127.0.0.1:${process.env.E2E_FRONTEND_CONTAINER_PORT ?? '5274'}`;

const SMOKE_PATHS = {
    home: '/',
    spaFallback: '/users/ana',
    currentUser: '/api/v1/auth/me',
    register: '/api/v1/auth/register',
} as const;

const REQUEST_TIMEOUT_MS = 10_000;

const BODY_PREVIEW_LENGTH = 200;

// Above nginx's 1 MB default: the body only reaches the gateway when the raised client_max_body_size
// applies, and the gateway's own 8 KB cap then rejects it as JSON.
const OVERSIZED_BODY_BYTES = 2_000_000;

const SPA_ROOT_MARKER = '<div id="root"></div>';

interface SmokeExpectation {
    status: number;
    contentType: string;
    bodyMarker?: string;
}

async function checkSmoke(
    name: string,
    path: string,
    expectation: SmokeExpectation,
    body?: string,
): Promise<Response> {
    const response = await fetch(`${FRONTEND_CONTAINER_URL}${path}`, {
        method: body === undefined ? 'GET' : 'POST',
        body,
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    const responseBody = await response.text();
    const contentType = response.headers.get('content-type') ?? '';

    if (response.status !== expectation.status || !contentType.includes(expectation.contentType)) {
        throw new Error(
            `Frontend container smoke check '${name}' failed: ` +
            `expected ${expectation.status} ${expectation.contentType}, ` +
            `got ${response.status} ${contentType} for ${path} (${responseBody.slice(0, BODY_PREVIEW_LENGTH)})`,
        );
    }

    if (expectation.bodyMarker !== undefined && !responseBody.includes(expectation.bodyMarker)) {
        throw new Error(
            `Frontend container smoke check '${name}' failed: ` +
            `${path} is missing '${expectation.bodyMarker}' (${responseBody.slice(0, BODY_PREVIEW_LENGTH)})`,
        );
    }

    return response;
}

export async function smokeCheckFrontendContainer(): Promise<void> {
    const homePage = await checkSmoke('spa-serves', SMOKE_PATHS.home, {
        status: 200,
        contentType: 'text/html',
        bodyMarker: SPA_ROOT_MARKER,
    });

    if (homePage.headers.get('content-security-policy') === null) {
        throw new Error(`Frontend container smoke check 'security-headers' failed: no content-security-policy on ${SMOKE_PATHS.home}.`);
    }

    await checkSmoke('spa-fallback', SMOKE_PATHS.spaFallback, {
        status: 200,
        contentType: 'text/html',
        bodyMarker: SPA_ROOT_MARKER,
    });

    // Anonymous: the gateway answers 401 as JSON. An nginx 404 page would be HTML instead.
    await checkSmoke('api-proxy', SMOKE_PATHS.currentUser, {
        status: 401,
        contentType: 'application/json',
    });

    await checkSmoke(
        'upload-cap',
        SMOKE_PATHS.register,
        {
            status: 413,
            contentType: 'application/json',
        },
        'x'.repeat(OVERSIZED_BODY_BYTES),
    );
}
