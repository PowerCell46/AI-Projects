import { useEffect, useRef, useState } from 'react';
import type { RefObject } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../api/http';
import { FLOWS } from '../../../flows';
import type { Flow, FlowField, FlowName, FlowStep } from '../../../flows';
import { describeAuthError, RISE_METRES } from '../../../utils/authErrors';
import type { AuthErrorScreen } from '../../../utils/authErrors';
import { validateField } from '../../../utils/validation';
import { useStepTransition } from './useStepTransition';
import type { StepDirection, StepSlide } from './useStepTransition';


export type StepValues = Record<FlowField, string>;

export interface StepFlowState {
    flow: Flow;
    step: FlowStep;
    stepIndex: number;
    depthMetres: number;
    isLastStep: boolean;
    values: StepValues;
    errorMessage: string | null;
    isAlarm: boolean;
    offersResend: boolean;
    isSubmitting: boolean;
    slide: StepSlide;
    direction: StepDirection;
    activeInputRef: RefObject<HTMLInputElement | null>;
    setValue: (field: FlowField, value: string) => void;
    submit: () => void;
    goBack: () => void;
}

interface StepLocationState {
    stepIndex: number;
}

const AUTOFOCUS_DELAY_MS = 60;

const NETWORK_FAILURE_STATUS = 0;

const EMPTY_VALUES: StepValues = {
    identifier: '',
    email: '',
    username: '',
    password: '',
};

function isStepLocationState(locationState: unknown): locationState is StepLocationState {
    return typeof locationState === 'object'
        && locationState !== null
        && 'stepIndex' in locationState
        && typeof locationState.stepIndex === 'number';
}

function readRequestedStepIndex(locationState: unknown, stepCount: number): number {
    if (!isStepLocationState(locationState)) {
        return 0;
    }

    const { stepIndex } = locationState;

    return Number.isInteger(stepIndex) && stepIndex >= 0 && stepIndex < stepCount ? stepIndex : 0;
}

function describeFailure(flowName: FlowName, stepIndex: number, failure: unknown): AuthErrorScreen {
    if (failure instanceof ApiError) {
        return describeAuthError(flowName, stepIndex, failure.status, failure.messages);
    }

    return describeAuthError(flowName, stepIndex, NETWORK_FAILURE_STATUS, []);
}

export function useStepFlow(
    flowName: FlowName,
    onComplete: (values: StepValues) => void | Promise<void>,
    initialValues: Partial<StepValues> = {},
): StepFlowState {
    const navigate = useNavigate();
    const location = useLocation();
    const flow = FLOWS[flowName];
    const requestedStepIndex = readRequestedStepIndex(location.state, flow.steps.length);
    const { stepIndex, slide, direction } = useStepTransition(requestedStepIndex);
    const [values, setValues] = useState<StepValues>({
        ...EMPTY_VALUES,
        ...initialValues,
    });
    const [stepError, setStepError] = useState<AuthErrorScreen | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const activeInputRef = useRef<HTMLInputElement>(null);

    // A reload keeps the history entry's step but not the typed values, so it restarts at step 1.
    useEffect(() => {
        if (requestedStepIndex !== 0) {
            navigate(
                location.pathname,
                {
                    replace: true,
                    state: null,
                },
            );
        }
    // Mount only: re-running on every step change would reset each step the user advances to.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        const focusTimerId = window.setTimeout(
            () => activeInputRef.current?.focus(),
            AUTOFOCUS_DELAY_MS,
        );

        return () => window.clearTimeout(focusTimerId);
    }, [stepIndex]);

    const step = flow.steps[stepIndex];
    const isLastStep = stepIndex === flow.steps.length - 1;
    const visibleError = stepError?.stepIndex === stepIndex ? stepError : null;
    const isRising = visibleError?.gauge === 'rise';

    function setValue(field: FlowField, value: string) {
        setValues((currentValues) => ({
            ...currentValues,
            [field]: value,
        }));
        setStepError(null);
    }

    function showError(errorScreen: AuthErrorScreen) {
        const stepDelta = errorScreen.stepIndex - stepIndex;

        setStepError(errorScreen);

        // Every step was reached by one history push, so going back n steps is going back n entries.
        if (stepDelta < 0) {
            navigate(stepDelta);
        }
    }

    async function complete() {
        setIsSubmitting(true);

        try {
            await onComplete(values);

        } catch (failure) {
            showError(describeFailure(flowName, stepIndex, failure));

        } finally {
            setIsSubmitting(false);
        }
    }

    function submit() {
        if (slide !== 'idle' || isSubmitting) {
            return;
        }

        setStepError(null);

        const validationMessage = validateField(flowName, step.field, values[step.field]);

        if (validationMessage) {
            showError({
                stepIndex,
                gauge: 'hold',
                message: validationMessage,
                offersResend: false,
            });

        } else if (isLastStep) {
            complete();

        } else {
            const nextLocationState: StepLocationState = { stepIndex: stepIndex + 1 };

            navigate(
                location.pathname,
                { state: nextLocationState },
            );
        }
    }

    function goBack() {
        if (slide === 'idle') {
            setStepError(null);
            navigate(-1);
        }
    }

    return {
        flow,
        step,
        stepIndex,
        depthMetres: isRising ? step.depthMetres - RISE_METRES : step.depthMetres,
        isLastStep,
        values,
        errorMessage: visibleError?.message ?? null,
        isAlarm: isRising,
        offersResend: visibleError?.offersResend ?? false,
        isSubmitting,
        slide,
        direction,
        activeInputRef,
        setValue,
        submit,
        goBack,
    };
}
