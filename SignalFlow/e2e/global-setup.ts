import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const COMPOSE_ARGS = '-p signal-flow-e2e -f docker-compose.e2e.yml'

export default function globalSetup() {
    const buildFlag = process.env.E2E_NO_BUILD === '1' ? '' : '--build'
    const command = `docker compose ${COMPOSE_ARGS} up -d ${buildFlag} --wait`

    try {
        execSync(command, {
            cwd: __dirname,
            stdio: 'inherit',
            timeout: 300_000,
        })
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

