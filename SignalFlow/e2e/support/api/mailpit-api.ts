import { request as playwrightRequest } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { MAILPIT_URL } from '../constants';
import { MAILPIT_ENDPOINTS } from '../endpoints';

export interface MailpitAddress {
    Name: string;
    Address: string;
}

export interface MailpitMessage {
    ID: string;
    From: MailpitAddress;
    To: MailpitAddress[];
    Subject: string;
    Created: string;
}

export type MailpitInfo = Record<string, unknown>;

interface MailpitMessageList {
    messages?: MailpitMessage[];
}

async function withMailpitContext<T>(
    action: (mailpitContext: APIRequestContext) => Promise<T>,
): Promise<T> {
    const mailpitContext = await playwrightRequest.newContext({ baseURL: MAILPIT_URL });

    try {
        return await action(mailpitContext);

    } finally {
        await mailpitContext.dispose();
    }
}

export async function clearMailpitMessages(): Promise<void> {
    await withMailpitContext(async (mailpitContext) => {
        try {
            await mailpitContext.delete(MAILPIT_ENDPOINTS.messages);

        } catch {
            // Mailpit can be briefly unreachable; the next read reports the real state.
        }
    });
}

export async function getMailpitMessages(): Promise<MailpitMessage[]> {
    return withMailpitContext(async (mailpitContext) => {
        try {
            const response = await mailpitContext.get(MAILPIT_ENDPOINTS.messages);

            if (!response.ok()) {
                return [];
            }

            const messageList: MailpitMessageList = await response.json();

            return messageList.messages ?? [];

        } catch {
            return [];
        }
    });
}

export async function getMailpitInfo(): Promise<MailpitInfo | null> {
    return withMailpitContext(async (mailpitContext) => {
        try {
            const response = await mailpitContext.get(MAILPIT_ENDPOINTS.info);

            if (!response.ok()) {
                return null;
            }

            return await response.json();

        } catch {
            return null;
        }
    });
}
