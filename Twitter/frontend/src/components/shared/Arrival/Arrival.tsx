import type { ReactNode } from 'react';
import { useFocusOnMount } from '../../../hooks/useFocusOnMount';
import './Arrival.css';


interface ArrivalProps {
    eyebrow: string;
    headline: ReactNode;
    detail?: string;
    isAlarm?: boolean;
    children?: ReactNode;
}

function Arrival({ eyebrow, headline, detail, isAlarm = false, children }: ArrivalProps) {
    // The step form is gone, so focus would fall to the page; the headline announces the new state instead.
    const headlineRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <section className="arrival" data-alarm={isAlarm}>
            <p className="arrival-eyebrow">{eyebrow}</p>
            <h1 ref={headlineRef} className="arrival-headline" tabIndex={-1}>{headline}</h1>
            {detail && <p className="arrival-detail">{detail}</p>}
            <span className="arrival-rule" />
            {children && <div className="arrival-actions">{children}</div>}
        </section>
    );
}

export default Arrival;
