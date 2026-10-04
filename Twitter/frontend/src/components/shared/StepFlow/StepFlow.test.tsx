import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FlowName } from '../../../flows';
import {
    advance,
    expectedTransform,
    horizonTransform,
    settleTransition,
    waitForHistoryTraversal,
} from '../../../test/stepFlowHelpers';
import { EMPTY_FIELD_MESSAGE, USERNAME_MESSAGE } from '../../../utils/validation';
import DescentStage from '../DescentStage/DescentStage';
import StepFlow from './StepFlow';
import { STEP_SWAP_MS } from './useStepTransition';
import { useStepFlow } from './useStepFlow';
import type { StepValues } from './useStepFlow';


interface HarnessProps {
    flowName: FlowName;
    onComplete?: (values: StepValues) => void;
}

function Harness({ flowName, onComplete = () => {} }: HarnessProps) {
    const stepFlow = useStepFlow(flowName, onComplete);

    return (
        <DescentStage
            depthMetres={stepFlow.depthMetres}
            seafloorDepthMetres={stepFlow.flow.seafloorDepthMetres}
        >
            <StepFlow state={stepFlow} />
        </DescentStage>
    );
}

function renderFlow(flowName: FlowName, onComplete?: (values: StepValues) => void) {
    return render(
        <BrowserRouter>
            <Harness flowName={flowName} onComplete={onComplete} />
        </BrowserRouter>,
    );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    window.history.replaceState(null, '', '/');
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the first step', () => {
    it('should_show_the_first_question_with_its_labelled_input_and_one_filled_bar', () => {
        renderFlow('register');

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
        expect(screen.getByRole('textbox', { name: 'email address' })).toBeTruthy();
        expect(document.querySelectorAll('.step-eyebrow-bar[data-filled="true"]')).toHaveLength(1);
        expect(document.querySelectorAll('.step-eyebrow-bar')).toHaveLength(3);
    });

    it('should_not_show_a_back_button_on_the_first_step', () => {
        renderFlow('register');

        expect(screen.queryByRole('button', { name: 'BACK' })).toBeNull();
    });
});

describe('advancing', () => {
    it('should_advance_when_enter_is_pressed_and_swap_the_content_after_the_exit', async () => {
        renderFlow('register');

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await advance(STEP_SWAP_MS - 1);

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();

        await settleTransition();

        expect(screen.getByRole('heading', { name: 'Pick the name you\'ll be known by.' })).toBeTruthy();
        expect(document.querySelectorAll('.step-eyebrow-bar[data-filled="true"]')).toHaveLength(2);
    });

    it('should_advance_when_the_primary_button_is_clicked', async () => {
        renderFlow('register');

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com',
        );
        await user.click(screen.getByRole('button', { name: 'CONTINUE' }));
        await settleTransition();

        expect(screen.getByRole('textbox', { name: 'username' })).toBeTruthy();
    });

    it('should_start_the_horizon_travel_only_when_the_content_swaps', async () => {
        renderFlow('register');
        const restingTransform = expectedTransform(140, 10910);

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await advance(STEP_SWAP_MS - 1);

        expect(horizonTransform()).toBe(restingTransform);

        await advance(1);

        expect(horizonTransform()).toBe(expectedTransform(3860, 10910));
    });

    it('should_focus_the_new_field_60ms_after_the_swap', async () => {
        renderFlow('register');

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await advance(STEP_SWAP_MS);
        await advance(59);

        expect(document.activeElement).not.toBe(screen.getByRole('textbox', { name: 'username' }));

        await advance(1);

        expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'username' }));
    });

    it('should_announce_the_step_in_the_live_region', async () => {
        renderFlow('register');
        const liveRegion = document.querySelector('[aria-live="polite"]');

        expect(liveRegion?.textContent).toBe('Step 1 of 3, Identity.');

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await settleTransition();

        expect(liveRegion?.textContent).toBe('Step 2 of 3, Handle.');
    });

    it('should_call_onComplete_with_every_value_when_the_last_step_is_submitted', async () => {
        const onComplete = vi.fn();
        renderFlow('login', onComplete);

        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();
        await user.type(screen.getByLabelText('password'), 'secret{Enter}');

        expect(onComplete).toHaveBeenCalledWith({
            identifier: 'peter_g',
            email: '',
            username: '',
            password: 'secret',
        });
    });
});

describe('going back', () => {
    async function advanceToUsernameStep() {
        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await settleTransition();
    }

    it('should_step_back_with_the_values_kept_when_the_back_button_is_clicked', async () => {
        renderFlow('register');
        await advanceToUsernameStep();

        await user.click(screen.getByRole('button', { name: 'BACK' }));
        await waitForHistoryTraversal();
        await settleTransition();

        const emailInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'email address' });

        expect(emailInput.value).toBe('peter@example.com');
        expect(horizonTransform()).toBe(expectedTransform(140, 10910));
    });

    it('should_step_back_when_the_browser_goes_back', async () => {
        renderFlow('register');
        await advanceToUsernameStep();

        act(() => {
            window.history.back();
        });
        await waitForHistoryTraversal();
        await settleTransition();

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
    });

    it('should_restart_at_the_first_step_when_the_page_is_reloaded_on_a_later_step', async () => {
        window.history.replaceState(
            {
                usr: { stepIndex: 2 },
                key: 'reload',
                idx: 0,
            },
            '',
            '/',
        );

        renderFlow('register');
        await settleTransition();

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
        expect(window.history.state.usr).toBeNull();
    });
});

describe('errors', () => {
    it('should_hold_at_depth_with_the_empty_message_when_the_field_is_empty', async () => {
        renderFlow('register');

        await user.click(screen.getByRole('button', { name: 'CONTINUE' }));
        await settleTransition();

        expect(screen.getByRole('alert').textContent).toBe(EMPTY_FIELD_MESSAGE);
        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
        expect(horizonTransform()).toBe(expectedTransform(140, 10910));
    });

    it('should_hold_at_the_username_step_with_the_username_message_when_the_username_is_too_short', async () => {
        renderFlow('register');
        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'peter@example.com{Enter}',
        );
        await settleTransition();

        await user.type(
            screen.getByRole('textbox', { name: 'username' }),
            'al{Enter}',
        );
        await settleTransition();

        expect(screen.getByRole('alert').textContent).toBe(USERNAME_MESSAGE);
        expect(screen.getByRole('textbox', { name: 'username' }).getAttribute('aria-invalid')).toBe('true');
        expect(horizonTransform()).toBe(expectedTransform(3860, 10910));
    });

    it('should_clear_the_error_on_the_next_keystroke', async () => {
        renderFlow('register');
        await user.click(screen.getByRole('button', { name: 'CONTINUE' }));

        await user.type(
            screen.getByRole('textbox', { name: 'email address' }),
            'p',
        );

        expect(screen.getByRole('alert').textContent).toBe('');
        expect(screen.getByRole('textbox', { name: 'email address' }).getAttribute('aria-invalid')).toBe('false');
    });
});

describe('inputs', () => {
    it('should_keep_the_identifier_input_mounted_with_autocomplete_username_on_the_password_step', async () => {
        renderFlow('login');
        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();

        const identifierInput = document.querySelector('input[name="identifier"]');

        expect(identifierInput?.getAttribute('autocomplete')).toBe('username');
        expect(identifierInput?.closest('[hidden]')).not.toBeNull();
        expect(screen.getByLabelText('password').getAttribute('autocomplete')).toBe('current-password');
    });

    it('should_give_each_register_field_the_matching_autocomplete_value', () => {
        renderFlow('register');

        const autoCompleteByName = Array.from(document.querySelectorAll('input'))
            .map((input) => [input.name, input.autocomplete]);

        expect(autoCompleteByName).toEqual([
            ['email', 'email'],
            ['username', 'username'],
            ['password', 'new-password'],
        ]);
    });
});

describe('password toggle', () => {
    async function goToPasswordStep() {
        renderFlow('login');
        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();
    }

    it('should_offer_the_toggle_only_on_the_password_step', async () => {
        renderFlow('login');

        expect(screen.queryByRole('button', { name: 'Show password' })).toBeNull();

        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();

        expect(screen.getByRole('button', { name: 'Show password' }).getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_the_password_as_text_when_the_toggle_is_pressed_and_hide_it_again_on_the_second_press', async () => {
        await goToPasswordStep();
        await user.type(screen.getByLabelText('password'), 'secret');

        await user.click(screen.getByRole('button', { name: 'Show password' }));

        expect(screen.getByLabelText('password').getAttribute('type')).toBe('text');
        expect(screen.getByDisplayValue('secret')).toBe(screen.getByLabelText('password'));
        expect(screen.getByRole('button', { name: 'Hide password' }).getAttribute('aria-pressed')).toBe('true');

        await user.click(screen.getByRole('button', { name: 'Hide password' }));

        expect(screen.getByLabelText('password').getAttribute('type')).toBe('password');
    });

    it('should_not_submit_the_step_when_the_toggle_is_pressed', async () => {
        const onComplete = vi.fn();
        renderFlow('login', onComplete);
        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();
        await user.type(screen.getByLabelText('password'), 'secret');

        await user.click(screen.getByRole('button', { name: 'Show password' }));

        expect(onComplete).not.toHaveBeenCalled();
    });
});
