import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import './StageLink.css';


interface StageLinkProps {
    to: string;
    variant: 'footer' | 'secondary' | 'primary';
    children: ReactNode;
    state?: object;
}

function StageLink({ to, variant, children, state }: StageLinkProps) {
    return (
        <Link className="stage-link" data-variant={variant} to={to} state={state}>
            {children}
        </Link>
    );
}

export default StageLink;
