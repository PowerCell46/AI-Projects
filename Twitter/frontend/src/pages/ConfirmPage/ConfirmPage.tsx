import { useSearchParams } from 'react-router-dom';
import Arrival from '../../components/shared/Arrival/Arrival';
import DescentStage from '../../components/shared/DescentStage/DescentStage';
import StageLink from '../../components/shared/StageLink/StageLink';
import { ROUTES } from '../../routes';
import { SIGNAL_LOST_MESSAGE } from '../../utils/authErrors';
import { useConfirmation } from './useConfirmation';
import './ConfirmPage.css';


const TOKEN_PARAM = 'token';

const SURFACE_DEPTH_METRES = 140;

const SEAFLOOR_DEPTH_METRES = 4900;

const EXPIRED_MESSAGE = 'LINK EXPIRED OR ALREADY USED';

function ConfirmPage() {
    const [searchParams] = useSearchParams();
    const { status, retry } = useConfirmation(searchParams.get(TOKEN_PARAM));

    const depthMetres = status === 'confirmed' ? SEAFLOOR_DEPTH_METRES : SURFACE_DEPTH_METRES;

    return (
        <DescentStage
            depthMetres={depthMetres}
            seafloorDepthMetres={SEAFLOOR_DEPTH_METRES}
            isAlarm={status === 'expired' || status === 'failed'}
            footer={status === 'expired' && <StageLink to={ROUTES.login} variant="footer">LOG IN</StageLink>}
        >
            {status === 'pending' && (
                <Arrival eyebrow="CHECKING LINK" headline="Hold on, checking your link." />
            )}
            {status === 'confirmed' && (
                <Arrival eyebrow="ACCOUNT CONFIRMED" headline="You're cleared to descend.">
                    <StageLink to={ROUTES.login} variant="primary">LOG IN</StageLink>
                </Arrival>
            )}
            {status === 'expired' && (
                <Arrival isAlarm eyebrow={EXPIRED_MESSAGE} headline="This link won't take you down.">
                    <StageLink to={ROUTES.resend} variant="secondary">RESEND LINK</StageLink>
                </Arrival>
            )}
            {status === 'failed' && (
                <Arrival isAlarm eyebrow={SIGNAL_LOST_MESSAGE} headline="We couldn't check your link.">
                    <button type="button" className="confirm-page-retry" onClick={retry}>TRY AGAIN</button>
                </Arrival>
            )}
        </DescentStage>
    );
}

export default ConfirmPage;
