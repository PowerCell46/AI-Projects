import type { AuthMode } from '../../types/auth';


// All mode-specific terminal copy in one place (see AUTH_DESIGN.md "Copy").

export interface AuthModeCopy {
    eyebrow: string;
    heading: string;
    submitLabel: string;
    switchPrompt: string;
    switchAction: string;
}


export const AUTH_COPY: Record<AuthMode, AuthModeCopy> = {
    login: {
        eyebrow: '>> LOG ON',
        heading: 'identify_self',
        submitLabel: '[ LOG ON ]',
        switchPrompt: 'NO RECORD ON FILE?',
        switchAction: 'enroll',
    },
    register: {
        eyebrow: '>> NEW USER',
        heading: 'enroll_new',
        submitLabel: '[ ENROLL ]',
        switchPrompt: 'RECORD EXISTS?',
        switchAction: 'log on',
    },
};
