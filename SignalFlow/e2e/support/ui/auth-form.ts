import type { Locator, Page } from '@playwright/test';
import { ROUTES } from '../routes';
import { SELECTORS } from '../selectors';

export type AuthMode = 'login' | 'register';

const SUBMIT_LABELS: Record<AuthMode, string> = {
    login: 'Sign in',
    register: 'Create account',
};

const EXACT = { exact: true };

// The auth page renders both forms with the inactive one inert + aria-hidden;
// scope to the visible form so selectors never match the hidden twin.
function activeAuthForm(page: Page): Locator {
    return page.locator(SELECTORS.activeAuthForm);
}

function authField(form: Locator, label: string): Locator {
    return form.getByLabel(label, EXACT);
}

function submitButton(form: Locator, mode: AuthMode): Locator {
    return form.getByRole(
        'button',
        {
            name: SUBMIT_LABELS[mode],
            exact: true,
        },
    );
}

async function openAuthPage(page: Page, mode: AuthMode): Promise<Locator> {
    await page.goto(ROUTES[mode]);

    return activeAuthForm(page);
}

export function authAlert(form: Locator): Locator {
    return form.getByRole('alert');
}

export async function submitAuthForm(
    page: Page,
    mode: AuthMode,
    email: string,
    password: string,
): Promise<Locator> {
    const form = await openAuthPage(page, mode);

    await authField(form, 'Email').fill(email);
    await authField(form, 'Password').fill(password);
    await submitButton(form, mode).click();

    return form;
}

export async function submitEmptyAuthForm(page: Page, mode: AuthMode): Promise<Locator> {
    const form = await openAuthPage(page, mode);

    await submitButton(form, mode).click();

    return form;
}
