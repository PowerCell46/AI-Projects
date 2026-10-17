import './StepError.css';


interface StepErrorProps {
    id: string;
    message: string | null;
}

function StepError({ id, message }: StepErrorProps) {
    return (
        <div
            id={id}
            className="step-error"
            data-visible={message !== null}
            role="alert"
        >
            <div className="step-error-clip">
                {message !== null && (
                    <p className="step-error-message">
                        <span className="step-error-marker" />
                        {message}
                    </p>
                )}
            </div>
        </div>
    );
}

export default StepError;
