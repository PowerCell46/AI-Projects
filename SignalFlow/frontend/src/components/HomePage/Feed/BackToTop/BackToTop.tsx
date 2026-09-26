import { useEffect, useState } from 'react'
import './BackToTop.css'

const SCROLL_THRESHOLD = 400

function BackToTop() {
    const [visible, setVisible] = useState(false)

    useEffect(() => {
        function updateVisibility() {
            const scrollable = document.documentElement.scrollHeight > window.innerHeight
            const pastThreshold = window.scrollY > SCROLL_THRESHOLD

            setVisible(scrollable && pastThreshold)
        }

        updateVisibility()

        window.addEventListener(
            'scroll',
            updateVisibility,
            { passive: true },
        )
        window.addEventListener(
            'resize',
            updateVisibility,
        )

        return () => {
            window.removeEventListener(
                'scroll',
                updateVisibility,
            )
            window.removeEventListener(
                'resize',
                updateVisibility,
            )
        }
    }, [])

    function handleClick() {
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

        window.scrollTo({
            top: 0,
            behavior: reduceMotion ? 'auto' : 'smooth',
        })
    }

    if (!visible) {
        return null
    }

    return (
        <button type="button" className="back-to-top" onClick={handleClick} aria-label="Back to top">
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="M12 5l-7 7h4v7h6v-7h4z" fill="currentColor" />
            </svg>
        </button>
    )
}

export default BackToTop
