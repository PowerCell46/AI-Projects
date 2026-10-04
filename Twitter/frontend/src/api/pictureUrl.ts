import { ENDPOINTS } from './endpoints';


export function toPictureUrl(path: string | null): string | null {
    return path === null ? null : ENDPOINTS.backendPath(path);
}
