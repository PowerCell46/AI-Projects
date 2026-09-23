import './BackToTop.css'

function BackToTop() {
    function handleClick() {
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

        window.scrollTo({
            top: 0,
            behavior: reduceMotion ? 'auto' : 'smooth',
        })
    }

    return (
        <button type="button" className="back-to-top" onClick={handleClick} aria-label="Back to top">
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
                <path d="M12 5l-7 7h4v7h6v-7h4z" fill="currentColor" />
            </svg>
        </button>
    )
}

export default BackToTop
