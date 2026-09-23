import { useState } from 'react'
import { subscribe, unsubscribe } from '../../../../api/subscriptions'
import type { FeedTopic } from '../../../../api/feed'
import './TopicCard.css'

interface TopicCardProps {
    topic: FeedTopic
    onChange: (topicId: string, subscribed: boolean) => void
}

function TopicCard({ topic, onChange }: TopicCardProps) {
    const [subscribed, setSubscribed] = useState(topic.subscribed)
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)

    async function handleToggle() {
        if (pending) {
            return
        }

        const next = !subscribed

        setPending(true)
        setError(null)
        setSubscribed(next)

        try {
            if (next) {
                await subscribe(topic.id)
            } else {
                await unsubscribe(topic.id)
            }

            onChange(topic.id, next)
        } catch {
            setSubscribed(!next)
            setError(next ? "Couldn't subscribe. Try again." : "Couldn't unsubscribe. Try again.")
        } finally {
            setPending(false)
        }
    }

    return (
        <article className="topic-card" data-subscribed={subscribed}>
            <div className="topic-card-row">
                <h2 className="topic-card-title">{topic.name}</h2>
                <span className="topic-card-category">{topic.categoryName}</span>
            </div>

            <p className="topic-card-description">{topic.description}</p>

            <button
                type="button"
                className="topic-card-button"
                aria-pressed={subscribed}
                disabled={pending}
                onClick={handleToggle}
            >
                {subscribed ? 'Subscribed' : 'Subscribe'}
            </button>

            {error && <p className="topic-card-error">{error}</p>}
        </article>
    )
}

export default TopicCard
