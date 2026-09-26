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
    const [burst, setBurst] = useState<'subscribe' | 'unsubscribe' | null>(null)

    async function handleToggle() {
        if (pending) {
            return
        }

        const next = !subscribed

        setPending(true)
        setError(null)
        setSubscribed(next)

        // Trigger the one-shot animation
        setBurst(next ? 'subscribe' : 'unsubscribe')
        setTimeout(() => setBurst(null), 1000)

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
        <article
            className="topic-card"
            data-subscribed={subscribed}
            data-burst={burst ?? undefined}
        >
            <div className="topic-card-row">
                <h2 className="topic-card-title">{topic.name}</h2>
                <span className="topic-card-category">{topic.categoryName}</span>
            </div>

            <p className="topic-card-description">{topic.description}</p>

            <div className="topic-card-button-wrap">
                <button
                    type="button"
                    className="topic-card-button"
                    aria-pressed={subscribed}
                    disabled={pending}
                    onClick={handleToggle}
                >
                    {subscribed ? 'Subscribed' : 'Subscribe'}
                </button>
                {/* Ripple ring — rendered as a sibling so it can overflow the button */}
                {burst && (
                    <span className="topic-card-ripple" data-kind={burst} aria-hidden="true" />
                )}
            </div>

            {error && <p className="topic-card-error">{error}</p>}
        </article>
    )
}

export default TopicCard
