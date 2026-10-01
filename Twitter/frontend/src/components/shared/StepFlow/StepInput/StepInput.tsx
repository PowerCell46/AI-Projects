import type { ChangeEvent, RefObject } from 'react';
import type { FlowField, FlowStep } from '../../../../flows';
import './StepInput.css';


interface StepInputProps {
    step: FlowStep;
    value: string;
    isActive: boolean;
    isInvalid: boolean;
    errorId: string;
    inputRef: RefObject<HTMLInputElement | null>;
    onChange: (field: FlowField, value: string) => void;
}

function StepInput({ step, value, isActive, isInvalid, errorId, inputRef, onChange }: StepInputProps) {
    const inputId = `step-input-${step.field}`;

    function handleChange(event: ChangeEvent<HTMLInputElement>) {
        onChange(step.field, event.target.value);
    }

    return (
        <div className="step-input" data-invalid={isInvalid} hidden={!isActive}>
            <label className="sr-only" htmlFor={inputId}>{step.placeholder}</label>
            <input
                ref={isActive ? inputRef : undefined}
                id={inputId}
                className="step-input-field"
                type={step.inputType}
                name={step.field}
                autoComplete={step.autoComplete}
                placeholder={step.placeholder}
                value={value}
                aria-invalid={isInvalid}
                aria-describedby={isInvalid ? errorId : undefined}
                spellCheck={false}
                autoCapitalize="none"
                onChange={handleChange}
            />
            <span className="step-input-rule" />
        </div>
    );
}

export default StepInput;
