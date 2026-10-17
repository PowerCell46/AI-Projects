import { useState } from 'react';
import type { ChangeEvent, RefObject } from 'react';
import type { FlowField, FlowStep } from '../../../../flows';
import PasswordToggle from './PasswordToggle/PasswordToggle';
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
    const [isRevealed, setIsRevealed] = useState(false);

    function handleChange(event: ChangeEvent<HTMLInputElement>) {
        onChange(step.field, event.target.value);
    }

    function handleToggleReveal() {
        setIsRevealed((wasRevealed) => !wasRevealed);
    }

    const inputId = `step-input-${step.field}`;
    const isPassword = step.inputType === 'password';

    return (
        <div
            className="step-input"
            data-invalid={isInvalid}
            data-has-toggle={isPassword}
            hidden={!isActive}
        >
            <label className="step-input-label" htmlFor={inputId}>{step.placeholder}</label>
            <input
                id={inputId}
                name={step.field}
                type={isRevealed ? 'text' : step.inputType}
                className="step-input-field"
                aria-invalid={isInvalid}
                aria-describedby={isInvalid ? errorId : undefined}
                autoComplete={step.autoComplete}
                placeholder={step.placeholder}
                value={value}
                spellCheck={false}
                autoCapitalize="none"
                ref={isActive ? inputRef : undefined}
                onChange={handleChange}
            />
            {isPassword && (
                <PasswordToggle
                    isRevealed={isRevealed}
                    onToggle={handleToggleReveal}
                />
            )}
            <span className="step-input-rule" />
        </div>
    );
}

export default StepInput;
