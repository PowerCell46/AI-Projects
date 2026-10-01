import { depthPositionPercent } from '../../../../utils/depth';
import { useDepthCount } from './useDepthCount';
import './Horizon.css';


interface HorizonProps {
    depthMetres: number;
    seafloorDepthMetres: number;
    isAlarm: boolean;
}

function Horizon({ depthMetres, seafloorDepthMetres, isAlarm }: HorizonProps) {
    const displayedMetres = useDepthCount(depthMetres);

    const positionPercent = depthPositionPercent(depthMetres, seafloorDepthMetres);

    return (
        <div
            className="horizon"
            aria-hidden="true"
            data-alarm={isAlarm}
            style={{ transform: `translateY(${positionPercent}%)` }}
        >
            <span className="horizon-marker" />
            <span className="horizon-hairline" />
            <div className="horizon-readout">
                <span className="horizon-readout-label">DEPTH</span>
                <span className="horizon-readout-value">
                    <span className="horizon-readout-number">{displayedMetres}</span>
                    <span className="horizon-readout-unit">M</span>
                </span>
            </div>
        </div>
    );
}

export default Horizon;
