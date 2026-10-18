import { useLocation, useNavigate } from 'react-router-dom';
import { followListOf } from './FollowListModal/followListKind';
import type { FollowListKind } from './FollowListModal/followListKind';


export interface OpenFollowList {
    openKind: FollowListKind | null;
    open: (kind: FollowListKind) => void;
    close: () => void;
}

// Opening pushes a history entry that carries the list in its state, so the browser's Back button closes it; closing
// from the page goes back the same way.
export function useOpenFollowList(): OpenFollowList {
    const location = useLocation();
    const navigate = useNavigate();

    function open(kind: FollowListKind) {
        navigate(
            location.pathname,
            { state: { followList: kind } },
        );
    }

    function close() {
        navigate(-1);
    }

    return {
        openKind: followListOf(location.state),
        open,
        close,
    };
}
