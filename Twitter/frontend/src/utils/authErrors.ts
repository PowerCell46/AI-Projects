import { stepIndexOfField } from '../flows';
import type { FlowField, FlowName } from '../flows';


export type GaugeAction = 'hold' | 'travel' | 'rise';

export interface AuthErrorScreen {
    stepIndex: number;
    gauge: GaugeAction;
    message: string;
    offersResend: boolean;
}

export const RISE_METRES = 420;

export const SIGNAL_LOST_MESSAGE = 'SIGNAL LOST — TRY AGAIN';

export const INVALID_CREDENTIALS_MESSAGE = `INVALID CREDENTIALS — RISING ${RISE_METRES} M`;

export const CONFIRM_EMAIL_MESSAGE = 'PLEASE CONFIRM YOUR EMAIL FIRST';

const EMAIL_TAKEN_SERVER_MESSAGE = 'Email already registered.';

const USERNAME_TAKEN_SERVER_MESSAGE = 'Username already taken.';

const REGISTER_FIELDS_IN_STEP_ORDER: FlowField[] = ['email', 'username', 'password'];

const STATUS_BAD_REQUEST = 400;

const STATUS_UNAUTHORIZED = 401;

const STATUS_FORBIDDEN = 403;

const STATUS_CONFLICT = 409;

export function formatServerMessage(serverMessage: string): string {
    return serverMessage
        .replace(/\.$/, '')
        .toUpperCase();
}

function signalLost(currentStepIndex: number): AuthErrorScreen {
    return {
        stepIndex: currentStepIndex,
        gauge: 'hold',
        message: SIGNAL_LOST_MESSAGE,
        offersResend: false,
    };
}

function describeConflict(messages: string[], currentStepIndex: number): AuthErrorScreen {
    if (messages.includes(EMAIL_TAKEN_SERVER_MESSAGE)) {
        return {
            stepIndex: stepIndexOfField('register', 'email'),
            gauge: 'travel',
            message: formatServerMessage(EMAIL_TAKEN_SERVER_MESSAGE),
            offersResend: false,
        };
    }

    if (messages.includes(USERNAME_TAKEN_SERVER_MESSAGE)) {
        return {
            stepIndex: stepIndexOfField('register', 'username'),
            gauge: 'travel',
            message: formatServerMessage(USERNAME_TAKEN_SERVER_MESSAGE),
            offersResend: false,
        };
    }

    return signalLost(currentStepIndex);
}

function describeValidationFailure(messages: string[], currentStepIndex: number): AuthErrorScreen {
    // The gateway prefixes every 400 message with the name of the field it rejected.
    for (const field of REGISTER_FIELDS_IN_STEP_ORDER) {
        const fieldMessage = messages.find((message) => message.startsWith(`${field} `));

        if (fieldMessage) {
            return {
                stepIndex: stepIndexOfField('register', field),
                gauge: 'travel',
                message: formatServerMessage(fieldMessage),
                offersResend: false,
            };
        }
    }

    return signalLost(currentStepIndex);
}

function describeRegisterError(status: number, messages: string[], currentStepIndex: number): AuthErrorScreen {
    if (status === STATUS_CONFLICT) {
        return describeConflict(messages, currentStepIndex);
    }

    if (status === STATUS_BAD_REQUEST) {
        return describeValidationFailure(messages, currentStepIndex);
    }

    return signalLost(currentStepIndex);
}

function describeLoginError(status: number, currentStepIndex: number): AuthErrorScreen {
    const passwordStepIndex = stepIndexOfField('login', 'password');

    if (status === STATUS_UNAUTHORIZED) {
        return {
            stepIndex: passwordStepIndex,
            gauge: 'rise',
            message: INVALID_CREDENTIALS_MESSAGE,
            offersResend: false,
        };
    }

    if (status === STATUS_FORBIDDEN) {
        return {
            stepIndex: passwordStepIndex,
            gauge: 'hold',
            message: CONFIRM_EMAIL_MESSAGE,
            offersResend: true,
        };
    }

    return signalLost(currentStepIndex);
}

export function describeAuthError(
    flowName: FlowName,
    currentStepIndex: number,
    status: number,
    messages: string[],
): AuthErrorScreen {
    if (flowName === 'register') {
        return describeRegisterError(status, messages, currentStepIndex);
    }

    if (flowName === 'login') {
        return describeLoginError(status, currentStepIndex);
    }

    return signalLost(currentStepIndex);
}
