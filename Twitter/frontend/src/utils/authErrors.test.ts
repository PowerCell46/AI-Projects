import { describe, expect, it } from 'vitest';
import { describeAuthError, formatServerMessage } from './authErrors';


describe('formatServerMessage', () => {
    it('should_uppercase_the_message_and_drop_the_trailing_period', () => {
        expect(formatServerMessage('Email already registered.')).toBe('EMAIL ALREADY REGISTERED');
    });
});

describe('describeAuthError', () => {
    describe('register', () => {
        it('should_travel_to_the_email_step_when_the_email_is_taken', () => {
            expect(describeAuthError('register', 2, 409, ['Email already registered.'])).toEqual({
                stepIndex: 0,
                gauge: 'travel',
                message: 'EMAIL ALREADY REGISTERED',
                offersResend: false,
            });
        });

        it('should_travel_to_the_username_step_when_the_username_is_taken', () => {
            expect(describeAuthError('register', 2, 409, ['Username already taken.'])).toEqual({
                stepIndex: 1,
                gauge: 'travel',
                message: 'USERNAME ALREADY TAKEN',
                offersResend: false,
            });
        });

        it('should_travel_to_the_earliest_failing_field_when_the_request_is_invalid', () => {
            const messages = [
                'password must contain a lowercase letter, an uppercase letter and a digit',
                'username must be 3 to 15 letters, digits or underscores',
            ];

            expect(describeAuthError('register', 2, 400, messages)).toEqual({
                stepIndex: 1,
                gauge: 'travel',
                message: 'USERNAME MUST BE 3 TO 15 LETTERS, DIGITS OR UNDERSCORES',
                offersResend: false,
            });
        });

        it('should_hold_with_signal_lost_when_a_400_names_no_known_field', () => {
            expect(describeAuthError('register', 2, 400, ['something odd'])).toEqual({
                stepIndex: 2,
                gauge: 'hold',
                message: 'SIGNAL LOST — TRY AGAIN',
                offersResend: false,
            });
        });

        it('should_hold_with_signal_lost_when_a_409_carries_an_unknown_message', () => {
            expect(describeAuthError('register', 2, 409, ['Something else.']).message).toBe('SIGNAL LOST — TRY AGAIN');
        });
    });

    describe('login', () => {
        it('should_rise_420_metres_on_the_password_step_when_the_credentials_are_wrong', () => {
            expect(describeAuthError('login', 1, 401, [])).toEqual({
                stepIndex: 1,
                gauge: 'rise',
                message: 'INVALID CREDENTIALS — RISING 420 M',
                offersResend: false,
            });
        });

        it('should_hold_and_offer_a_resend_when_the_account_is_unconfirmed', () => {
            expect(describeAuthError('login', 1, 403, [])).toEqual({
                stepIndex: 1,
                gauge: 'hold',
                message: 'PLEASE CONFIRM YOUR EMAIL FIRST',
                offersResend: true,
            });
        });
    });

    describe('any flow', () => {
        it.each([
            ['login', 0],
            ['register', 1],
            ['resend', 0],
        ] as const)('should_hold_with_signal_lost_on_a_network_failure_in_%s', (flowName, currentStepIndex) => {
            expect(describeAuthError(flowName, currentStepIndex, 0, [])).toEqual({
                stepIndex: currentStepIndex,
                gauge: 'hold',
                message: 'SIGNAL LOST — TRY AGAIN',
                offersResend: false,
            });
        });

        it.each([500, 502, 429])('should_hold_with_signal_lost_on_status_%i', (status) => {
            expect(describeAuthError('login', 0, status, []).message).toBe('SIGNAL LOST — TRY AGAIN');
        });
    });
});
