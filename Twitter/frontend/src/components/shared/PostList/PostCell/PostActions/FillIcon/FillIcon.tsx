import { useId } from 'react';
import './FillIcon.css';


const SHAPE_PATHS = {
    heart: 'M12 20.4C4.4 15.2 2.6 11.8 2.6 8.9 2.6 6.2 4.7 4.1 7.3 4.1c1.8 0 3.4.9 4.3 2.3h.8c.9-1.4 2.5-2.3 4.3-2.3 2.6 0 4.7 2.1 4.7 4.8 0 2.9-1.8 6.3-9.4 11.5z',
    bookmark: 'M5.8 3.2h12.4v17.6L12 16.1l-6.2 4.7z',
};

const VIEW_BOX_SIZE = 24;

interface FillIconProps {
    shape: keyof typeof SHAPE_PATHS;
    isActive: boolean;
}

function FillIcon({ shape, isActive }: FillIconProps) {
    const clipPathId = useId();
    const path = SHAPE_PATHS[shape];

    return (
        <svg
            className="fill-icon"
            viewBox={`0 0 ${VIEW_BOX_SIZE} ${VIEW_BOX_SIZE}`}
            aria-hidden="true"
            data-shape={shape}
            data-active={isActive}
        >
            <path className="fill-icon-outline" d={path} />
            <clipPath id={clipPathId}>
                <rect className="fill-icon-level" width={VIEW_BOX_SIZE} height={VIEW_BOX_SIZE} />
            </clipPath>
            <path className="fill-icon-solid" d={path} clipPath={`url(#${clipPathId})`} />
        </svg>
    );
}

export default FillIcon;
