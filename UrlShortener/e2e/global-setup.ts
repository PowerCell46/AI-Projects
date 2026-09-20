import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const COMPOSE_ARGS = '-p url-shortener-e2e -f docker-compose.e2e.yml'

export default function globalSetup() {
    execSync(`docker compose ${COMPOSE_ARGS} up -d --build --wait`, {
        cwd: __dirname,
        stdio: 'inherit',
    })
}
