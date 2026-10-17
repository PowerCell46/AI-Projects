import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

import { COMPOSE_ARGS } from './compose-args';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const DOWN_TIMEOUT_MS = 60_000;

export default function globalTeardown(): void {
    if (process.env.E2E_SKIP_TEARDOWN === '1') {
        console.log('E2E_SKIP_TEARDOWN=1 set, leaving the e2e stack running.');

        return;
    }

    execSync(`docker compose ${COMPOSE_ARGS} down -v`, {
        cwd: __dirname,
        stdio: 'inherit',
        timeout: DOWN_TIMEOUT_MS,
    });
}
