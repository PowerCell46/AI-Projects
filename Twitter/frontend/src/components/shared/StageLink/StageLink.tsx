import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import './StageLink.css';


interface StageLinkProps {
    to: string;
    variant: 'footer' | 'secondary' | 'primary';
    state?: object;
    children: ReactNode;
}

function StageLink({ to, variant, state, children }: StageLinkProps) {
    return (
        <Link className="stage-link" data-variant={variant} to={to} state={state}>
            {children}
        </Link>
    );
}

export default StageLink;
