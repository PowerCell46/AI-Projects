import { randomUUID } from 'node:crypto';

const CATEGORY_SUFFIX_LENGTH = 8;

// The topic service caps category names at 40 characters, so keep the random suffix short. A
// distinct label per category makes the names sort in label order.
export function uniqueCategoryName(label = 'cat'): string {
    return `e2e-${label}-${randomUUID().slice(0, CATEGORY_SUFFIX_LENGTH)}`;
}

export function uniqueTopicName(): string {
    return `e2e-topic-${randomUUID()}`;
}
