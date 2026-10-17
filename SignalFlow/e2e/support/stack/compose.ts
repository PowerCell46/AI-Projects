import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const E2E_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const COMPOSE_COMMAND = 'docker compose -p signal-flow-e2e -f docker-compose.e2e.yml';

const STACK_START_TIMEOUT_MS = 300_000;

const STACK_STOP_TIMEOUT_MS = 60_000;

function runCompose(subcommand: string, timeoutMs: number): void {
    execSync(
        `${COMPOSE_COMMAND} ${subcommand}`,
        {
            cwd: E2E_ROOT,
            stdio: 'inherit',
            timeout: timeoutMs,
        },
    );
}

export function startStack(shouldBuild: boolean): void {
    const buildFlag = shouldBuild ? '--build' : '';

    runCompose(`up -d ${buildFlag} --wait`, STACK_START_TIMEOUT_MS);
}

export function stopStack(): void {
    runCompose('down -v', STACK_STOP_TIMEOUT_MS);
}
