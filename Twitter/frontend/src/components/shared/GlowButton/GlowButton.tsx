import type { ButtonHTMLAttributes, Ref } from 'react';
import './GlowButton.css';


interface GlowButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type' | 'children'> {
    label: string;
    isLabelHidden?: boolean;
    ref?: Ref<HTMLButtonElement>;
}

// An outlined button whose panel rises from the bottom on hover. The caller's own class carries its states; a caller
// that names the button with `aria-label` hides the visible label from assistive technology.
function GlowButton({ label, isLabelHidden = false, className = '', ref, ...buttonProps }: GlowButtonProps) {
    return (
        <button type="button" className={`glow-button ${className}`} ref={ref} {...buttonProps}>
            <span className="glow-button-label" aria-hidden={isLabelHidden}>{label}</span>
        </button>
    );
}

export default GlowButton;
