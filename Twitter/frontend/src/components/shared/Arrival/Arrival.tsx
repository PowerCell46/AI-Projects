import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import './Arrival.css';


interface ArrivalProps {
    eyebrow: string;
    headline: ReactNode;
    detail?: string;
    isAlarm?: boolean;
    children?: ReactNode;
}

function Arrival({ eyebrow, headline, detail, isAlarm = false, children }: ArrivalProps) {
    const headlineRef = useRef<HTMLHeadingElement>(null);

    // The step form is gone, so focus would fall to the page; the headline announces the new state instead.
    useEffect(() => {
        headlineRef.current?.focus();
    }, []);

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
