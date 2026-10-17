import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { COMPOSE_ARGS } from '../compose-args';

const E2E_DIRECTORY = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const GATEWAY_DATABASE_SERVICE = 'postgres-api-gateway';

const DATABASE_USER_AND_NAME = 'twitter_e2e';

// The gateway reads "now" from its clock, so the only way to age a confirmation link in a running stack is its row.
// The address is a generated one (letters, digits, underscore), so it is safe inside the SQL string.
export function expireConfirmationLinks(email: string) {
    const sql = `UPDATE email_confirmation_tokens SET expires_at = now() - interval '1 hour' `
        + `WHERE user_id = (SELECT id FROM users WHERE email = '${email.toLowerCase()}')`;

    execFileSync(
        'docker',
        [
            'compose',
            ...COMPOSE_ARGS.split(' '),
            'exec',
            '-T',
            GATEWAY_DATABASE_SERVICE,
            'psql',
            '-U',
            DATABASE_USER_AND_NAME,
            '-d',
            DATABASE_USER_AND_NAME,
            '-v',
            'ON_ERROR_STOP=1',
            '-c',
            sql,
        ],
        {
            cwd: E2E_DIRECTORY,
            stdio: 'pipe',
        },
    );
}
