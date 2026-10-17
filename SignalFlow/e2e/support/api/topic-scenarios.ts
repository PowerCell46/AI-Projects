import type { APIRequestContext } from '@playwright/test';
import { uniqueCategoryName, uniqueTopicName } from '../data/unique-names';
import { withAdminContext } from './admin-session';
import { createCategory } from './category-api';
import { createInterestTopic } from './topic-api';
import type { NewInterestTopic, TestSchedule } from './topic-api';

export interface TestTopic {
    categoryId: string;
    topicId: string;
    name: string;
}

export interface TestTopicWithCategories extends TestTopic {
    categoryNames: string[];
}

export interface NewTopicOptions {
    name?: string;
    schedule?: TestSchedule;
}

interface TestCategory {
    id: string;
    name: string;
}

async function createTopicInCategory(
    adminContext: APIRequestContext,
    categoryId: string,
    name: string,
    schedule?: TestSchedule,
): Promise<TestTopic> {
    const newTopic: NewInterestTopic = {
        name,
        categoryIds: [categoryId],
        schedule,
    };

    const topicId = await createInterestTopic(adminContext, newTopic);

    return {
        categoryId,
        topicId,
        name,
    };
}

async function createCategoriesInLabelOrder(
    adminContext: APIRequestContext,
    categoryLabels: string[],
): Promise<TestCategory[]> {
    const categories: TestCategory[] = [];

    for (const label of [...categoryLabels].sort()) {
        const name = uniqueCategoryName(label);
        const id = await createCategory(adminContext, name);

        categories.push({
            id,
            name,
        });
    }

    return categories;
}

// Logs in as the seeded admin and creates one category + one topic through the gateway's forwarded
// routes (writes are ADMIN-only). Names are unique per call so parallel tests never see each
// other's topics by accident.
export async function createTopicViaApi(options: NewTopicOptions = {}): Promise<TestTopic> {
    return withAdminContext(async (adminContext) => {
        const categoryId = await createCategory(adminContext, uniqueCategoryName());
        const name = options.name ?? uniqueTopicName();

        return createTopicInCategory(adminContext, categoryId, name, options.schedule);
    });
}

// Creates many topics under one category and one admin session, which avoids repeated admin logins.
export async function createTopicsBatchViaApi(count: number): Promise<TestTopic[]> {
    return withAdminContext(async (adminContext) => {
        const categoryId = await createCategory(adminContext, uniqueCategoryName());
        const topics: TestTopic[] = [];

        for (let index = 0; index < count; index++) {
            const topic = await createTopicInCategory(adminContext, categoryId, uniqueTopicName());

            topics.push(topic);
        }

        return topics;
    });
}

// The ids are sent in reverse name order on purpose, so a test can prove the response and the card
// sort the categories by name, not by the order sent.
export async function createTopicWithCategoriesViaApi(
    categoryLabels: string[],
): Promise<TestTopicWithCategories> {
    return withAdminContext(async (adminContext) => {
        const categories = await createCategoriesInLabelOrder(adminContext, categoryLabels);
        const reversedCategoryIds = categories
            .map((category) => category.id)
            .reverse();

        const newTopic: NewInterestTopic = {
            name: uniqueTopicName(),
            categoryIds: reversedCategoryIds,
        };

        const topicId = await createInterestTopic(adminContext, newTopic);

        return {
            categoryId: categories[0].id,
            topicId,
            name: newTopic.name,
            categoryNames: categories.map((category) => category.name),
        };
    });
}
