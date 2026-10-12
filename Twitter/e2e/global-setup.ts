import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

import { smokeCheckFrontendContainer } from './container-smoke'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const COMPOSE_ARGS = '-p twitter-e2e -f docker-compose.e2e.yml'

const BUILD_TIMEOUT_MS = 900_000

const UP_TIMEOUT_MS = 300_000

export default async function globalSetup() {
    // The four Maven image builds run first with their own generous timeout, so a slow
    // first build fails as a build error instead of a stack-start timeout.
    if (process.env.E2E_NO_BUILD !== '1') {
        execSync(`docker compose ${COMPOSE_ARGS} build`, {
            cwd: __dirname,
            stdio: 'inherit',
            timeout: BUILD_TIMEOUT_MS,
        })
    }

    const command = `docker compose ${COMPOSE_ARGS} up -d --wait`

    try {
        execSync(command, {
            cwd: __dirname,
            stdio: 'inherit',
            timeout: UP_TIMEOUT_MS,
        })

        // The specs run against the dev server; this covers the nginx image they never touch.
        await smokeCheckFrontendContainer()
    } catch (error) {
        console.error('Failed to start e2e stack in globalSetup, cleaning up...', error)
        try {
            execSync(`docker compose ${COMPOSE_ARGS} down -v`, {
                cwd: __dirname,
                stdio: 'inherit',
                timeout: 60_000,
            })
        } catch {
            // Ignore teardown error and rethrow initial error
        }
        throw error
    }
}

