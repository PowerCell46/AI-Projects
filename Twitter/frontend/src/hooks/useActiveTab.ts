import { useLocation } from 'react-router-dom';
import { tabOfPath } from '../utils/tabs';
import type { TabId } from '../utils/tabs';


// The tab the address belongs to, or null on a page without tabs.
export function useActiveTab(): TabId | null {
    const { pathname } = useLocation();

    return tabOfPath(pathname);
}
