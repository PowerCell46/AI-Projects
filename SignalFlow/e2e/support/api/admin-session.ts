import { request as playwrightRequest } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { FRONTEND_URL } from '../constants';
import { ADMIN_USER } from '../data/test-user';
import { ENDPOINTS } from '../endpoints';
import { postOrThrow } from './request';

// Each call gets its own context, so admin session cookies never leak into a test user's `request` fixture.
export async function withAdminContext<T>(
    action: (adminContext: APIRequestContext) => Promise<T>,
): Promise<T> {
    const adminContext = await playwrightRequest.newContext({ baseURL: FRONTEND_URL });

    try {
        await postOrThrow(adminContext, ENDPOINTS.auth.login, ADMIN_USER);

        return await action(adminContext);

    } finally {
        await adminContext.dispose();
    }
}
