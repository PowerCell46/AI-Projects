import { describe, expect, it } from 'vitest';
import { FLOWS, stepIndexOfField } from './flows';


describe('FLOWS', () => {
    it('should_give_login_two_steps_with_the_identifier_marked_as_username', () => {
        const login = FLOWS.login;

        expect(login.steps.map((step) => step.autoComplete)).toEqual(['username', 'current-password']);
        expect(login.seafloorDepthMetres).toBe(4900);
    });

    it('should_give_register_three_steps_ending_in_a_new_password', () => {
        const register = FLOWS.register;

        expect(register.steps.map((step) => step.field)).toEqual(['email', 'username', 'password']);
        expect(register.steps.map((step) => step.depthMetres)).toEqual([140, 3860, 9720]);
        expect(register.steps[2].autoComplete).toBe('new-password');
        expect(register.seafloorDepthMetres).toBe(10910);
    });

    it('should_give_resend_one_email_step', () => {
        const resend = FLOWS.resend;

        expect(resend.steps).toHaveLength(1);
        expect(resend.steps[0].inputType).toBe('email');
        expect(resend.steps[0].question).toBe('Where should we send it?');
    });
});

describe('stepIndexOfField', () => {
    it('should_find_the_step_that_holds_the_field', () => {
        expect(stepIndexOfField('register', 'username')).toBe(1);
        expect(stepIndexOfField('login', 'password')).toBe(1);
    });

    it('should_return_minus_one_when_the_flow_has_no_such_field', () => {
        expect(stepIndexOfField('resend', 'password')).toBe(-1);
    });
});
