import { startStack, stopStack } from './support/stack/compose';

function stopStackQuietly(): void {
    try {
        stopStack();

    } catch {
        // The start error is the one worth reporting, so a failed cleanup stays silent.
    }
}

export default function globalSetup(): void {
    const shouldBuild = process.env.E2E_NO_BUILD !== '1';

    try {
        startStack(shouldBuild);

    } catch (error) {
        console.error('Failed to start e2e stack in globalSetup, cleaning up...', error);

        stopStackQuietly();

        throw error;
    }
}
