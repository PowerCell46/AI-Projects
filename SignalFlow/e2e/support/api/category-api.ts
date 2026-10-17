import type { APIRequestContext } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { postOrThrow } from './request';
import type { CreatedResource } from './request';

export async function createCategory(adminContext: APIRequestContext, name: string): Promise<string> {
    const category = await postOrThrow<CreatedResource>(
        adminContext,
        ENDPOINTS.categories,
        { name },
    );

    return category.id;
}
