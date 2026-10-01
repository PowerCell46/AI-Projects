export type FlowName = 'login' | 'register' | 'resend';

export type FlowField = 'identifier' | 'email' | 'username' | 'password';

export interface FlowStep {
    field: FlowField;
    label: string;
    question: string;
    placeholder: string;
    inputType: 'text' | 'email' | 'password';
    autoComplete: string;
    depthMetres: number;
}

export interface Flow {
    steps: FlowStep[];
    submitLabel: string;
    seafloorDepthMetres: number;
}

const IDENTITY_DEPTH_METRES = 140;

export const FLOWS: Record<FlowName, Flow> = {
    login: {
        steps: [
            {
                field: 'identifier',
                label: 'IDENTITY',
                question: 'Who are you out here?',
                placeholder: 'email or username',
                inputType: 'text',
                autoComplete: 'username',
                depthMetres: IDENTITY_DEPTH_METRES,
            },
            {
                field: 'password',
                label: 'KEY',
                question: 'And the password.',
                placeholder: 'password',
                inputType: 'password',
                autoComplete: 'current-password',
                depthMetres: 3860,
            },
        ],
        submitLabel: 'LOG IN',
        seafloorDepthMetres: 4900,
    },
    register: {
        steps: [
            {
                field: 'email',
                label: 'IDENTITY',
                question: 'Where do we reach you?',
                placeholder: 'email address',
                inputType: 'email',
                autoComplete: 'email',
                depthMetres: IDENTITY_DEPTH_METRES,
            },
            {
                field: 'username',
                label: 'HANDLE',
                question: "Pick the name you'll be known by.",
                placeholder: 'username',
                inputType: 'text',
                autoComplete: 'username',
                depthMetres: 3860,
            },
            {
                field: 'password',
                label: 'KEY',
                question: 'Now seal it.',
                placeholder: 'password',
                inputType: 'password',
                autoComplete: 'new-password',
                depthMetres: 9720,
            },
        ],
        submitLabel: 'CREATE ACCOUNT',
        seafloorDepthMetres: 10910,
    },
    resend: {
        steps: [
            {
                field: 'email',
                label: 'IDENTITY',
                question: 'Where should we send it?',
                placeholder: 'email address',
                inputType: 'email',
                autoComplete: 'email',
                depthMetres: IDENTITY_DEPTH_METRES,
            },
        ],
        submitLabel: 'SEND LINK',
        seafloorDepthMetres: 4900,
    },
};

export function stepIndexOfField(flowName: FlowName, field: FlowField): number {
    return FLOWS[flowName].steps.findIndex((step) => step.field === field);
}
