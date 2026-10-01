import type { ReactNode } from 'react';
import Horizon from './Horizon/Horizon';
import Ruler from './Ruler/Ruler';
import './DescentStage.css';


interface DescentStageProps {
    depthMetres: number;
    seafloorDepthMetres: number;
    isAlarm?: boolean;
    footer?: ReactNode;
    children: ReactNode;
}

function DescentStage({
    depthMetres,
    seafloorDepthMetres,
    isAlarm = false,
    footer,
    children,
}: DescentStageProps) {
    return (
        <div className="descent-stage" data-alarm={isAlarm}>
            <Ruler />
            <Horizon
                depthMetres={depthMetres}
                seafloorDepthMetres={seafloorDepthMetres}
                isAlarm={isAlarm}
            />
            <p className="descent-stage-brand">TWITTER</p>
            <main className="descent-stage-well">
                <div className="descent-stage-content">{children}</div>
            </main>
            {footer && <footer className="descent-stage-footer">{footer}</footer>}
        </div>
    );
}

export default DescentStage;
