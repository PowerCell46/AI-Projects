import type { APIRequestContext } from '@playwright/test';
import { ENDPOINTS } from '../endpoints';
import { postOrThrow } from './request';
import type { CreatedResource } from './request';

const TOPIC_PROMPT = 'e2e prompt';

export type TestSchedule = 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'WEEKLY';

export interface NewInterestTopic {
    name: string;
    categoryIds: string[];
    schedule?: TestSchedule;
}

interface InterestTopicBody extends NewInterestTopic {
    description: string;
    prompt: string;
}

export async function createInterestTopic(
    adminContext: APIRequestContext,
    newTopic: NewInterestTopic,
): Promise<string> {
    const body: InterestTopicBody = {
        ...newTopic,
        description: `e2e description for ${newTopic.name}`,
        prompt: TOPIC_PROMPT,
    };

    const topic = await postOrThrow<CreatedResource>(adminContext, ENDPOINTS.interestTopics, body);

    return topic.id;
}
