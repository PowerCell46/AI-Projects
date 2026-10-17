import type { APIRequestContext } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { newUser } from '../data/test-user';
import type { TestUser } from '../data/test-user';
import { postOrThrow } from './request';

export async function registerViaApi(request: APIRequestContext, user: TestUser): Promise<void> {
    await postOrThrow(request, ENDPOINTS.auth.register, user);
}

export async function registerNewUser(request: APIRequestContext): Promise<TestUser> {
    const user = newUser();

    await registerViaApi(request, user);

    return user;
}
