import { randomUUID } from 'node:crypto';
import { TEST_PASSWORD } from '../constants';

export interface TestUser {
    email: string;
    password: string;
}

// Seeded by the gateway's DatabaseLoader from ADMIN_EMAIL/ADMIN_PASSWORD in
// docker-compose.e2e.yml. Must also satisfy the login DTO rules.
export const ADMIN_USER: TestUser = {
    email: 'admin@e2e.local',
    password: 'Admin12345',
};

export function newUser(): TestUser {
    return {
        email: `e2e-${randomUUID()}@example.com`,
        password: TEST_PASSWORD,
    };
}
