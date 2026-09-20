import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const COMPOSE_ARGS = '-p url-shortener-e2e -f docker-compose.e2e.yml'

export default function globalTeardown() {
    if (process.env.E2E_SKIP_TEARDOWN === '1') {
        console.log('E2E_SKIP_TEARDOWN=1 set, leaving the e2e stack running.')
        return
    }

    execSync(`docker compose ${COMPOSE_ARGS} down -v`, {
        cwd: __dirname,
        stdio: 'inherit',
    })
}
