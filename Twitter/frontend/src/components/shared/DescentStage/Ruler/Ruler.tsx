import {
    TICK_INTERVALS,
    isMajorTick,
    tickLabelMetres,
    tickPositionPercent,
} from '../../../../utils/depth';
import './Ruler.css';


const TICK_INDEXES = Array.from(
    { length: TICK_INTERVALS + 1 },
    (_, tickIndex) => tickIndex,
);

function Ruler() {
    return (
        <ul className="ruler" aria-hidden="true">
            {TICK_INDEXES.map((tickIndex) => (
                <li
                    key={tickIndex}
                    className="ruler-tick"
                    data-major={isMajorTick(tickIndex)}
                    style={{ top: `${tickPositionPercent(tickIndex)}%` }}
                >
                    {isMajorTick(tickIndex) && (
                        <span className="ruler-tick-label">{tickLabelMetres(tickIndex)}</span>
                    )}
                </li>
            ))}
        </ul>
    );
}

export default Ruler;
