import './LockedField.css';


interface LockedFieldProps {
    label: string;
    value: string;
    reason: string;
}

// Shown so a user who looks for it finds it and sees why it cannot change; the tag is visual, the reason is read aloud.
function LockedField({ label, value, reason }: LockedFieldProps) {
    return (
        <div className="locked-field">
            <p className="locked-field-label">
                {label}
                <span className="locked-field-tag" aria-hidden="true">LOCKED</span>
                <span className="sr-only">{reason}</span>
            </p>
            <p className="locked-field-value">{value}</p>
        </div>
    );
}

export default LockedField;
