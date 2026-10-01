import './StepEyebrow.css';


interface StepEyebrowProps {
    label: string;
    stepIndex: number;
    stepCount: number;
}

function StepEyebrow({ label, stepIndex, stepCount }: StepEyebrowProps) {
    const barIndexes = Array.from({ length: stepCount }, (_, barIndex) => barIndex);

    return (
        <p className="step-eyebrow">
            <span className="step-eyebrow-bars" aria-hidden="true">
                {barIndexes.map((barIndex) => (
                    <span
                        key={barIndex}
                        className="step-eyebrow-bar"
                        data-filled={barIndex <= stepIndex}
                    />
                ))}
            </span>
            <span className="step-eyebrow-label">{label}</span>
        </p>
    );
}

export default StepEyebrow;
