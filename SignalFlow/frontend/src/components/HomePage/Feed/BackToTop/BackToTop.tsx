import { useEffect, useState } from 'react';
import { isReducedMotionPreferred } from '../../../../utils/motion';
import './BackToTop.css';

const SCROLL_THRESHOLD_PX = 400;

function BackToTop() {
    const [isVisible, setIsVisible] = useState(false);

    useEffect(() => {
        function updateVisibility() {
            const isScrollable = document.documentElement.scrollHeight > window.innerHeight;
            const isPastThreshold = window.scrollY > SCROLL_THRESHOLD_PX;

            setIsVisible(isScrollable && isPastThreshold);
        }

        updateVisibility();

        window.addEventListener(
            'scroll',
            updateVisibility,
            { passive: true },
        );
        window.addEventListener('resize', updateVisibility);

        return () => {
            window.removeEventListener('scroll', updateVisibility);
            window.removeEventListener('resize', updateVisibility);
        };
    }, []);

    function handleClick() {
        window.scrollTo({
            top: 0,
            behavior: isReducedMotionPreferred() ? 'auto' : 'smooth',
        });
    }

    if (!isVisible) {
        return null;
    }

    return (
        <button
            type="button"
            className="back-to-top"
            aria-label="Back to top"
            onClick={handleClick}
        >
            <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
                <path d="M12 5l-7 7h4v7h6v-7h4z" fill="currentColor" />
            </svg>
        </button>
    );
}

export default BackToTop;
