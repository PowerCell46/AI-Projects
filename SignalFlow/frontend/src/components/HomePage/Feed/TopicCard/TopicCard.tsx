import { useState } from 'react';
import { subscribe, unsubscribe } from '../../../../api/subscriptions';
import type { FeedCategory, FeedSchedule, FeedTopic } from '../../../../api/feed';
import './TopicCard.css';

const SCHEDULE_LABELS: Record<FeedSchedule, string> = {
    DAILY: 'Daily',
    WEEKDAYS: 'Weekdays',
    WEEKENDS: 'Weekends',
    WEEKLY: 'Weekly · Mondays',
};

const MAX_VISIBLE_CATEGORIES = 2;

// Must outlast the longest burst animation in TopicCard.css (950ms), so the data attribute is
// only cleared once the animation has finished.
const BURST_DURATION_MS = 1000;

const SUBSCRIBE_ERROR = "Couldn't subscribe. Try again.";

const UNSUBSCRIBE_ERROR = "Couldn't unsubscribe. Try again.";

type BurstKind = 'subscribe' | 'unsubscribe';

function hiddenCategoryNames(categories: FeedCategory[]): string {
    return categories
        .slice(MAX_VISIBLE_CATEGORIES)
        .map((category) => category.name)
        .join(', ');
}

interface TopicCardProps {
    topic: FeedTopic;
    onChange: (topicId: string, isSubscribed: boolean) => void;
}

function TopicCard({ topic, onChange }: TopicCardProps) {
    const [isSubscribed, setIsSubscribed] = useState(topic.subscribed);
    const [isPending, setIsPending] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [burst, setBurst] = useState<BurstKind | null>(null);

    async function handleToggle() {
        if (isPending) {
            return;
        }

        const willSubscribe = !isSubscribed;

        setIsPending(true);
        setErrorMessage(null);
        setIsSubscribed(willSubscribe);

        setBurst(willSubscribe ? 'subscribe' : 'unsubscribe');
        window.setTimeout(() => setBurst(null), BURST_DURATION_MS);

        try {
            if (willSubscribe) {
                await subscribe(topic.id);

            } else {
                await unsubscribe(topic.id);
            }

            onChange(topic.id, willSubscribe);

        } catch {
            setIsSubscribed(!willSubscribe);
            setErrorMessage(willSubscribe ? SUBSCRIBE_ERROR : UNSUBSCRIBE_ERROR);

        } finally {
            setIsPending(false);
        }
    }

    const scheduleLabel: string | undefined = SCHEDULE_LABELS[topic.schedule];
    const visibleCategories = topic.categories.slice(0, MAX_VISIBLE_CATEGORIES);
    const hiddenCategoryCount = topic.categories.length - MAX_VISIBLE_CATEGORIES;

    return (
        <article className="topic-card" data-subscribed={isSubscribed} data-burst={burst ?? undefined}>
            <h2 className="topic-card-title">{topic.name}</h2>

            <ul className="topic-card-categories">
                {visibleCategories.map((category) => (
                    <li key={category.id} className="topic-card-category" title={category.name}>
                        {category.name}
                    </li>
                ))}

                {hiddenCategoryCount > 0 && (
                    <li
                        className="topic-card-category"
                        data-overflow="true"
                        title={hiddenCategoryNames(topic.categories)}
                    >
                        +{hiddenCategoryCount}
                    </li>
                )}
            </ul>

            {scheduleLabel && <p className="topic-card-schedule">{scheduleLabel}</p>}

            <p className="topic-card-description">{topic.description}</p>

            <div className="topic-card-button-wrap">
                <button
                    type="button"
                    className="topic-card-button"
                    aria-pressed={isSubscribed}
                    disabled={isPending}
                    onClick={handleToggle}
                >
                    {isSubscribed ? 'Subscribed' : 'Subscribe'}
                </button>

                {/* A sibling of the button, so the expanding ring can overflow it. */}
                {burst && <span className="topic-card-ripple" data-kind={burst} aria-hidden="true" />}
            </div>

            {errorMessage && <p className="topic-card-error">{errorMessage}</p>}
        </article>
    );
}

export default TopicCard;
