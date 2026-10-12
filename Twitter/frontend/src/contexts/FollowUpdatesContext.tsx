import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { FollowSubject } from '../hooks/usePersonFollow';


// The fields of a follow that you can change from a profile page.
export type FollowUpdate = Partial<Pick<FollowSubject, 'followedByMe' | 'followersCount'>>;

interface FollowUpdatesContextValue {
    updates: Record<string, FollowUpdate>;
    reportFollowUpdate: (username: string, update: FollowUpdate) => void;
}

interface FollowUpdatesProviderProps {
    children: ReactNode;
}

const FollowUpdatesContext = createContext<FollowUpdatesContextValue | null>(null);

export function FollowUpdatesProvider({ children }: FollowUpdatesProviderProps) {
    const [updates, setUpdates] = useState<Record<string, FollowUpdate>>({});

    const reportFollowUpdate = useCallback((username: string, update: FollowUpdate) => {
        // Usernames match case-insensitively, as on the profile page.
        const key = username.toLowerCase();

        setUpdates((currentUpdates) => ({
            ...currentUpdates,
            [key]: {
                ...currentUpdates[key],
                ...update,
            },
        }));
    }, []);

    const value = useMemo(
        () => ({
            updates,
            reportFollowUpdate,
        }),
        [updates, reportFollowUpdate],
    );

    return (
        <FollowUpdatesContext.Provider value={value}>
            {children}
        </FollowUpdatesContext.Provider>
    );
}

// The context convention keeps the provider and its hook in one file, so fast refresh reloads the page here.
// oxlint-disable-next-line react/only-export-components
export function useFollowUpdates(): FollowUpdatesContextValue {
    const context = useContext(FollowUpdatesContext);

    if (!context) {
        throw new Error('useFollowUpdates must be used inside FollowUpdatesProvider.');
    }

    return context;
}
