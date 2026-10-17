// The frontend source is not modified for tests, so these follow its existing class names.
export const SELECTORS = {
    activeAuthForm: '.auth-form-wrap[aria-hidden="false"]',
    homeWordmark: '.home-wordmark',
    feedState: '.feed-state',
    feedRetry: '.feed-retry',
    pagerControl: '.pager-control',
    topicCard: '.topic-card',
    topicCardTitle: '.topic-card-title',
    topicCardCategory: '.topic-card-category',
    topicCardSchedule: '.topic-card-schedule',
} as const;
