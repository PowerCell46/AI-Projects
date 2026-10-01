import type { FormEvent, ReactNode } from 'react';
import StepError from './StepError/StepError';
import StepEyebrow from './StepEyebrow/StepEyebrow';
import StepInput from './StepInput/StepInput';
import type { StepFlowState } from './useStepFlow';
import './StepFlow.css';


const ERROR_ID = 'step-flow-error';

const NEXT_STEP_LABEL = 'CONTINUE';

const BACK_LABEL = 'BACK';

interface StepFlowProps {
    state: StepFlowState;
    errorAction?: ReactNode;
}

function describeStep(stepIndex: number, stepCount: number, label: string): string {
    const capitalizedLabel = label.charAt(0) + label.slice(1).toLowerCase();

    return `Step ${stepIndex + 1} of ${stepCount}, ${capitalizedLabel}.`;
}

function StepFlow({ state, errorAction }: StepFlowProps) {
    const { flow, step, stepIndex, isLastStep, values, errorMessage, isSubmitting, slide, direction } = state;

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        state.submit();
    }

    return (
        <form className="step-flow" noValidate onSubmit={handleSubmit}>
            <div className="step-flow-block" data-slide={slide} data-direction={direction}>
                <StepEyebrow label={step.label} stepIndex={stepIndex} stepCount={flow.steps.length} />
                <h1 className="step-flow-question">{step.question}</h1>
                {flow.steps.map((flowStep, flowStepIndex) => (
                    <StepInput
                        key={flowStep.field}
                        step={flowStep}
                        value={values[flowStep.field]}
                        isActive={flowStepIndex === stepIndex}
                        isInvalid={errorMessage !== null && flowStepIndex === stepIndex}
                        errorId={ERROR_ID}
                        inputRef={state.activeInputRef}
                        onChange={state.setValue}
                    />
                ))}
                <StepError id={ERROR_ID} message={errorMessage} />
                {errorMessage !== null && errorAction}
                <div className="step-flow-actions">
                    <button type="submit" className="step-flow-primary" aria-disabled={isSubmitting}>
                        {isLastStep ? flow.submitLabel : NEXT_STEP_LABEL}
                    </button>
                    <button
                        type="button"
                        className="step-flow-back"
                        hidden={stepIndex === 0}
                        onClick={state.goBack}
                    >
                        {BACK_LABEL}
                    </button>
                </div>
            </div>
            <p className="sr-only" aria-live="polite">
                {describeStep(stepIndex, flow.steps.length, step.label)}
            </p>
        </form>
    );
}

export default StepFlow;
