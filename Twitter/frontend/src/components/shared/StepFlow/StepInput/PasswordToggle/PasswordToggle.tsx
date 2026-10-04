import './PasswordToggle.css';


interface PasswordToggleProps {
    isRevealed: boolean;
    onToggle: () => void;
}

function PasswordToggle({ isRevealed, onToggle }: PasswordToggleProps) {
    return (
        <button
            type="button"
            className="password-toggle"
            aria-label={isRevealed ? 'Hide password' : 'Show password'}
            aria-pressed={isRevealed}
            onClick={onToggle}
        >
            <svg className="password-toggle-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z" />
                <circle cx="12" cy="12" r="3" />
                <path className="password-toggle-slash" d="M4 4l16 16" />
            </svg>
        </button>
    );
}

export default PasswordToggle;
