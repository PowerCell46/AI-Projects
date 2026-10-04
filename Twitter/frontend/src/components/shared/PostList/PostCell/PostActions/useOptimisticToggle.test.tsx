import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useOptimisticToggle } from './useOptimisticToggle';


interface PendingRequest {
    nextValue: boolean;
    resolve: () => void;
    reject: () => void;
}

// Each call to the sender waits until the test settles it, so a click can be made while a request is in flight.
function createSender() {
    const pendingRequests: PendingRequest[] = [];
    const sendChange = vi.fn((nextValue: boolean) => new Promise<void>((resolve, reject) => {
        pendingRequests.push({
            nextValue,
            resolve,
            reject: () => reject(new Error('The request failed.')),
        });
    }));

    return {
        sendChange,
        pendingRequests,
    };
}

async function settle(request: PendingRequest, outcome: 'resolve' | 'reject') {
    await act(async () => {
        request[outcome]();
    });
}

describe('useOptimisticToggle', () => {
    it('should_start_with_the_initial_value_and_send_nothing', () => {
        const { sendChange } = createSender();

        const { result } = renderHook(() => useOptimisticToggle(true, sendChange));

        expect(result.current.isOn).toBe(true);
        expect(sendChange).not.toHaveBeenCalled();
    });

    it('should_flip_at_once_and_send_the_new_value_when_toggled', () => {
        const { sendChange } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));

        act(() => result.current.toggle());

        expect(result.current.isOn).toBe(true);
        expect(sendChange).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should_stay_flipped_when_the_request_succeeds', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());

        await settle(pendingRequests[0], 'resolve');

        expect(result.current.isOn).toBe(true);
        expect(sendChange).toHaveBeenCalledTimes(1);
    });

    it('should_flip_back_without_throwing_when_the_request_fails', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());

        await settle(pendingRequests[0], 'reject');

        expect(result.current.isOn).toBe(false);
    });

    it('should_send_the_opposite_value_afterwards_when_clicked_twice_while_the_first_is_in_flight', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        act(() => result.current.toggle());

        expect(result.current.isOn).toBe(false);
        expect(sendChange).toHaveBeenCalledTimes(1);

        await settle(pendingRequests[0], 'resolve');

        expect(sendChange).toHaveBeenCalledTimes(2);
        expect(sendChange).toHaveBeenLastCalledWith(false);
        expect(result.current.isOn).toBe(false);
    });

    it('should_send_nothing_more_when_the_clicks_in_flight_end_where_the_server_already_is', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        act(() => result.current.toggle());
        act(() => result.current.toggle());

        await settle(pendingRequests[0], 'resolve');

        expect(sendChange).toHaveBeenCalledTimes(1);
        expect(result.current.isOn).toBe(true);
    });

    it('should_send_one_request_at_a_time_when_clicked_many_times', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        act(() => result.current.toggle());

        await settle(pendingRequests[0], 'resolve');

        expect(pendingRequests).toHaveLength(2);
        expect(sendChange).toHaveBeenCalledTimes(2);
    });

    it('should_go_back_to_the_last_confirmed_value_and_drop_the_queued_click_when_the_request_fails', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        act(() => result.current.toggle());
        act(() => result.current.toggle());

        await settle(pendingRequests[0], 'reject');

        expect(result.current.isOn).toBe(false);
        expect(sendChange).toHaveBeenCalledTimes(1);
    });

    it('should_go_back_to_the_value_the_server_confirmed_when_a_later_request_fails', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        act(() => result.current.toggle());
        await settle(pendingRequests[0], 'resolve');

        await settle(pendingRequests[1], 'reject');

        expect(result.current.isOn).toBe(true);
    });

    it('should_send_again_when_clicked_after_a_failure', async () => {
        const { sendChange, pendingRequests } = createSender();
        const { result } = renderHook(() => useOptimisticToggle(false, sendChange));
        act(() => result.current.toggle());
        await settle(pendingRequests[0], 'reject');

        act(() => result.current.toggle());

        expect(sendChange).toHaveBeenCalledTimes(2);
        expect(sendChange).toHaveBeenLastCalledWith(true);
        expect(result.current.isOn).toBe(true);
    });
});
