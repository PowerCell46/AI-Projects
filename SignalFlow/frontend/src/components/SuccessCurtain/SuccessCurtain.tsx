import './SuccessCurtain.css'

export type CurtainPhase = 'covering' | 'covered' | 'revealing'
export type CurtainDirection = 'up' | 'down'

interface SuccessCurtainProps {
    phase: CurtainPhase
    direction: CurtainDirection
    heading: string
    sub: string
}

function SuccessCurtain({ phase, direction, heading, sub }: SuccessCurtainProps) {
    return (
        <div className="success-curtain" data-phase={phase} data-direction={direction} aria-hidden="true">
            <div className="success-curtain-content">
                <p className="success-curtain-wordmark">signalflow</p>
                <p className="success-curtain-heading">{heading}</p>
                {sub !== '' && <p className="success-curtain-sub">{sub}</p>}
            </div>
        </div>
    )
}

export default SuccessCurtain
