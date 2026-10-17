import { stopStack } from './support/stack/compose';

export default function globalTeardown(): void {
    if (process.env.E2E_SKIP_TEARDOWN === '1') {
        console.log('E2E_SKIP_TEARDOWN=1 set, leaving the e2e stack running.');

        return;
    }

    stopStack();
}
